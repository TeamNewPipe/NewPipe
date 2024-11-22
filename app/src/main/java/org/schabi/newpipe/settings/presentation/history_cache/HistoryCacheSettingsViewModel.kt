package org.schabi.newpipe.settings.presentation.history_cache

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ReCaptchaActivity
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.error.usecases.OpenErrorActivity
import org.schabi.newpipe.settings.domain.usecases.DeleteCompleteSearchHistory
import org.schabi.newpipe.settings.domain.usecases.DeleteCompleteStreamStateHistory
import org.schabi.newpipe.settings.domain.usecases.DeleteWatchHistory
import org.schabi.newpipe.settings.domain.usecases.get_preference.GetPreference
import org.schabi.newpipe.settings.domain.usecases.update_preference.UpdatePreference
import org.schabi.newpipe.settings.presentation.history_cache.events.HistoryCacheEvent
import org.schabi.newpipe.settings.presentation.history_cache.events.HistoryCacheEvent.OnClickClearSearchHistory
import org.schabi.newpipe.settings.presentation.history_cache.events.HistoryCacheEvent.OnClickClearWatchHistory
import org.schabi.newpipe.settings.presentation.history_cache.events.HistoryCacheEvent.OnClickDeletePlaybackPositions
import org.schabi.newpipe.settings.presentation.history_cache.events.HistoryCacheEvent.OnClickReCaptchaCookies
import org.schabi.newpipe.settings.presentation.history_cache.events.HistoryCacheEvent.OnClickWipeCachedMetadata
import org.schabi.newpipe.settings.presentation.history_cache.events.HistoryCacheEvent.OnUpdateBooleanPreference
import org.schabi.newpipe.settings.presentation.history_cache.events.HistoryCacheUiEvent
import org.schabi.newpipe.settings.presentation.history_cache.events.HistoryCacheUiEvent.ShowClearWatchHistorySnackbar
import org.schabi.newpipe.settings.presentation.history_cache.events.HistoryCacheUiEvent.ShowDeletePlaybackSnackbar
import org.schabi.newpipe.settings.presentation.history_cache.events.HistoryCacheUiEvent.ShowDeleteSearchHistorySnackbar
import org.schabi.newpipe.settings.presentation.history_cache.events.HistoryCacheUiEvent.ShowWipeCachedMetadataSnackbar
import org.schabi.newpipe.settings.presentation.history_cache.state.SwitchPreferencesUiState
import org.schabi.newpipe.util.InfoCache
import javax.inject.Inject

@HiltViewModel
class HistoryCacheSettingsViewModel @Inject constructor(
    private val updateStringPreference: UpdatePreference<String>,
    private val updateBooleanPreference: UpdatePreference<Boolean>,
    private val getStringPreference: GetPreference<String>,
    private val getBooleanPreference: GetPreference<Boolean>,
    private val deleteWatchHistory: DeleteWatchHistory,
    private val deleteCompleteStreamStateHistory: DeleteCompleteStreamStateHistory,
    private val deleteCompleteSearchHistory: DeleteCompleteSearchHistory,
    private val openErrorActivity: OpenErrorActivity,
) : ViewModel() {
    private val _switchState = MutableStateFlow(SwitchPreferencesUiState())
    val switchState: StateFlow<SwitchPreferencesUiState> = _switchState.asStateFlow()

    private val _captchaCookies = MutableStateFlow(false)
    val captchaCookies: StateFlow<Boolean> = _captchaCookies.asStateFlow()

    private val _eventFlow = MutableSharedFlow<HistoryCacheUiEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    fun onInit() {

        viewModelScope.launch {
            val flow = getStringPreference(R.string.recaptcha_cookies_key, "")
            flow.collect { preference ->
                _captchaCookies.update {
                    preference.isNotEmpty()
                }
            }
        }

        viewModelScope.launch {
            getBooleanPreference(R.string.enable_watch_history_key, true).collect { preference ->
                _switchState.update { oldState ->
                    oldState.copy(
                        watchHistoryEnabled = preference
                    )
                }
            }
        }

        viewModelScope.launch {
            getBooleanPreference(R.string.enable_playback_resume_key, true).collect { preference ->
                _switchState.update { oldState ->
                    oldState.copy(
                        resumePlaybackEnabled = preference
                    )
                }
            }
        }

        viewModelScope.launch {
            getBooleanPreference(
                R.string.enable_playback_state_lists_key,
                true
            ).collect { preference ->
                _switchState.update { oldState ->
                    oldState.copy(
                        positionsInListsEnabled = preference
                    )
                }
            }
        }
        viewModelScope.launch {
            getBooleanPreference(R.string.enable_search_history_key, true).collect { preference ->
                _switchState.update { oldState ->
                    oldState.copy(
                        searchHistoryEnabled = preference
                    )
                }
            }
        }
    }

    fun onEvent(event: HistoryCacheEvent) {
        when (event) {
            is OnUpdateBooleanPreference -> {
                viewModelScope.launch {
                    updateBooleanPreference(event.key, event.isEnabled)
                }
            }

            is OnClickWipeCachedMetadata -> {
                InfoCache.getInstance().clearCache()
                viewModelScope.launch {
                    _eventFlow.emit(ShowWipeCachedMetadataSnackbar)
                }
            }

            is OnClickClearWatchHistory -> {
                viewModelScope.launch {
                    deleteWatchHistory(
                        onDeletePlaybackStates = {
                            viewModelScope.launch {
                                _eventFlow.emit(ShowDeletePlaybackSnackbar)
                            }
                        },
                        onDeleteWholeStreamHistory = {
                            viewModelScope.launch {
                                _eventFlow.emit(ShowClearWatchHistorySnackbar)
                            }
                        },
                        onRemoveOrphanedRecords = {
                            // TODO: ask why original in android fragments did nothing
                        }
                    )
                }
            }

            is OnClickDeletePlaybackPositions -> {
                viewModelScope.launch {
                    deleteCompleteStreamStateHistory(
                        Dispatchers.IO,
                        onError = { error ->
                            openErrorActivity(
                                ErrorInfo(
                                    error,
                                    UserAction.DELETE_FROM_HISTORY,
                                    "Delete playback states"
                                )
                            )
                        },
                        onSuccess = {
                            viewModelScope.launch {
                                _eventFlow.emit(ShowDeletePlaybackSnackbar)
                            }
                        }
                    )
                }
            }

            is OnClickClearSearchHistory -> {
                viewModelScope.launch {
                    deleteCompleteSearchHistory(
                        dispatcher = Dispatchers.IO,
                        onError = { error ->
                            openErrorActivity(
                                ErrorInfo(
                                    error,
                                    UserAction.DELETE_FROM_HISTORY,
                                    "Delete search history"
                                )
                            )
                        },
                        onSuccess = {
                            viewModelScope.launch {
                                _eventFlow.emit(ShowDeleteSearchHistorySnackbar)
                            }
                        }
                    )
                }
            }

            is OnClickReCaptchaCookies -> {
                viewModelScope.launch {
                    updateStringPreference(event.key, "")
                    DownloaderImpl.getInstance()
                        .setCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY, "")
                    _eventFlow.emit(ShowWipeCachedMetadataSnackbar)
                }
            }
        }
    }
}
