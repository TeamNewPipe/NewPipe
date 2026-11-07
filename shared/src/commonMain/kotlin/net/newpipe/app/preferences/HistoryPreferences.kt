/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.preferences

/**
 * Preference keys and defaults for the History and cache settings screen.
 */
object HistoryPreferences {
    const val KEY_ENABLE_WATCH_HISTORY = "enable_watch_history"
    const val KEY_ENABLE_PLAYBACK_RESUME = "enable_playback_resume"
    const val KEY_ENABLE_PLAYBACK_STATE_LISTS = "enable_playback_state_lists"
    const val KEY_ENABLE_SEARCH_HISTORY = "enable_search_history"

    const val KEY_RECAPTCHA_COOKIES = "recaptcha_cookies_key"

    const val DEFAULT_ENABLE_WATCH_HISTORY = true
    const val DEFAULT_ENABLE_PLAYBACK_RESUME = true
    const val DEFAULT_ENABLE_PLAYBACK_STATE_LISTS = true
    const val DEFAULT_ENABLE_SEARCH_HISTORY = true
    const val DEFAULT_RECAPTCHA_COOKIES = ""
}
