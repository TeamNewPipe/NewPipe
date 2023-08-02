package org.schabi.newpipe.local.feed.notifications

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.rxjava3.RxWorker
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import org.schabi.newpipe.App
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.local.feed.service.FeedLoadManager
import org.schabi.newpipe.local.feed.service.FeedLoadService
import java.util.concurrent.TimeUnit

/*
 * Worker which checks for new streams of subscribed channels
 * in intervals which can be set by the user in the settings.
 */
class NotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : RxWorker(appContext, workerParams) {

    private val notificationHelper by lazy {
        NotificationHelper(appContext)
    }
    private val feedLoadManager = FeedLoadManager(appContext)

    override fun createWork(): Single<Result> = if (areNotificationsEnabled(applicationContext)) {
        feedLoadManager.startLoading(
            ignoreOutdatedThreshold = true,
            groupId = FeedLoadManager.GROUP_NOTIFICATION_ENABLED
        )
            .doOnSubscribe { showLoadingFeedForegroundNotification() }
            .map { feed ->
                // filter out feedUpdateInfo items (i.e. channels) with nothing new
                feed.mapNotNull {
                    it.value?.takeIf { feedUpdateInfo ->
                        feedUpdateInfo.newStreams.isNotEmpty()
                    }
                }
            }
            .observeOn(AndroidSchedulers.mainThread()) // Picasso requires calls from main thread
            .map { feedUpdateInfoList ->
                // display notifications for each feedUpdateInfo (i.e. channel)
                feedUpdateInfoList.forEach { feedUpdateInfo ->
                    notificationHelper.displayNewStreamsNotifications(feedUpdateInfo)
                }
                return@map Result.success()
            }
            .doOnError { throwable ->
                Log.e(TAG, "Error while displaying streams notifications", throwable)
                ErrorUtil.createNotification(
                    applicationContext,
                    ErrorInfo(throwable, UserAction.NEW_STREAMS_NOTIFICATIONS, "main worker")
                )
            }
            .onErrorReturnItem(Result.failure())
    } else {
        // the user can disable streams notifications in the device's app settings
        Single.just(Result.success())
    }

    private fun showLoadingFeedForegroundNotification() {
        val notification = NotificationCompat.Builder(
            applicationContext,
            applicationContext.getString(R.string.notification_channel_id)
        ).setOngoing(true)
            .setProgress(-1, -1, true)
            .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentTitle(applicationContext.getString(R.string.feed_notification_loading))
            .build()
        setForegroundAsync(ForegroundInfo(FeedLoadService.NOTIFICATION_ID, notification))
    }

    companion object {

        private val TAG = NotificationWorker::class.java.simpleName
        private const val WORK_TAG = App.PACKAGE_NAME + "_streams_notifications"

        private fun areNotificationsEnabled(context: Context) =
            NotificationHelper.areNewStreamsNotificationsEnabled(context) &&
                NotificationHelper.areNotificationsEnabledOnDevice(context)

        /**
         * Schedules a task for the [NotificationWorker]
         * if the (device and in-app) notifications are enabled,
         * otherwise [cancel]s all scheduled tasks.
         */
        @JvmStatic
        fun initialize(context: Context) {
            if (areNotificationsEnabled(context)) {
                schedule(context)
            } else {
                cancel(context)
            }
        }

        /**
         * @param context the context to use
         * @param options configuration options for the scheduler
         * @param force Force the scheduler to use the new options
         * by replacing the previously used worker.
         */
        fun schedule(context: Context, options: ScheduleOptions, force: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (options.isRequireNonMeteredNetwork) {
                        NetworkType.UNMETERED
                    } else {
                        NetworkType.CONNECTED
                    }
                ).build()

            val request = PeriodicWorkRequest.Builder(
                NotificationWorker::class.java,
                options.interval,
                TimeUnit.MILLISECONDS
            ).setConstraints(constraints)
                .addTag(WORK_TAG)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_TAG,
                    if (force) {
                        ExistingPeriodicWorkPolicy.REPLACE
                    } else {
                        ExistingPeriodicWorkPolicy.KEEP
                    },
                    request
                )
        }

        @JvmStatic
        fun schedule(context: Context) = schedule(context, ScheduleOptions.from(context))

        /**
         * Check for new streams immediately
         */
        @JvmStatic
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<NotificationWorker>()
                .addTag(WORK_TAG)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        /**
         * Cancels all current work related to the [NotificationWorker].
         */
        @JvmStatic
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
        }
    }
}
