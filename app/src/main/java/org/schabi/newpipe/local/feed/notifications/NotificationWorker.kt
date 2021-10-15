package org.schabi.newpipe.local.feed.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.rxjava3.RxWorker
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.NotificationMode
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

    override fun createWork(): Single<Result> = if (isEnabled(applicationContext)) {
        feedLoadManager.startLoading(ignoreOutdatedThreshold = true)
            .map { feed ->
                feed.mapNotNull { x ->
                    x.value?.takeIf {
                        it.notificationMode == NotificationMode.ENABLED &&
                            it.newStreamsCount > 0
                    }
                }
            }
            .doOnSubscribe { setForegroundAsync(createForegroundInfo()) }
            .flatMapObservable { Observable.fromIterable(it) }
            .flatMapCompletable { x -> notificationHelper.displayNewStreamsNotification(x) }
            .toSingleDefault(Result.success())
            .onErrorReturnItem(Result.failure())
    } else Single.just(Result.success())

    private fun createForegroundInfo(): ForegroundInfo {
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
        return ForegroundInfo(FeedLoadService.NOTIFICATION_ID, notification)
    }

    companion object {

        private const val TAG = "streams_notifications"

        private fun isEnabled(context: Context): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(
                    context.getString(R.string.enable_streams_notifications),
                    false
                ) && NotificationHelper.isNotificationsEnabledNative(context)
        }

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
                .addTag(TAG)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    TAG,
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

        @JvmStatic
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<NotificationWorker>()
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
