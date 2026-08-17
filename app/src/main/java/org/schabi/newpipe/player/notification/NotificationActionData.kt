@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.player.notification

import android.annotation.SuppressLint
import androidx.annotation.DrawableRes
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_ONE
import org.schabi.newpipe.R
import org.schabi.newpipe.player.Player

data class NotificationActionData(
    val action: String,
    val name: String,
    @DrawableRes val icon: Int
) {
    companion object {
        @SuppressLint("PrivateResource") // we currently use Exoplayer's internal strings and icons
        @JvmStatic
        fun fromNotificationActionEnum(
            player: Player,
            @NotificationConstants.Action selectedAction: Int
        ): NotificationActionData? {
            val baseActionIcon = NotificationConstants.ACTION_ICONS[selectedAction]
            val ctx = player.context

            return when (selectedAction) {
                NotificationConstants.PREVIOUS -> NotificationActionData(
                    NotificationConstants.ACTION_PLAY_PREVIOUS,
                    ctx.getString(androidx.media3.ui.R.string.exo_controls_previous_description),
                    baseActionIcon
                )

                NotificationConstants.NEXT -> NotificationActionData(
                    NotificationConstants.ACTION_PLAY_NEXT,
                    ctx.getString(androidx.media3.ui.R.string.exo_controls_next_description),
                    baseActionIcon
                )

                NotificationConstants.REWIND -> NotificationActionData(
                    NotificationConstants.ACTION_FAST_REWIND,
                    ctx.getString(androidx.media3.ui.R.string.exo_controls_rewind_description),
                    baseActionIcon
                )

                NotificationConstants.FORWARD -> NotificationActionData(
                    NotificationConstants.ACTION_FAST_FORWARD,
                    ctx.getString(androidx.media3.ui.R.string.exo_controls_fastforward_description),
                    baseActionIcon
                )

                NotificationConstants.SMART_REWIND_PREVIOUS -> if (player.playQueue != null && player.playQueue!!.size() > 1) {
                    NotificationActionData(
                        NotificationConstants.ACTION_PLAY_PREVIOUS,
                        ctx.getString(androidx.media3.ui.R.string.exo_controls_previous_description),
                        androidx.media3.ui.R.drawable.exo_icon_previous
                    )
                } else {
                    NotificationActionData(
                        NotificationConstants.ACTION_FAST_REWIND,
                        ctx.getString(androidx.media3.ui.R.string.exo_controls_rewind_description),
                        androidx.media3.ui.R.drawable.exo_icon_rewind
                    )
                }

                NotificationConstants.SMART_FORWARD_NEXT -> if (player.playQueue != null && player.playQueue!!.size() > 1) {
                    NotificationActionData(
                        NotificationConstants.ACTION_PLAY_NEXT,
                        ctx.getString(androidx.media3.ui.R.string.exo_controls_next_description),
                        androidx.media3.ui.R.drawable.exo_icon_next
                    )
                } else {
                    NotificationActionData(
                        NotificationConstants.ACTION_FAST_FORWARD,
                        ctx.getString(androidx.media3.ui.R.string.exo_controls_fastforward_description),
                        androidx.media3.ui.R.drawable.exo_icon_fastforward
                    )
                }

                NotificationConstants.PLAY_PAUSE_BUFFERING -> if (player.currentState == Player.STATE_PREFLIGHT ||
                    player.currentState == Player.STATE_BLOCKED ||
                    player.currentState == Player.STATE_BUFFERING
                ) {
                    NotificationActionData(
                        NotificationConstants.ACTION_PLAY_PAUSE,
                        ctx.getString(R.string.notification_action_buffering),
                        R.drawable.ic_hourglass_top
                    )
                } else {
                    fromNotificationActionEnum(player, NotificationConstants.PLAY_PAUSE)
                }

                NotificationConstants.PLAY_PAUSE -> if (player.currentState == Player.STATE_COMPLETED) {
                    NotificationActionData(
                        NotificationConstants.ACTION_PLAY_PAUSE,
                        ctx.getString(androidx.media3.ui.R.string.exo_controls_pause_description),
                        R.drawable.ic_hourglass_top
                    )
                } else if (player.isPlaying() ||
                    player.currentState == Player.STATE_PREFLIGHT ||
                    player.currentState == Player.STATE_BLOCKED ||
                    player.currentState == Player.STATE_BUFFERING
                ) {
                    NotificationActionData(
                        NotificationConstants.ACTION_PLAY_PAUSE,
                        ctx.getString(androidx.media3.ui.R.string.exo_controls_pause_description),
                        androidx.media3.ui.R.drawable.exo_icon_pause
                    )
                } else {
                    NotificationActionData(
                        NotificationConstants.ACTION_PLAY_PAUSE,
                        ctx.getString(androidx.media3.ui.R.string.exo_controls_play_description),
                        androidx.media3.ui.R.drawable.exo_icon_play
                    )
                }

                NotificationConstants.REPEAT -> if (player.repeatMode == REPEAT_MODE_ALL) {
                    NotificationActionData(
                        NotificationConstants.ACTION_REPEAT,
                        ctx.getString(androidx.media3.ui.R.string.exo_controls_repeat_all_description),
                        androidx.media3.ui.R.drawable.exo_icon_repeat_all
                    )
                } else if (player.repeatMode == REPEAT_MODE_ONE) {
                    NotificationActionData(
                        NotificationConstants.ACTION_REPEAT,
                        ctx.getString(androidx.media3.ui.R.string.exo_controls_repeat_one_description),
                        androidx.media3.ui.R.drawable.exo_icon_repeat_one
                    )
                } else {
                    /* player.getRepeatMode() == REPEAT_MODE_OFF */
                    NotificationActionData(
                        NotificationConstants.ACTION_REPEAT,
                        ctx.getString(androidx.media3.ui.R.string.exo_controls_repeat_off_description),
                        androidx.media3.ui.R.drawable.exo_icon_repeat_off
                    )
                }

                NotificationConstants.SHUFFLE -> if (player.playQueue != null && player.playQueue!!.isShuffled) {
                    NotificationActionData(
                        NotificationConstants.ACTION_SHUFFLE,
                        ctx.getString(androidx.media3.ui.R.string.exo_controls_shuffle_on_description),
                        androidx.media3.ui.R.drawable.exo_icon_shuffle_on
                    )
                } else {
                    NotificationActionData(
                        NotificationConstants.ACTION_SHUFFLE,
                        ctx.getString(androidx.media3.ui.R.string.exo_controls_shuffle_off_description),
                        androidx.media3.ui.R.drawable.exo_icon_shuffle_off
                    )
                }

                NotificationConstants.CLOSE -> NotificationActionData(
                    NotificationConstants.ACTION_CLOSE,
                    ctx.getString(R.string.close),
                    R.drawable.ic_close
                )

                else -> null
            }
        }
    }
}
