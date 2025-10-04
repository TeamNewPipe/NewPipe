package org.schabi.newpipe.fragments.detail

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schabi.newpipe.download.DownloadEntry
import org.schabi.newpipe.download.DownloadHandle
import org.schabi.newpipe.download.DownloadStage
import org.schabi.newpipe.download.DownloadStatusRepository

class VideoDownloadStatusViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState

    private var observeJob: Job? = null
    private var currentServiceId: Int = -1
    private var currentUrl: String? = null

    fun setStream(context: Context, serviceId: Int, url: String?) {
        val normalizedUrl = url ?: ""
        if (serviceId < 0 || normalizedUrl.isBlank()) {
            observeJob?.cancel()
            observeJob = null
            currentServiceId = -1
            currentUrl = null
            _uiState.value = DownloadUiState()
            return
        }

        if (currentServiceId == serviceId && currentUrl == normalizedUrl) {
            return
        }

        currentServiceId = serviceId
        currentUrl = normalizedUrl

        val appContext = context.applicationContext

        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            DownloadStatusRepository.observe(appContext, serviceId, normalizedUrl)
                .collectLatest { entries ->
                    _uiState.update { current ->
                        val selectedHandle = current.selected?.handle
                        val newSelected = selectedHandle?.let { handle ->
                            entries.firstOrNull { it.handle == handle }
                        }
                        current.copy(entries = entries, selected = newSelected)
                    }
                }
        }
    }

    fun onChipSelected(entry: DownloadEntry) {
        _uiState.update { it.copy(selected = entry) }
    }

    fun dismissSheet() {
        _uiState.update { it.copy(selected = null) }
    }

    suspend fun deleteFile(context: Context, handle: DownloadHandle): Boolean {
        val success = runCatching {
            DownloadStatusRepository.deleteFile(context.applicationContext, handle)
        }.getOrDefault(false)
        if (success) {
            _uiState.update { state ->
                state.copy(
                    entries = state.entries.filterNot { it.handle == handle },
                    selected = null
                )
            }
        }
        return success
    }

    suspend fun removeLink(context: Context, handle: DownloadHandle): Boolean {
        val success = runCatching {
            DownloadStatusRepository.removeLink(context.applicationContext, handle)
        }.getOrDefault(false)
        if (success) {
            _uiState.update { state ->
                state.copy(
                    entries = state.entries.filterNot { it.handle == handle },
                    selected = null
                )
            }
        }
        return success
    }
}

data class DownloadUiState(
    val entries: List<DownloadEntry> = emptyList(),
    val selected: DownloadEntry? = null
) {
    val isSheetVisible: Boolean get() = selected != null
}

val DownloadEntry.isPending: Boolean
    get() = when (stage) {
        DownloadStage.Pending, DownloadStage.Running -> true
        DownloadStage.Finished -> false
    }

val DownloadEntry.isRunning: Boolean
    get() = stage == DownloadStage.Running
