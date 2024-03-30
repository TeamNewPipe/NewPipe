package org.schabi.newpipe.player.notification

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.mediasession.MediaSessionPlayerUi
import org.schabi.newpipe.util.NavigationHelper
import java.util.Objects
import java.util.Optional
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.ToIntFunction
import kotlin.math.min

/**
 * This is a utility class for player notifications.
 */
class NotificationUtil(private val player: Player) {
    @NotificationConstants.Action
    private val notificationSlots: IntArray = NotificationConstants.SLOT_DEFAULTS.clone()
    private var notificationManager: NotificationManagerCompat? = null
    private var notificationBuilder: NotificationCompat.Builder? = null
    /////////////////////////////////////////////////////
    // NOTIFICATION
    /////////////////////////////////////////////////////
    /**
     * Creates the notification if it does not exist already and recreates it if forceRecreate is
     * true. Updates the notification with the data in the player.
     * @param forceRecreate whether to force the recreation of the notification even if it already
     * exists
     */
    @Synchronized
    fun createNotificationIfNeededAndUpdate(forceRecreate: Boolean) {
        if (forceRecreate || notificationBuilder == null) {
            notificationBuilder = createNotification()
        }
        updateNotification()
        notificationManager!!.notify(NOTIFICATION_ID, notificationBuilder!!.build())
    }

    @Synchronized
    fun updateThumbnail() {
        if (notificationBuilder != null) {
            if (DEBUG) {
                Log.d(TAG, ("updateThumbnail() called with thumbnail = [" + Integer.toHexString(
                        Optional.ofNullable(player.getThumbnail()).map(Function({ o: Bitmap? -> Objects.hashCode(o) })).orElse(0))
                        + "], title = [" + player.getVideoTitle() + "]"))
            }
            setLargeIcon(notificationBuilder!!)
            notificationManager!!.notify(NOTIFICATION_ID, notificationBuilder!!.build())
        }
    }

    @Synchronized
    private fun createNotification(): NotificationCompat.Builder {
        if (DEBUG) {
            Log.d(TAG, "createNotification()")
        }
        notificationManager = NotificationManagerCompat.from(player.getContext())
        val builder: NotificationCompat.Builder = NotificationCompat.Builder(player.getContext(),
                player.getContext().getString(R.string.notification_channel_id))
        val mediaStyle: androidx.media.app.NotificationCompat.MediaStyle = androidx.media.app.NotificationCompat.MediaStyle()

        // setup media style (compact notification slots and media session)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // notification actions are ignored on Android 13+, and are replaced by code in
            // MediaSessionPlayerUi
            val compactSlots: IntArray = initializeNotificationSlots()
            mediaStyle.setShowActionsInCompactView(*compactSlots)
        }
        player.UIs()
                .get((MediaSessionPlayerUi::class.java))
                .flatMap(Function<MediaSessionPlayerUi?, Optional<out MediaSessionCompat.Token?>?>({ obj: MediaSessionPlayerUi? -> obj!!.getSessionToken() }))
                .ifPresent(Consumer({ token: MediaSessionCompat.Token? -> mediaStyle.setMediaSession(token) }))

