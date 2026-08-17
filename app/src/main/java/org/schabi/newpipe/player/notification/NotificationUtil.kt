package org.schabi.newpipe.player.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.DebugConstants
import org.schabi.newpipe.R
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.PlayerIntentType
import org.schabi.newpipe.player.PlayerService
import org.schabi.newpipe.player.mediasession.MediaSessionPlayerUi
import org.schabi.newpipe.util.NavigationHelper
import java.util.Optional

/**
 * This is a utility class for player notifications.
 */
class NotificationUtil(private val player: Player) {

    @NotificationConstants.Action
    private val notificationSlots = NotificationConstants.SLOT_DEFAULTS.clone()

    private var notificationManager: NotificationManagerCompat? = null
    private var notificationBuilder: NotificationCompat.Builder? = null

    /**
     * Creates the notification if it does not exist already and recreates it if forceRecreate is
     * true. Updates the notification with the data in the player.
     * @param forceRecreate whether to force the recreation of the notification even if it already
     *                      exists
     */
    @Synchronized
    fun createNotificationIfNeededAndUpdate(forceRecreate: Boolean) {
        if (forceRecreate || notificationBuilder == null) {
            notificationBuilder = createNotification()
        }
        updateNotification()
        if (notificationManager!!.areNotificationsEnabled()) {
            notificationManager!!.notify(NOTIFICATION_ID, notificationBuilder!!.build())
        }
    }

    @Synchronized
    fun updateThumbnail() {
        if (notificationBuilder != null) {
            if (DEBUG) {
                Log.d(
                    TAG, "updateThumbnail() called with thumbnail = [" + Integer.toHexString(
                        Optional.ofNullable(player.getThumbnail()).map { it.hashCode() }.orElse(0)
                    ) + "], title = [" + player.videoTitle + "]"
                )
            }

            setLargeIcon(notificationBuilder!!)
            if (notificationManager!!.areNotificationsEnabled()) {
                notificationManager!!.notify(NOTIFICATION_ID, notificationBuilder!!.build())
            }
        }
    }

    @Synchronized
    private fun createNotification(): NotificationCompat.Builder {
        if (DEBUG) {
            Log.d(TAG, "createNotification()")
        }
        notificationManager = NotificationManagerCompat.from(player.context)

        // setup media style (compact notification slots and media session)
        val mediaStyle = MediaStyle()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // notification actions are ignored on Android 13+, and are replaced by code in
            // MediaSessionPlayerUi
            val compactSlots = initializeNotificationSlots()
            mediaStyle.setShowActionsInCompactView(*compactSlots)
        }
        mediaStyle.setMediaSession(player.mediaSessionToken)

        // setup notification builder
        val builder = setupNotificationBuilder(player.context, mediaStyle)
            .setColorized(
                player.prefs.getBoolean(
                    player.context.getString(R.string.notification_colorize_key), true
                )
            )

        // set the initial value for the video thumbnail, updatable with updateNotificationThumbnail
        setLargeIcon(builder)

