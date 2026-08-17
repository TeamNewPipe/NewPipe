@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.player.notification

import android.content.Intent
import android.graphics.Bitmap
import androidx.media3.common.Player.RepeatMode
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.helper.PlayerHelper.MINIMIZE_ON_EXIT_MODE_NONE
import org.schabi.newpipe.player.ui.PlayerUi

class NotificationPlayerUi(player: Player) : PlayerUi(player) {
    private val notificationUtil = NotificationUtil(player)

    override fun destroy() {
        super.destroy()
        notificationUtil.cancelNotificationAndStopForeground()
    }

    override fun onThumbnailLoaded(bitmap: Bitmap?) {
        super.onThumbnailLoaded(bitmap)
        notificationUtil.updateThumbnail()
    }

    override fun onBlocked() {
        super.onBlocked()
        notificationUtil.createNotificationIfNeededAndUpdate(false)
    }

    override fun onPlaying() {
        super.onPlaying()
        notificationUtil.createNotificationIfNeededAndUpdate(false)
    }

    override fun onBuffering() {
        super.onBuffering()
        if (notificationUtil.shouldUpdateBufferingSlot()) {
            notificationUtil.createNotificationIfNeededAndUpdate(false)
        }
    }

    override fun onPaused() {
        super.onPaused()

        // Remove running notification when user does not want minimization to background or popup
        if (PlayerHelper.getMinimizeOnExitAction(context) == MINIMIZE_ON_EXIT_MODE_NONE &&
            player.videoPlayerSelected()
        ) {
            notificationUtil.cancelNotificationAndStopForeground()
        } else {
            notificationUtil.createNotificationIfNeededAndUpdate(false)
        }
    }

    override fun onPausedSeek() {
        super.onPausedSeek()
        notificationUtil.createNotificationIfNeededAndUpdate(false)
    }

    override fun onCompleted() {
        super.onCompleted()
        notificationUtil.createNotificationIfNeededAndUpdate(false)
    }

    override fun onRepeatModeChanged(@RepeatMode repeatMode: Int) {
        super.onRepeatModeChanged(repeatMode)
        notificationUtil.createNotificationIfNeededAndUpdate(false)
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        super.onShuffleModeEnabledChanged(shuffleModeEnabled)
        notificationUtil.createNotificationIfNeededAndUpdate(false)
    }

    override fun onBroadcastReceived(intent: Intent) {
        super.onBroadcastReceived(intent)
        if (NotificationConstants.ACTION_RECREATE_NOTIFICATION == intent.action) {
            notificationUtil.createNotificationIfNeededAndUpdate(true)
        }
    }

    override fun onMetadataChanged(info: StreamInfo) {
        super.onMetadataChanged(info)
        notificationUtil.createNotificationIfNeededAndUpdate(true)
    }

    override fun onPlayQueueEdited() {
        super.onPlayQueueEdited()
        notificationUtil.createNotificationIfNeededAndUpdate(false)
    }

    fun createNotificationAndStartForeground() {
        notificationUtil.createNotificationAndStartForeground()
    }
}
