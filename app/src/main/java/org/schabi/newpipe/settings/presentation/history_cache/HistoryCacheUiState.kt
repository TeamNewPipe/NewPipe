package org.schabi.newpipe.settings.presentation.history_cache

data class HistoryCacheUiState(
    val switchPreferencesUiState: SwitchPreferencesUiState = SwitchPreferencesUiState()
)

data class SwitchPreferencesUiState(
    val watchHistoryEnabled: Boolean = false,
    val resumePlaybackEnabled: Boolean = false,
    val positionsInListsEnabled: Boolean = false,
    val searchHistoryEnabled: Boolean = false,
)
