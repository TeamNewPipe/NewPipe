package org.schabi.newpipe.settings.presentation.history_cache

sealed class HistoryCacheEvent {
    data class OnUpdateBooleanPreference(val key: String, val isEnabled: Boolean) :
        HistoryCacheEvent()

    data class OnClickWipeCachedMetadata(val key: String) : HistoryCacheEvent()
    data class OnClickClearWatchHistory(val key: String) : HistoryCacheEvent()
    data class OnClickDeletePlaybackPositions(val key: String) : HistoryCacheEvent()
    data class OnClickClearSearchHistory(val key: String) : HistoryCacheEvent()
    data class OnClickReCaptchaCookies(val key: String) : HistoryCacheEvent()
}