        return builder
    }

    /**
     * Updates the notification builder and the button icons depending on the playback state.
     */
    @Synchronized
    private fun updateNotification() {
        if (DEBUG) {
            Log.d(TAG, "updateNotification()")
        }

        // also update content intent, in case the user switched players
        notificationBuilder!!.setContentIntent(
            PendingIntentCompat.getActivity(
                player.context,
                NOTIFICATION_ID, getIntentForNotification(), PendingIntent.FLAG_UPDATE_CURRENT, false
            )
        )
        notificationBuilder!!.setContentTitle(player.videoTitle)
        notificationBuilder!!.setContentText(player.uploaderName)
        notificationBuilder!!.setTicker(player.videoTitle)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // notification actions are ignored on Android 13+, and are replaced by code in
            // MediaSessionPlayerUi
            updateActions(notificationBuilder!!)
        }
    }

    @SuppressLint("RestrictedApi")
    fun shouldUpdateBufferingSlot(): Boolean {
        return if (notificationBuilder == null) {
            // if there is no notification active, there is no point in updating it
            false
        } else if (notificationBuilder!!.mActions.size < 3) {
            // this should never happen, but let's make sure notification actions are populated
            true
        } else {
            // only second and third slot could contain PLAY_PAUSE_BUFFERING, update them only if they
            // are not already in the buffering state (the only one with a null action intent)
            (notificationSlots[1] == NotificationConstants.PLAY_PAUSE_BUFFERING &&
                notificationBuilder!!.mActions[1].actionIntent != null) ||
                (notificationSlots[2] == NotificationConstants.PLAY_PAUSE_BUFFERING &&
                    notificationBuilder!!.mActions[2].actionIntent != null)
        }
    }

    fun createNotificationAndStartForeground() {
        if (notificationBuilder == null) {
            notificationBuilder = createNotification()
        }
        updateNotification()
        startForeground(player.getService(), notificationBuilder!!.build())
    }

    fun cancelNotificationAndStopForeground() {
        ServiceCompat.stopForeground(player.getService(), ServiceCompat.STOP_FOREGROUND_REMOVE)

        if (notificationManager != null) {
            notificationManager!!.cancel(NOTIFICATION_ID)
        }
        notificationManager = null
        notificationBuilder = null
    }

    /////////////////////////////////////////////////////
    // ACTIONS
    /////////////////////////////////////////////////////

    /**
     * The compact slots array from settings contains indices from 0 to 4, each referring to one of
     * the five actions configurable by the user. However, if the user sets an action to "Nothing",
     * then all of the actions coming after will have a "settings index" different than the index
     * of the corresponding action when sent to the system.
     *
     * @return the indices of compact slots referred to the list of non-nothing actions that will be
     *         sent to the system
     */
    private fun initializeNotificationSlots(): IntArray {
        val settingsCompactSlots = NotificationConstants
            .getCompactSlotsFromPreferences(player.context, player.prefs)
        val adjustedCompactSlots = mutableListOf<Int>()

        var nonNothingIndex = 0
        for (i in 0..4) {
            notificationSlots[i] = player.prefs.getInt(
                player.context.getString(NotificationConstants.SLOT_PREF_KEYS[i]),
                NotificationConstants.SLOT_DEFAULTS[i]
            )

            if (notificationSlots[i] != NotificationConstants.NOTHING) {
                if (settingsCompactSlots.contains(i)) {
                    adjustedCompactSlots.add(nonNothingIndex)
                }
                nonNothingIndex += 1
            }
        }

        return adjustedCompactSlots.toIntArray()
    }

    @SuppressLint("RestrictedApi")
    private fun updateActions(builder: NotificationCompat.Builder) {
        builder.mActions.clear()
        for (i in 0..4) {
            addAction(builder, notificationSlots[i])
        }
    }

    private fun addAction(
        builder: NotificationCompat.Builder,
        @NotificationConstants.Action slot: Int
    ) {
        val data = NotificationActionData.fromNotificationActionEnum(player, slot) ?: return

        val intent = PendingIntentCompat.getBroadcast(
            player.context,
            NOTIFICATION_ID, Intent(data.action), PendingIntent.FLAG_UPDATE_CURRENT, false
        )
        builder.addAction(NotificationCompat.Action(data.icon, data.name, intent))
    }

    private fun getIntentForNotification(): Intent {
        return if (player.audioPlayerSelected() || player.popupPlayerSelected()) {
            // Means we play in popup or audio only. Let's show the play queue
            Intent(player.context, MainActivity::class.java)
        } else {
            // We are playing in fragment. Don't open another activity just show fragment. That's it
            val intent = Intent(player.context, MainActivity::class.java)
            intent.putExtra(Player.RESUME_PLAYBACK, true)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.action = Intent.ACTION_MAIN
            intent.addCategory(Intent.CATEGORY_LAUNCHER)
            intent
        }
    }

    /////////////////////////////////////////////////////
    // BITMAP
    /////////////////////////////////////////////////////

    private fun setLargeIcon(builder: NotificationCompat.Builder) {
        val showThumbnail = player.prefs.getBoolean(
            player.context.getString(R.string.show_thumbnail_key), true
        )
        val thumbnail = player.getThumbnail()
        if (thumbnail == null || !showThumbnail) {
            // since the builder is reused, make sure the thumbnail is unset if there is not one
            builder.setLargeIcon(null as Bitmap?)
            return
        }

        val scaleImageToSquareAspectRatio = player.prefs.getBoolean(
            player.context.getString(R.string.scale_to_square_image_in_notifications_key),
            false
        )
        if (scaleImageToSquareAspectRatio) {
            builder.setLargeIcon(getBitmapWithSquareAspectRatio(thumbnail))
        } else {
            builder.setLargeIcon(thumbnail)
        }
    }

    private fun getBitmapWithSquareAspectRatio(bitmap: Bitmap): Bitmap {
        // Find the smaller dimension and then take a center portion of the image that
        // has that size.
        val w = bitmap.width
        val h = bitmap.height
        val dstSize = Math.min(w, h)
        val x = (w - dstSize) / 2
        val y = (h - dstSize) / 2
        return Bitmap.createBitmap(bitmap, x, y, dstSize, dstSize)
    }

    companion object {
        private val TAG = NotificationUtil::class.java.simpleName
        private val DEBUG = DebugConstants.DEBUG
        private const val NOTIFICATION_ID = 123789

        @JvmStatic
        fun startForegroundWithDummyNotification(service: PlayerService) {
            val builder = setupNotificationBuilder(service, MediaStyle())
            startForeground(service, builder.build())
        }

        private fun setupNotificationBuilder(
            context: Context,
            style: MediaStyle
        ): NotificationCompat.Builder {
            return NotificationCompat.Builder(context, context.getString(R.string.notification_channel_id))
                .setStyle(style)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setShowWhen(false)
                .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
                .setColor(ContextCompat.getColor(context, R.color.dark_background_color))
                .setDeleteIntent(
                    PendingIntentCompat.getBroadcast(
                        context,
                        NOTIFICATION_ID, Intent(NotificationConstants.ACTION_CLOSE),
                        PendingIntent.FLAG_UPDATE_CURRENT, false
                    )
                )
        }

        private fun startForeground(service: PlayerService, notification: Notification) {
            // ServiceInfo constants are not used below Android Q, so 0 is set here
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
            ServiceCompat.startForeground(service, NOTIFICATION_ID, notification, serviceType)
        }
    }
}
