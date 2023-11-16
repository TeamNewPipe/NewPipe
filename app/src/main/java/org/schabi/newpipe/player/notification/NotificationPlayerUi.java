package org.schabi.newpipe.player.notification;

import static org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_NONE;
import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_RECREATE_NOTIFICATION;

import android.content.Intent;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Player.RepeatMode;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.ui.PlayerUi;

public final class NotificationPlayerUi extends PlayerUi {
    private final NotificationUtil notificationUtil;

    public NotificationPlayerUi(@NonNull final Player player) {
        super(player);
        notificationUtil = new NotificationUtil(player);
    }

    @Override
    public void destroy() {
        super.destroy();
        notificationUtil.cancelNotificationAndStopForeground();
    }

    @Override
    public void onThumbnailLoaded(@Nullable final Bitmap bitmap) {
        super.onThumbnailLoaded(bitmap);
        notificationUtil.updateThumbnail();
    }

    @Override
    public void onBlocked() {
        super.onBlocked();
        notificationUtil.createNotificationIfNeededAndUpdate(false);
    }

    @Override
    public void onPlaying() {
        super.onPlaying();
        notificationUtil.createNotificationIfNeededAndUpdate(false);
    }

    @Override
    public void onBuffering() {
        super.onBuffering();
        if (notificationUtil.shouldUpdateBufferingSlot()) {
            notificationUtil.createNotificationIfNeededAndUpdate(false);
        }
    }

    @Override
    public void onPaused() {
        super.onPaused();

        // Remove running notification when user does not want minimization to background or popup
        if (PlayerHelper.getMinimizeOnExitAction(context) == MINIMIZE_ON_EXIT_MODE_NONE
                && player.videoPlayerSelected()) {
            notificationUtil.cancelNotificationAndStopForeground();
        } else {
            notificationUtil.createNotificationIfNeededAndUpdate(false);
        }
    }

    @Override
    public void onPausedSeek() {
        super.onPausedSeek();
        notificationUtil.createNotificationIfNeededAndUpdate(false);
    }

    @Override
    public void onCompleted() {
        super.onCompleted();
        notificationUtil.createNotificationIfNeededAndUpdate(false);
    }

    @Override
    public void onRepeatModeChanged(@RepeatMode final int repeatMode) {
        super.onRepeatModeChanged(repeatMode);
        notificationUtil.createNotificationIfNeededAndUpdate(false);
    }

    @Override
    public void onShuffleModeEnabledChanged(final boolean shuffleModeEnabled) {
        super.onShuffleModeEnabledChanged(shuffleModeEnabled);
        notificationUtil.createNotificationIfNeededAndUpdate(false);
    }

    @Override
    public void onBroadcastReceived(final Intent intent) {
        super.onBroadcastReceived(intent);
        if (ACTION_RECREATE_NOTIFICATION.equals(intent.getAction())) {
            notificationUtil.createNotificationIfNeededAndUpdate(true);
        }
    }

    @Override
    public void onMetadataChanged(@NonNull final StreamInfo info) {
        super.onMetadataChanged(info);
        notificationUtil.createNotificationIfNeededAndUpdate(true);
    }

    @Override
    public void onPlayQueueEdited() {
        super.onPlayQueueEdited();
        notificationUtil.createNotificationIfNeededAndUpdate(false);
    }

    public void createNotificationAndStartForeground() {
        notificationUtil.createNotificationAndStartForeground();
    }
}
