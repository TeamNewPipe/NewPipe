package org.schabi.newpipe.settings.presentation.history_cache.events

sealed class HistoryCacheEvent {
    data class OnUpdateBooleanPreference(val key: Int, val isEnabled: Boolean) : HistoryCacheEvent()
    data class OnClickWipeCachedMetadata(val key: Int) : HistoryCacheEvent()
    data class OnClickClearWatchHistory(val key: Int) : HistoryCacheEvent()
    data class OnClickDeletePlaybackPositions(val key: Int) : HistoryCacheEvent()
    data class OnClickClearSearchHistory(val key: Int) : HistoryCacheEvent()
    data class OnClickReCaptchaCookies(val key: Int) : HistoryCacheEvent()
}
