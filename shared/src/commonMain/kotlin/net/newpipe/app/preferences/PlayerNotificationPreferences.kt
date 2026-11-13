/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.preferences

import net.newpipe.app.model.NotificationAction

object PlayerNotificationPreferences {
    const val SLOT_COUNT = 5
    const val MAX_COMPACT_SLOTS = 3

    const val NO_COMPACT_SLOT = -1

    const val KEY_SCALE_TO_SQUARE_IMAGE = "scale_to_square_image_in_notifications"
    const val KEY_COLORIZE = "notification_colorize_key"
    const val KEY_SHOW_THUMBNAIL = "show_thumbnail_key"
    val KEYS_SLOT = List(SLOT_COUNT) { slot -> "notification_slot_${slot}_key" }
    val KEYS_COMPACT_SLOT = List(MAX_COMPACT_SLOTS) { index ->
        "notification_slot_compact_${index}_key"
    }

    const val DEFAULT_SCALE_TO_SQUARE_IMAGE = false
    const val DEFAULT_COLORIZE = true
    const val DEFAULT_SHOW_THUMBNAIL = true
    val DEFAULT_SLOT_ACTIONS = listOf(
        NotificationAction.SMART_REWIND_PREVIOUS,
        NotificationAction.PLAY_PAUSE_BUFFERING,
        NotificationAction.SMART_FORWARD_NEXT,
        NotificationAction.REPEAT,
        NotificationAction.CLOSE
    )
    val DEFAULT_COMPACT_SLOTS = setOf(0, 1, 2)
}
