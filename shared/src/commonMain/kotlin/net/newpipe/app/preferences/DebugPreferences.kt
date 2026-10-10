/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.preferences

/**
 * Preference keys and defaults for the Debug settings screen.
 *
 */
object DebugPreferences {
    const val KEY_ALLOW_HEAP_DUMPING = "allow_heap_dumping_key"
    const val KEY_ALLOW_DISPOSED_EXCEPTIONS = "allow_disposed_exceptions_key"
    const val KEY_SHOW_ORIGINAL_TIME_AGO = "show_original_time_ago_key"
    const val KEY_SHOW_CRASH_THE_PLAYER = "show_crash_the_player_key"

    const val DEFAULT_ALLOW_HEAP_DUMPING = false
    const val DEFAULT_ALLOW_DISPOSED_EXCEPTIONS = false
    const val DEFAULT_SHOW_ORIGINAL_TIME_AGO = false
    const val DEFAULT_SHOW_CRASH_THE_PLAYER = false
}