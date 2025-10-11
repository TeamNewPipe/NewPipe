package org.schabi.newpipe.settings.presentation.history_cache.events

sealed class HistoryCacheUiEvent {
    data object ShowDeletePlaybackSnackbar : HistoryCacheUiEvent()
    data object ShowDeleteSearchHistorySnackbar : HistoryCacheUiEvent()
    data object ShowClearWatchHistorySnackbar : HistoryCacheUiEvent()
    data object ShowReCaptchaCookiesSnackbar : HistoryCacheUiEvent()
    data object ShowWipeCachedMetadataSnackbar : HistoryCacheUiEvent()
}
