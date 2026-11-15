/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.model

/**
 * Actions that can be placed in a slot of the player notification
 */

import newpipe.shared.generated.resources.Res
import newpipe.shared.generated.resources.close
import newpipe.shared.generated.resources.ic_close
import newpipe.shared.generated.resources.ic_fast_forward
import newpipe.shared.generated.resources.ic_fast_rewind
import newpipe.shared.generated.resources.ic_hourglass_top
import newpipe.shared.generated.resources.ic_next
import newpipe.shared.generated.resources.ic_pause
import newpipe.shared.generated.resources.ic_previous
import newpipe.shared.generated.resources.ic_repeat
import newpipe.shared.generated.resources.ic_shuffle
import newpipe.shared.generated.resources.notification_action_buffering
import newpipe.shared.generated.resources.notification_action_fast_forward
import newpipe.shared.generated.resources.notification_action_next
import newpipe.shared.generated.resources.notification_action_nothing
import newpipe.shared.generated.resources.notification_action_pause
import newpipe.shared.generated.resources.notification_action_play
import newpipe.shared.generated.resources.notification_action_previous
import newpipe.shared.generated.resources.notification_action_repeat
import newpipe.shared.generated.resources.notification_action_rewind
import newpipe.shared.generated.resources.notification_action_shuffle
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/**
 * Actions that can be placed in a slot of the player notification
 * @param id Value stored in the preferences, shared with the current Android app
 * @param labels Names of the actions combined in this slot, shown joined together
 * @param icon Icon shown next to the action, if any
 */
enum class NotificationAction(
    val id: Int,
    val labels: List<StringResource>,
    val icon: DrawableResource?
) {
    NOTHING(
        id = 0,
        labels = listOf(Res.string.notification_action_nothing),
        icon = null
    ),
    PREVIOUS(
        id = 1,
        labels = listOf(Res.string.notification_action_previous),
        icon = Res.drawable.ic_previous
    ),
    NEXT(
        id = 2,
        labels = listOf(Res.string.notification_action_next),
        icon = Res.drawable.ic_next
    ),
    REWIND(
        id = 3,
        labels = listOf(Res.string.notification_action_rewind),
        icon = Res.drawable.ic_fast_rewind
    ),
    FORWARD(
        id = 4,
        labels = listOf(Res.string.notification_action_fast_forward),
        icon = Res.drawable.ic_fast_forward
    ),
    SMART_REWIND_PREVIOUS(
        id = 5,
        labels = listOf(Res.string.notification_action_rewind, Res.string.notification_action_previous),
        icon = Res.drawable.ic_previous
    ),
    SMART_FORWARD_NEXT(
        id = 6,
        labels = listOf(Res.string.notification_action_fast_forward, Res.string.notification_action_next),
        icon = Res.drawable.ic_next
    ),
    PLAY_PAUSE(
        id = 7,
        labels = listOf(Res.string.notification_action_play, Res.string.notification_action_pause),
        icon = Res.drawable.ic_pause
    ),
    PLAY_PAUSE_BUFFERING(
        id = 8,
        labels = listOf(
            Res.string.notification_action_play,
            Res.string.notification_action_pause,
            Res.string.notification_action_buffering
        ),
        icon = Res.drawable.ic_hourglass_top
    ),
    REPEAT(
        id = 9,
        labels = listOf(Res.string.notification_action_repeat),
        icon = Res.drawable.ic_repeat
    ),
    SHUFFLE(
        id = 10,
        labels = listOf(Res.string.notification_action_shuffle),
        icon = Res.drawable.ic_shuffle
    ),
    CLOSE(
        id = 11,
        labels = listOf(Res.string.close),
        icon = Res.drawable.ic_close
    );

    companion object {
        fun fromId(id: Int): NotificationAction = entries.firstOrNull { action ->
            action.id == id
        } ?: NOTHING
    }
}
