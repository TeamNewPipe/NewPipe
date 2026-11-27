/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.preferences

/**
 * Preference keys and default values for the Panic settings screen
 */
object PanicPreferences {
    const val KEY_TRIGGER_APPS = "key_trigger_apps"
    const val KEY_REMOVE_FROM_RECENT_APPS = "key_remove_from_recent_apps"
    const val KEY_EXIT_APP = "key_exit_app"
    const val KEY_CLEAR_APP_DATABASE = "key_clear_app_database"
    const val KEY_CLEAR_SEARCH_HISTORY = "key_clear_search_history"
    const val KEY_CLEAR_WATCH_HISTORY = "key_clear_watch_history"

    val DEFAULT_TRIGGER_APPS = null
    const val DEFAULT_REMOVE_FROM_RECENT_APPS = true
    const val DEFAULT_EXIT_APP = true
    const val DEFAULT_CLEAR_APP_DATABASE = false
    const val DEFAULT_CLEAR_SEARCH_HISTORY = false
    const val DEFAULT_CLEAR_WATCH_HISTORY = false
}
