/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.viewmodel.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.newpipe.app.platform.HistoryActions
import net.newpipe.app.preferences.HistoryPreferences.DEFAULT_ENABLE_PLAYBACK_RESUME
import net.newpipe.app.preferences.HistoryPreferences.DEFAULT_ENABLE_PLAYBACK_STATE_LISTS
import net.newpipe.app.preferences.HistoryPreferences.DEFAULT_ENABLE_SEARCH_HISTORY
import net.newpipe.app.preferences.HistoryPreferences.DEFAULT_ENABLE_WATCH_HISTORY
import net.newpipe.app.preferences.HistoryPreferences.DEFAULT_RECAPTCHA_COOKIES
import net.newpipe.app.preferences.HistoryPreferences.KEY_ENABLE_PLAYBACK_RESUME
import net.newpipe.app.preferences.HistoryPreferences.KEY_ENABLE_PLAYBACK_STATE_LISTS
import net.newpipe.app.preferences.HistoryPreferences.KEY_ENABLE_SEARCH_HISTORY
import net.newpipe.app.preferences.HistoryPreferences.KEY_ENABLE_WATCH_HISTORY
import net.newpipe.app.preferences.HistoryPreferences.KEY_RECAPTCHA_COOKIES
import org.koin.core.annotation.KoinViewModel

enum class HistoryMessage {
    METADATA_CACHE_WIPED,
    WATCH_HISTORY_DELETED,
    PLAYBACK_STATES_DELETED,
    SEARCH_HISTORY_DELETED,
    RECAPTCHA_COOKIES_CLEARED
}

@KoinViewModel
class HistorySettingsViewModel(
    private val settings: Settings,
    private val historyActions: HistoryActions
) : ViewModel() {

    val watchHistoryEnabled: StateFlow<Boolean>
        field = booleanFlow(KEY_ENABLE_WATCH_HISTORY, DEFAULT_ENABLE_WATCH_HISTORY)

    val playbackResumeEnabled: StateFlow<Boolean>
        field = booleanFlow(KEY_ENABLE_PLAYBACK_RESUME, DEFAULT_ENABLE_PLAYBACK_RESUME)

    val playbackStateListsEnabled: StateFlow<Boolean>
        field = booleanFlow(
            KEY_ENABLE_PLAYBACK_STATE_LISTS,
            DEFAULT_ENABLE_PLAYBACK_STATE_LISTS
        )

    val searchHistoryEnabled: StateFlow<Boolean>
        field = booleanFlow(KEY_ENABLE_SEARCH_HISTORY, DEFAULT_ENABLE_SEARCH_HISTORY)

    val recaptchaCookiesPresent: StateFlow<Boolean>
        field = MutableStateFlow(storedRecaptchaCookies().isNotEmpty())

    val message: StateFlow<HistoryMessage?>
        field = MutableStateFlow<HistoryMessage?>(null)

    fun setWatchHistoryEnabled(value: Boolean) = watchHistoryEnabled
        .persist(KEY_ENABLE_WATCH_HISTORY, value)

    fun setPlaybackResumeEnabled(value: Boolean) = playbackResumeEnabled
        .persist(KEY_ENABLE_PLAYBACK_RESUME, value)

    fun setPlaybackStateListsEnabled(value: Boolean) = playbackStateListsEnabled
        .persist(KEY_ENABLE_PLAYBACK_STATE_LISTS, value)

    fun setSearchHistoryEnabled(value: Boolean) = searchHistoryEnabled
        .persist(KEY_ENABLE_SEARCH_HISTORY, value)

    fun wipeMetadataCache() {
        historyActions.wipeMetadataCache()
        message.value = HistoryMessage.METADATA_CACHE_WIPED
    }

    fun clearWatchHistory() = clearThenReport(
        success = HistoryMessage.WATCH_HISTORY_DELETED,
        deletion = historyActions::clearWatchHistory
    )

    fun clearPlaybackStates() = clearThenReport(
        success = HistoryMessage.PLAYBACK_STATES_DELETED,
        deletion = historyActions::clearPlaybackStates
    )

    fun clearSearchHistory() = clearThenReport(
        success = HistoryMessage.SEARCH_HISTORY_DELETED,
        deletion = historyActions::clearSearchHistory
    )

    fun clearRecaptchaCookies() {
        historyActions.clearRecaptchaCookies()
        settings.putString(KEY_RECAPTCHA_COOKIES, DEFAULT_RECAPTCHA_COOKIES)
        recaptchaCookiesPresent.value = false
        message.value = HistoryMessage.RECAPTCHA_COOKIES_CLEARED
    }

    fun onMessageShown() {
        message.value = null
    }

    private fun clearThenReport(success: HistoryMessage, deletion: suspend () -> Boolean) {
        viewModelScope.launch {
            if (deletion()) {
                message.value = success
            }
        }
    }

    private fun storedRecaptchaCookies() = settings.getString(KEY_RECAPTCHA_COOKIES, DEFAULT_RECAPTCHA_COOKIES)

    private fun booleanFlow(key: String, defaultValue: Boolean) = MutableStateFlow(settings.getBoolean(key, defaultValue))

    private fun MutableStateFlow<Boolean>.persist(key: String, newValue: Boolean) {
        settings.putBoolean(key, newValue)
        value = newValue
    }
}
