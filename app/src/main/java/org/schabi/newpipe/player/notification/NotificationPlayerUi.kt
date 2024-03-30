package org.schabi.newpipe.player.notification

import android.content.Intent
import android.graphics.Bitmap
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode
import org.schabi.newpipe.player.ui.PlayerUi

class NotificationPlayerUi(player: Player) : PlayerUi(player) {
    private val notificationUtil: NotificationUtil

    init {
        notificationUtil = NotificationUtil(player)
    }

    public override fun destroy() {
        super.destroy()
        notificationUtil.cancelNotificationAndStopForeground()
    }

    public override fun onThumbnailLoaded(bitmap: Bitmap?) {
        super.onThumbnailLoaded(bitmap)
        notificationUtil.updateThumbnail()
    }

    public override fun onBlocked() {
        super.onBlocked()
        notificationUtil.createNotificationIfNeededAndUpdate(false)
    }

    public override fun onPlaying() {
        super.onPlaying()
        notificationUtil.createNotificationIfNeededAndUpdate(false)
    }

    public override fun onBuffering() {
        super.onBuffering()
        if (notificationUtil.shouldUpdateBufferingSlot()) {
            notificationUtil.createNotificationIfNeededAndUpdate(false)
        }
    }

    public override fun onPaused() {
        super.onPaused()

        // Remove running notification when user does not want minimization to background or popup
        if ((PlayerHelper.getMinimizeOnExitAction(context) == MinimizeMode.Companion.MINIMIZE_ON_EXIT_MODE_NONE
                        && player.videoPlayerSelected())) {
            notificationUtil.cancelNotificationAndStopForeground()
        } else {
            notificationUtil.createNotificationIfNeededAndUpdate(false)
        }
    }

    public override fun onPausedSeek() {
        super.onPausedSeek()
        notificationUtil.createNotificationIfNeededAndUpdate(false)
    }

    public override fun onCompleted() {
        super.onCompleted()
        notificationUtil.createNotificationIfNeededAndUpdate(false)
    }

    public override fun onRepeatModeChanged(repeatMode: @com.google.android.exoplayer2.Player.RepeatMode Int) {
        super.onRepeatModeChanged(repeatMode)
        notificationUtil.createNotificationIfNeededAndUpdate(false)
    }

    public override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        super.onShuffleModeEnabledChanged(shuffleModeEnabled)
        notificationUtil.createNotificationIfNeededAndUpdate(false)
    }

    public override fun onBroadcastReceived(intent: Intent) {
        super.onBroadcastReceived(intent)
        if ((NotificationConstants.ACTION_RECREATE_NOTIFICATION == intent.getAction())) {
            notificationUtil.createNotificationIfNeededAndUpdate(true)
        }
    }

    public override fun onMetadataChanged(info: StreamInfo) {
        super.onMetadataChanged(info)
        notificationUtil.createNotificationIfNeededAndUpdate(true)
    }

    public override fun onPlayQueueEdited() {
        super.onPlayQueueEdited()
        notificationUtil.createNotificationIfNeededAndUpdate(false)
    }

    fun createNotificationAndStartForeground() {
        notificationUtil.createNotificationAndStartForeground()
    }
}
