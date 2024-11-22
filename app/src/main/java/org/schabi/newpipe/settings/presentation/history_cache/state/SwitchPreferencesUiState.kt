package org.schabi.newpipe.settings.presentation.history_cache.state

import androidx.compose.runtime.Stable
@Stable
data class SwitchPreferencesUiState(
    val watchHistoryEnabled: Boolean = false,
    val resumePlaybackEnabled: Boolean = false,
    val positionsInListsEnabled: Boolean = false,
    val searchHistoryEnabled: Boolean = false,
)
