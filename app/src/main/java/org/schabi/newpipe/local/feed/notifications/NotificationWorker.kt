package org.schabi.newpipe.local.feed.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.NotificationWithIdAndTag
import androidx.core.app.PendingIntentCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.rx3.await
import org.schabi.newpipe.App
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.feed.notifications.NotificationWorker.Companion.cancel
import org.schabi.newpipe.local.feed.service.FeedLoadManager
import org.schabi.newpipe.local.feed.service.FeedLoadService
import org.schabi.newpipe.local.feed.service.FeedUpdateInfo
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.image.CoilHelper
import org.schabi.newpipe.util.image.ImageStrategy

/*
 * Worker which checks for new streams of subscribed channels
 * in intervals which can be set by the user in the settings.
 */
class NotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    private val feedLoadManager = FeedLoadManager(appContext)
    private val notificationManager = NotificationManagerCompat.from(appContext)

    override suspend fun doWork(): Result {
        if (!areNotificationsEnabled(applicationContext)) {
            return Result.success()
        }

        try {
            val feedTask = feedLoadManager.startLoading(
                ignoreOutdatedThreshold = true,
                groupId = FeedLoadManager.GROUP_NOTIFICATION_ENABLED
            )

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
            if (notificationManager.areNotificationsEnabled()) {
                notificationManager.notify(FeedLoadService.NOTIFICATION_ID, notification)
            }

            val feed = feedTask.await()
            notificationManager.cancel(FeedLoadService.NOTIFICATION_ID)

            val feedUpdateInfoList = feed.mapNotNull {
                it.value?.takeIf { feedUpdateInfo ->
                    feedUpdateInfo.newStreams.isNotEmpty()
                }
            }
            feedUpdateInfoList.forEach { feedUpdateInfo ->
                if (notificationManager.areNotificationsEnabled()) {
                    val notifications = createNotifications(feedUpdateInfo)
                    notificationManager.notify(notifications)
                }
            }
            return Result.success()
        } catch (e: Exception) {
            if (e is CancellationException) {
                throw e
            }
            Log.e(TAG, "Error while displaying streams notifications", e)
            ErrorUtil.createNotification(
                applicationContext,
                ErrorInfo(e, UserAction.NEW_STREAMS_NOTIFICATIONS, "main worker")
            )
            return Result.failure()
        }
    }

    /**
     * Create notifications for new streams from a single channel. The individual notifications are
     * expandable on Android 7.0 and later.
     *
     * Opening the summary notification will open the corresponding channel page. Opening the
     * individual notifications will open the corresponding video.
     */
    suspend fun createNotifications(data: FeedUpdateInfo): List<NotificationWithIdAndTag> {
        val newStreams = data.newStreams

        // open the channel page when clicking on the summary notification
        val intent = NavigationHelper
            .getChannelIntent(applicationContext, data.serviceId, data.url)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val contentIntent = PendingIntentCompat.getActivity(applicationContext, data.pseudoId, intent, 0, false)

        // Build a summary notification for Android versions < 7.0
        val summary = applicationContext.resources
            .getQuantityString(R.plurals.new_streams, newStreams.size, newStreams.size)
        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle(data.name)
        newStreams.forEach { style.addLine(it.name) }

        // Create stream notifications
        val avatarIcon = CoilHelper.loadBitmap(applicationContext, data.avatarUrl)
        val streamNotifications = newStreams.map { createStreamNotification(it, data.serviceId, avatarIcon) }

        val summaryNotification = NotificationCompat.Builder(
            applicationContext,
            applicationContext.getString(R.string.streams_notification_channel_id)
        )
            .setContentTitle(data.name)
            .setContentText(summary)
            .setNumber(newStreams.size)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_LARGE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
            .setColor(applicationContext.getColor(R.color.ic_launcher_background))
            .setColorized(true)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setGroupSummary(true)
            .setGroup(data.url)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .setStyle(style)
            .setContentIntent(contentIntent)
            .setLargeIcon(avatarIcon)
            .build()

        return streamNotifications + NotificationWithIdAndTag(data.pseudoId, summaryNotification)
    }

    private suspend fun createStreamNotification(
        item: StreamInfoItem,
        serviceId: Int,
        channelIcon: Bitmap?
    ): NotificationWithIdAndTag {
        val id = item.url.hashCode()

        // Open the stream link in the player when clicking on the notification.
        val intent = NavigationHelper.getStreamIntent(applicationContext, serviceId, item.url, item.name)
        val contentIntent = PendingIntentCompat
            .getActivity(applicationContext, id, intent, PendingIntent.FLAG_UPDATE_CURRENT, false)

        val thumbnailUrl = ImageStrategy.choosePreferredImage(item.thumbnails)
        val thumbnail = CoilHelper.loadBitmap(applicationContext, thumbnailUrl)
        val builder = NotificationCompat.Builder(
            applicationContext,
            applicationContext.getString(R.string.streams_notification_channel_id)
        )
            .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
            .setWhen(item.uploadDate?.instant?.toEpochMilli() ?: System.currentTimeMillis())
            .setContentTitle(item.name)
            .setContentText(item.uploaderName)
            .setGroup(item.uploaderUrl)
            .setColor(applicationContext.getColor(R.color.ic_launcher_background))
            .setColorized(true)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setContentIntent(contentIntent)
            // Avoid creating noise for individual stream notifications.
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
        val style = NotificationCompat.BigPictureStyle()
            .bigPicture(thumbnail)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setLargeIcon(channelIcon)
                .setStyle(style.showBigPictureWhenCollapsed(true))
        } else {
            builder.setLargeIcon(thumbnail)
                .setStyle(style.bigLargeIcon(channelIcon))
        }
        return NotificationWithIdAndTag(id, builder.build())
    }

    companion object {

        private val TAG = NotificationWorker::class.java.simpleName
        private const val WORK_TAG = App.PACKAGE_NAME + "_streams_notifications"

        private fun areNotificationsEnabled(context: Context) = NotificationHelper.areNewStreamsNotificationsEnabled(context) &&
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

            val request = PeriodicWorkRequestBuilder<NotificationWorker>(
                options.interval,
                TimeUnit.MILLISECONDS
            )
                .setConstraints(constraints)
                .addTag(WORK_TAG)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_TAG,
                    if (force) {
                        ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
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
