package org.schabi.newpipe.local.feed.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.preference.PreferenceManager
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.feed.service.FeedUpdateInfo
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.image.PicassoHelper

/**
 * Helper for everything related to show notifications about new streams to the user.
 */
class NotificationHelper(val context: Context) {
    private val manager = NotificationManagerCompat.from(context)
    private val iconLoadingTargets = ArrayList<Target>()

    /**
     * Show notifications for new streams from a single channel. The individual notifications are
     * expandable on Android 7.0 and later.
     *
     * Opening the summary notification will open the corresponding channel page. Opening the
     * individual notifications will open the corresponding video.
     */
    fun displayNewStreamsNotifications(data: FeedUpdateInfo) {
        val newStreams = data.newStreams
        val summary = context.resources.getQuantityString(
            R.plurals.new_streams, newStreams.size, newStreams.size
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
            .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
            .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
            .setColorized(true)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setGroupSummary(true)
            .setGroup(data.originalInfo.url)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)

        // Build a summary notification for Android versions < 7.0
        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle(data.name)
        newStreams.forEach { style.addLine(it.name) }
        summaryBuilder.setStyle(style)

        // open the channel page when clicking on the summary notification
        summaryBuilder.setContentIntent(
            PendingIntentCompat.getActivity(
                context,
                data.pseudoId,
                NavigationHelper
                    .getChannelIntent(context, data.originalInfo.serviceId, data.originalInfo.url)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                0,
                false
            )
        )

        // a Target is like a listener for image loading events
        val target = object : Target {
            override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom) {
                // set channel icon only if there is actually one (for Android versions < 7.0)
                summaryBuilder.setLargeIcon(bitmap)

                // Show individual stream notifications, set channel icon only if there is actually
                // one
                showStreamNotifications(newStreams, data.originalInfo.serviceId, bitmap)
                // Show summary notification
                manager.notify(data.pseudoId, summaryBuilder.build())

                iconLoadingTargets.remove(this) // allow it to be garbage-collected
            }

            override fun onBitmapFailed(e: Exception, errorDrawable: Drawable) {
                // Show individual stream notifications
                showStreamNotifications(newStreams, data.originalInfo.serviceId, null)
                // Show summary notification
                manager.notify(data.pseudoId, summaryBuilder.build())
                iconLoadingTargets.remove(this) // allow it to be garbage-collected
            }

            override fun onPrepareLoad(placeHolderDrawable: Drawable) {
                // Nothing to do
            }
        }

        // add the target to the list to hold a strong reference and prevent it from being garbage
        // collected, since Picasso only holds weak references to targets
        iconLoadingTargets.add(target)

        PicassoHelper.loadNotificationIcon(data.avatarUrl).into(target)
    }

    private fun showStreamNotifications(
        newStreams: List<StreamInfoItem>,
        serviceId: Int,
        channelIcon: Bitmap?
    ) {
        for (stream in newStreams) {
            val notification = createStreamNotification(stream, serviceId, channelIcon)
            manager.notify(stream.url.hashCode(), notification)
        }
    }

    private fun createStreamNotification(
        item: StreamInfoItem,
        serviceId: Int,
        channelIcon: Bitmap?
    ): Notification {
        return NotificationCompat.Builder(
            context,
            context.getString(R.string.streams_notification_channel_id)
        )
            .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
            .setLargeIcon(channelIcon)
            .setContentTitle(item.name)
            .setContentText(item.uploaderName)
            .setGroup(item.uploaderUrl)
            .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
            .setColorized(true)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setContentIntent(
                // Open the stream link in the player when clicking on the notification.
                PendingIntentCompat.getActivity(
                    context,
                    item.url.hashCode(),
                    NavigationHelper.getStreamIntent(context, serviceId, item.url, item.name),
                    PendingIntent.FLAG_UPDATE_CURRENT,
                    false
                )
            )
            .setSilent(true) // Avoid creating noise for individual stream notifications.
            .build()
    }

    companion object {
        /**
         * Check whether notifications are enabled on the device.
         * Users can disable them via the system settings for a single app.
         * If this is the case, the app cannot create any notifications
         * and display them to the user.
         * <br>
         * On Android 26 and above, notification channels are used by NewPipe.
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
                val importance = channel?.importance
                enabled && channel != null && importance != NotificationManager.IMPORTANCE_NONE
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
         * Open the system's notification settings for NewPipe on Android Oreo (API 26) and later.
         * Open the system's app settings for NewPipe on previous Android versions.
         */
        fun openNewPipeSystemNotificationSettings(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:" + context.packageName)
                context.startActivity(intent)
            }
        }
    }
}
