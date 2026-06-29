/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
*/

package net.newpipe.app.viewmodel.settings.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.russhwolf.settings.ObservableSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.newpipe.app.platform.HistoryActions
import net.newpipe.app.viewmodel.settings.BooleanPreference
import org.koin.core.annotation.KoinViewModel

// Mirrors app/src/main/res/values/settings_keys.xml — keep byte-identical so
// the Compose screen and the legacy fragment read/write the same value.
private const val ENABLE_WATCH_HISTORY_KEY = "enable_watch_history"
private const val ENABLE_PLAYBACK_RESUME_KEY = "enable_playback_resume"
private const val ENABLE_PLAYBACK_STATE_LISTS_KEY = "enable_playback_state_lists"
private const val ENABLE_SEARCH_HISTORY_KEY = "enable_search_history"

@KoinViewModel
class HistorySettingsViewModel(
    settings: ObservableSettings,
    private val historyActions: HistoryActions
) : ViewModel() {

    private val watchHistoryPref = BooleanPreference(
        ENABLE_WATCH_HISTORY_KEY, true, settings, viewModelScope
    )
    private val playbackResumePref = BooleanPreference(
        ENABLE_PLAYBACK_RESUME_KEY, true, settings, viewModelScope
    )
    private val playbackStateListsPref = BooleanPreference(
        ENABLE_PLAYBACK_STATE_LISTS_KEY, true, settings, viewModelScope
    )
    private val searchHistoryPref = BooleanPreference(
        ENABLE_SEARCH_HISTORY_KEY, true, settings, viewModelScope
    )

    val watchHistory = watchHistoryPref.state
    val playbackResume = playbackResumePref.state
    val playbackStateLists = playbackStateListsPref.state
    val searchHistory = searchHistoryPref.state

    // Re-checked when the user taps "clear cookies" so the row disables itself
    // without needing a synchronous query on every recomposition.
    private val _hasRecaptchaCookies = MutableStateFlow(historyActions.hasRecaptchaCookies())
    val hasRecaptchaCookies = _hasRecaptchaCookies.asStateFlow()

    fun toggleWatchHistory(v: Boolean) = watchHistoryPref.toggle(v)
    fun togglePlaybackResume(v: Boolean) = playbackResumePref.toggle(v)
    fun togglePlaybackStateLists(v: Boolean) = playbackStateListsPref.toggle(v)
    fun toggleSearchHistory(v: Boolean) = searchHistoryPref.toggle(v)

    fun wipeMetadataCache() = historyActions.wipeMetadataCache()
    fun deleteWatchHistory() = historyActions.deleteWatchHistory()
    fun deletePlaybackStates() = historyActions.deletePlaybackStates()
    fun deleteSearchHistory() = historyActions.deleteSearchHistory()

    fun clearRecaptchaCookies() {
        historyActions.clearRecaptchaCookies()
        _hasRecaptchaCookies.value = false
    }
}