        // setup notification builder
        builder.setStyle(mediaStyle)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setShowWhen(false)
                .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
                .setColor(ContextCompat.getColor(player.getContext(),
                        R.color.dark_background_color))
                .setColorized(player.getPrefs().getBoolean(
                        player.getContext().getString(R.string.notification_colorize_key), true))
                .setDeleteIntent(PendingIntentCompat.getBroadcast(player.getContext(),
                        NOTIFICATION_ID, Intent(NotificationConstants.ACTION_CLOSE), PendingIntent.FLAG_UPDATE_CURRENT, false))

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
        notificationBuilder!!.setContentIntent(PendingIntentCompat.getActivity(player.getContext(),
                NOTIFICATION_ID, (getIntentForNotification())!!, PendingIntent.FLAG_UPDATE_CURRENT, false))
        notificationBuilder!!.setContentTitle(player.getVideoTitle())
        notificationBuilder!!.setContentText(player.getUploaderName())
        notificationBuilder!!.setTicker(player.getVideoTitle())
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // notification actions are ignored on Android 13+, and are replaced by code in
            // MediaSessionPlayerUi
            updateActions(notificationBuilder)
        }
    }

    @SuppressLint("RestrictedApi")
    fun shouldUpdateBufferingSlot(): Boolean {
        if (notificationBuilder == null) {
            // if there is no notification active, there is no point in updating it
            return false
        } else if (notificationBuilder!!.mActions.size < 3) {
            // this should never happen, but let's make sure notification actions are populated
            return true
        }

        // only second and third slot could contain PLAY_PAUSE_BUFFERING, update them only if they
        // are not already in the buffering state (the only one with a null action intent)
        return ((notificationSlots.get(1) == NotificationConstants.PLAY_PAUSE_BUFFERING
                && notificationBuilder!!.mActions.get(1).actionIntent != null)
                || (notificationSlots.get(2) == NotificationConstants.PLAY_PAUSE_BUFFERING
                && notificationBuilder!!.mActions.get(2).actionIntent != null))
    }

    fun createNotificationAndStartForeground() {
        if (notificationBuilder == null) {
            notificationBuilder = createNotification()
        }
        updateNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            player.getService().startForeground(NOTIFICATION_ID, notificationBuilder!!.build(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            player.getService().startForeground(NOTIFICATION_ID, notificationBuilder!!.build())
        }
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
     * sent to the system
     */
    private fun initializeNotificationSlots(): IntArray {
        val settingsCompactSlots: Collection<Int?>? = NotificationConstants.getCompactSlotsFromPreferences(player.getContext(), player.getPrefs())
        val adjustedCompactSlots: MutableList<Int> = ArrayList()
        var nonNothingIndex: Int = 0
        for (i in 0..4) {
            notificationSlots.get(i) = player.getPrefs().getInt(
                    player.getContext().getString(NotificationConstants.SLOT_PREF_KEYS.get(i)),
                    NotificationConstants.SLOT_DEFAULTS.get(i))
            if (notificationSlots.get(i) != NotificationConstants.NOTHING) {
                if (settingsCompactSlots!!.contains(i)) {
                    adjustedCompactSlots.add(nonNothingIndex)
                }
                nonNothingIndex += 1
            }
        }
        return adjustedCompactSlots.stream().mapToInt(ToIntFunction({ obj: Int -> obj.toInt() })).toArray()
    }

    @SuppressLint("RestrictedApi")
    private fun updateActions(builder: NotificationCompat.Builder?) {
        builder!!.mActions.clear()
        for (i in 0..4) {
            addAction(builder, notificationSlots.get(i))
        }
    }

    private fun addAction(builder: NotificationCompat.Builder?,
                          @NotificationConstants.Action slot: Int) {
        val data: NotificationActionData? = NotificationActionData.Companion.fromNotificationActionEnum(player, slot)
        if (data == null) {
            return
        }
        val intent: PendingIntent? = PendingIntentCompat.getBroadcast(player.getContext(),
                NOTIFICATION_ID, Intent(data.action()), PendingIntent.FLAG_UPDATE_CURRENT, false)
        builder!!.addAction(NotificationCompat.Action(data.icon(), data.name(), intent))
    }

    private fun getIntentForNotification(): Intent? {
        if (player.audioPlayerSelected() || player.popupPlayerSelected()) {
            // Means we play in popup or audio only. Let's show the play queue
            return NavigationHelper.getPlayQueueActivityIntent(player.getContext())
        } else {
            // We are playing in fragment. Don't open another activity just show fragment. That's it
            val intent: Intent = NavigationHelper.getPlayerIntent(
                    player.getContext(), MainActivity::class.java, null, true)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.setAction(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_LAUNCHER)
            return intent
        }
    }

    /////////////////////////////////////////////////////
    // BITMAP
    /////////////////////////////////////////////////////
    private fun setLargeIcon(builder: NotificationCompat.Builder) {
        val showThumbnail: Boolean = player.getPrefs().getBoolean(
                player.getContext().getString(R.string.show_thumbnail_key), true)
        val thumbnail: Bitmap? = player.getThumbnail()
        if (thumbnail == null || !showThumbnail) {
            // since the builder is reused, make sure the thumbnail is unset if there is not one
            builder.setLargeIcon(null as Bitmap?)
            return
        }
        val scaleImageToSquareAspectRatio: Boolean = player.getPrefs().getBoolean(
                player.getContext().getString(R.string.scale_to_square_image_in_notifications_key),
                false)
        if (scaleImageToSquareAspectRatio) {
            builder.setLargeIcon(getBitmapWithSquareAspectRatio(thumbnail))
        } else {
            builder.setLargeIcon(thumbnail)
        }
    }

    private fun getBitmapWithSquareAspectRatio(bitmap: Bitmap): Bitmap {
        // Find the smaller dimension and then take a center portion of the image that
        // has that size.
        val w: Int = bitmap.getWidth()
        val h: Int = bitmap.getHeight()
        val dstSize: Int = min(w.toDouble(), h.toDouble()).toInt()
        val x: Int = (w - dstSize) / 2
        val y: Int = (h - dstSize) / 2
        return Bitmap.createBitmap(bitmap, x, y, dstSize, dstSize)
    }

    companion object {
        private val TAG: String = NotificationUtil::class.java.getSimpleName()
        private val DEBUG: Boolean = Player.Companion.DEBUG
        private val NOTIFICATION_ID: Int = 123789
    }
}
