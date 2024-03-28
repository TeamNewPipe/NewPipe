package org.schabi.newpipe.player.notification;

import static com.google.android.exoplayer2.Player.REPEAT_MODE_ALL;
import static com.google.android.exoplayer2.Player.REPEAT_MODE_ONE;
import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_CLOSE;
import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_FAST_FORWARD;
import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_FAST_REWIND;
import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_PLAY_NEXT;
import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_PLAY_PAUSE;
import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_PLAY_PREVIOUS;
import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_REPEAT;
import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_SHUFFLE;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.R;
import org.schabi.newpipe.player.Player;

import java.util.Objects;

public final class NotificationActionData {

    @NonNull
    private final String action;
    @NonNull
    private final String name;
    @DrawableRes
    private final int icon;


    public NotificationActionData(@NonNull final String action, @NonNull final String name,
                                  @DrawableRes final int icon) {
        this.action = action;
        this.name = name;
        this.icon = icon;
    }

    @NonNull
    public String action() {
        return action;
    }

    @NonNull
    public String name() {
        return name;
    }

    @DrawableRes
    public int icon() {
        return icon;
    }


    @SuppressLint("PrivateResource") // we currently use Exoplayer's internal strings and icons
    @Nullable
    public static NotificationActionData fromNotificationActionEnum(
            @NonNull final Player player,
            @NotificationConstants.Action final int selectedAction
    ) {

        final int baseActionIcon = NotificationConstants.ACTION_ICONS[selectedAction];
        final Context ctx = player.getContext();

        switch (selectedAction) {
            case NotificationConstants.PREVIOUS:
                return new NotificationActionData(ACTION_PLAY_PREVIOUS,
                        ctx.getString(com.google.android.exoplayer2.ui.R.string
                                .exo_controls_previous_description), baseActionIcon);

            case NotificationConstants.NEXT:
                return new NotificationActionData(ACTION_PLAY_NEXT,
                        ctx.getString(com.google.android.exoplayer2.ui.R.string
                                .exo_controls_next_description), baseActionIcon);

            case NotificationConstants.REWIND:
                return new NotificationActionData(ACTION_FAST_REWIND,
                        ctx.getString(com.google.android.exoplayer2.ui.R.string
                                .exo_controls_rewind_description), baseActionIcon);

            case NotificationConstants.FORWARD:
                return new NotificationActionData(ACTION_FAST_FORWARD,
                        ctx.getString(com.google.android.exoplayer2.ui.R.string
                                .exo_controls_fastforward_description), baseActionIcon);

            case NotificationConstants.SMART_REWIND_PREVIOUS:
                if (player.getPlayQueue() != null && player.getPlayQueue().size() > 1) {
                    return new NotificationActionData(ACTION_PLAY_PREVIOUS,
                            ctx.getString(com.google.android.exoplayer2.ui.R.string
                                    .exo_controls_previous_description),
                            com.google.android.exoplayer2.ui.R.drawable.exo_notification_previous);
                } else {
                    return new NotificationActionData(ACTION_FAST_REWIND,
                            ctx.getString(com.google.android.exoplayer2.ui.R.string
                                    .exo_controls_rewind_description),
                            com.google.android.exoplayer2.ui.R.drawable.exo_controls_rewind);
                }

            case NotificationConstants.SMART_FORWARD_NEXT:
                if (player.getPlayQueue() != null && player.getPlayQueue().size() > 1) {
                    return new NotificationActionData(ACTION_PLAY_NEXT,
                            ctx.getString(com.google.android.exoplayer2.ui.R.string
                                    .exo_controls_next_description),
                            com.google.android.exoplayer2.ui.R.drawable.exo_notification_next);
                } else {
                    return new NotificationActionData(ACTION_FAST_FORWARD,
                            ctx.getString(com.google.android.exoplayer2.ui.R.string
                                    .exo_controls_fastforward_description),
                            com.google.android.exoplayer2.ui.R.drawable.exo_controls_fastforward);
                }

            case NotificationConstants.PLAY_PAUSE_BUFFERING:
                if (player.getCurrentState() == Player.STATE_PREFLIGHT
                        || player.getCurrentState() == Player.STATE_BLOCKED
                        || player.getCurrentState() == Player.STATE_BUFFERING) {
                    return new NotificationActionData(ACTION_PLAY_PAUSE,
                            ctx.getString(R.string.notification_action_buffering),
                            R.drawable.ic_hourglass_top);
                }

                // fallthrough
            case NotificationConstants.PLAY_PAUSE:
                if (player.getCurrentState() == Player.STATE_COMPLETED) {
                    return new NotificationActionData(ACTION_PLAY_PAUSE,
                            ctx.getString(com.google.android.exoplayer2.ui.R.string
                                    .exo_controls_pause_description),
                            R.drawable.ic_replay);
                } else if (player.isPlaying()
                        || player.getCurrentState() == Player.STATE_PREFLIGHT
                        || player.getCurrentState() == Player.STATE_BLOCKED
                        || player.getCurrentState() == Player.STATE_BUFFERING) {
                    return new NotificationActionData(ACTION_PLAY_PAUSE,
                            ctx.getString(com.google.android.exoplayer2.ui.R.string
                                    .exo_controls_pause_description),
                            com.google.android.exoplayer2.ui.R.drawable.exo_notification_pause);
                } else {
                    return new NotificationActionData(ACTION_PLAY_PAUSE,
                            ctx.getString(com.google.android.exoplayer2.ui.R.string
                                    .exo_controls_play_description),
                            com.google.android.exoplayer2.ui.R.drawable.exo_notification_play);
                }

            case NotificationConstants.REPEAT:
                if (player.getRepeatMode() == REPEAT_MODE_ALL) {
                    return new NotificationActionData(ACTION_REPEAT,
                            ctx.getString(com.google.android.exoplayer2.ui.R.string
                                    .exo_controls_repeat_all_description),
                            com.google.android.exoplayer2.ext.mediasession.R.drawable
                                    .exo_media_action_repeat_all);
                } else if (player.getRepeatMode() == REPEAT_MODE_ONE) {
                    return new NotificationActionData(ACTION_REPEAT,
                            ctx.getString(com.google.android.exoplayer2.ui.R.string
                                    .exo_controls_repeat_one_description),
                            com.google.android.exoplayer2.ext.mediasession.R.drawable
                                    .exo_media_action_repeat_one);
                } else /* player.getRepeatMode() == REPEAT_MODE_OFF */ {
                    return new NotificationActionData(ACTION_REPEAT,
                            ctx.getString(com.google.android.exoplayer2.ui.R.string
                                    .exo_controls_repeat_off_description),
                            com.google.android.exoplayer2.ext.mediasession.R.drawable
                                    .exo_media_action_repeat_off);
                }

            case NotificationConstants.SHUFFLE:
                if (player.getPlayQueue() != null && player.getPlayQueue().isShuffled()) {
                    return new NotificationActionData(ACTION_SHUFFLE,
                            ctx.getString(com.google.android.exoplayer2.ui.R.string
                                    .exo_controls_shuffle_on_description),
                            com.google.android.exoplayer2.ui.R.drawable.exo_controls_shuffle_on);
                } else {
                    return new NotificationActionData(ACTION_SHUFFLE,
                            ctx.getString(com.google.android.exoplayer2.ui.R.string
                                    .exo_controls_shuffle_off_description),
                            com.google.android.exoplayer2.ui.R.drawable.exo_controls_shuffle_off);
                }

            case NotificationConstants.CLOSE:
                return new NotificationActionData(ACTION_CLOSE, ctx.getString(R.string.close),
                        R.drawable.ic_close);

            case NotificationConstants.NOTHING:
            default:
                // do nothing
                return null;
        }
    }


    @Override
    public boolean equals(@Nullable final Object obj) {
        return (obj instanceof NotificationActionData other)
                && this.action.equals(other.action)
                && this.name.equals(other.name)
                && this.icon == other.icon;
    }

    @Override
    public int hashCode() {
        return Objects.hash(action, name, icon);
    }
}
