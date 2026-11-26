package org.schabi.newpipe.local.feed.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.feed.service.FeedUpdateInfo
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.image.ExtractorImageCompat
import org.schabi.newpipe.util.image.ImageStrategy

/**
 * Helper for everything related to show notifications about new streams to the user.
 */
class NotificationHelper internal constructor(
    val context: Context,
    private val loadImage: (String?, Int, Int) -> Bitmap?
) {
    constructor(context: Context) : this(context, NotificationImages(context)::load)

    private val manager = NotificationManagerCompat.from(context)

    /**
     * Show notifications for new streams from a single channel. The individual notifications are
     * expandable on Android 7.0 and later.
     *
     * Opening the summary notification will open the corresponding channel page. Opening the
     * individual notifications will open the corresponding video.
     */
    fun displayNewStreamsNotifications(data: FeedUpdateInfo, canNotify: () -> Boolean = { true }) {
        if (!manager.areNotificationsEnabled() || data.newStreams.isEmpty() || !canNotify()) return
        val newStreams = data.newStreams
        val summary = context.resources.getQuantityString(
            R.plurals.new_streams,
            newStreams.size,
            newStreams.size
        )
        val summaryBuilder = NotificationCompat.Builder(
            context,
            context.getString(R.string.streams_notification_channel_id)
        )
            .setContentTitle(data.name)
            .setContentText(summary)
            .setNumber(newStreams.size)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_LARGE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSmallIcon(R.drawable.ic_wizestream_triangle_white)
            .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
            .setColorized(true)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setGroupSummary(true)
            .setGroup(data.url)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)

        // Build a summary notification for Android versions < 7.0
        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle(data.name)
        newStreams.forEach { style.addLine(it.name) }
        summaryBuilder.setStyle(style)

        // open the channel page when clicking on the summary notification
        val intent = NavigationHelper
            .getChannelIntent(context, data.serviceId, data.url)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        summaryBuilder.setContentIntent(
            PendingIntentCompat.getActivity(context, data.pseudoId, intent, 0, false)
        )

        val avatarIcon = if (ImageStrategy.shouldLoadImages()) loadImage(data.avatarUrl, 128, 128) else null
        summaryBuilder.setLargeIcon(avatarIcon)

        // Show individual stream notifications, set channel icon only if there is actually one
        showStreamNotifications(data, avatarIcon, canNotify)
        // Show summary notification
        if (manager.areNotificationsEnabled() && canNotify()) {
            manager.notify(data.pseudoId, summaryBuilder.build())
        }
    }

    private fun showStreamNotifications(
        data: FeedUpdateInfo,
        channelIcon: Bitmap?,
        canNotify: () -> Boolean
    ) {
        if (manager.areNotificationsEnabled()) {
            data.newStreams.forEach { stream ->
                if (!canNotify()) return
                val notification =
                    createStreamNotification(stream, data, channelIcon)
                // Artwork may finish after the channel was muted or the active profile changed.
                if (!canNotify()) return
                manager.notify(stream.url.hashCode(), notification)
            }
        }
    }

    private fun createStreamNotification(
        item: StreamInfoItem,
        data: FeedUpdateInfo,
        channelIcon: Bitmap?
    ): Notification {
        val showThumbnails = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context.getString(R.string.streams_notifications_thumbnails_key), true)
        val thumbnail = if (showThumbnails && ImageStrategy.shouldLoadImages()) {
            val url = ImageStrategy.choosePreferredImage(ExtractorImageCompat.thumbnailImages(item))
            loadImage(url, 640, 360)
        } else {
            null
        }
        val builder = NotificationCompat.Builder(
            context,
            context.getString(R.string.streams_notification_channel_id)
        )
            .setSmallIcon(R.drawable.ic_wizestream_triangle_white)
            .setLargeIcon(thumbnail ?: channelIcon)
            .setContentTitle(item.name)
            .setContentText(item.uploaderName?.takeIf { it.isNotBlank() } ?: data.name)
            .setGroup(data.url)
            .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
            .setColorized(true)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setContentIntent(
                // Open the stream link in the player when clicking on the notification.
                PendingIntentCompat.getActivity(
                    context,
                    item.url.hashCode(),
                    NavigationHelper.getStreamIntent(context, data.serviceId, item.url, item.name),
                    PendingIntent.FLAG_UPDATE_CURRENT,
                    false
                )
            )
            .setSilent(true) // Avoid creating noise for individual stream notifications.
            .setOnlyAlertOnce(true)
        if (thumbnail != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(thumbnail)
                    .bigLargeIcon(channelIcon)
                    .setSummaryText(item.uploaderName?.takeIf { it.isNotBlank() } ?: data.name)
            )
        }
        return builder.build()
    }

    companion object {
        /**
         * Check whether notifications are enabled on the device.
         * Users can disable them via the system settings for a single app.
         * If this is the case, the app cannot create any notifications
         * and display them to the user.
         * <br>
         * On Android 26 and above, notification channels are used by WizeStream.
         * These can be configured by the user, too.
         * The notification channel for new streams is also checked by this method.
         *
         * @param context Context
         * @return <code>true</code> if notifications are allowed and can be displayed;
         * <code>false</code> otherwise
         */
        fun areNotificationsEnabledOnDevice(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channelId = context.getString(R.string.streams_notification_channel_id)
                val manager = context.getSystemService<NotificationManager>()!!
                val enabled = manager.areNotificationsEnabled()
                val channel = manager.getNotificationChannel(channelId)
                enabled && channel?.importance != NotificationManager.IMPORTANCE_NONE
            } else {
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }

        /**
         * Whether the user enabled the notifications for new streams in the app settings.
         */
        @JvmStatic
        fun areNewStreamsNotificationsEnabled(context: Context): Boolean {
            return (
                PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean(context.getString(R.string.enable_streams_notifications), false) &&
                    areNotificationsEnabledOnDevice(context)
                )
        }

        /**
         * Open the system's notification settings for WizeStream on Android Oreo (API 26)
         * and later.
         * Open the system's app settings for WizeStream on previous Android versions.
         */
        fun openSystemNotificationSettings(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = "package:${context.packageName}".toUri()
                context.startActivity(intent)
            }
        }
    }
}
