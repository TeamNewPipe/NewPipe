/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.model

/**
 * Actions that can be placed in a slot of the player notification
 *
 */
enum class NotificationAction(val id: Int) {
    NOTHING(0),
    PREVIOUS(1),
    NEXT(2),
    REWIND(3),
    FORWARD(4),
    SMART_REWIND_PREVIOUS(5),
    SMART_FORWARD_NEXT(6),
    PLAY_PAUSE(7),
    PLAY_PAUSE_BUFFERING(8),
    REPEAT(9),
    SHUFFLE(10),
    CLOSE(11);

    companion object {
        fun fromId(id: Int): NotificationAction = entries.firstOrNull { action ->
            action.id == id
        } ?: NOTHING
    }
}
