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
import org.schabi.newpipe.download.CompletedDownload
import org.schabi.newpipe.download.DownloadStatus
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
            DownloadStatusRepository.observe(appContext, serviceId, normalizedUrl).collectLatest { status ->
                _uiState.update { it.copy(chipState = status.toChipState()) }
            }
        }
    }

    fun onChipClicked(context: Context) {
        val url = currentUrl ?: return
        val serviceId = currentServiceId
        viewModelScope.launch {
            val result = runCatching {
                DownloadStatusRepository.refresh(context.applicationContext, serviceId, url)
            }
            result.getOrNull()?.let { status ->
                _uiState.update {
                    val chipState = status.toChipState()
                    it.copy(
                        chipState = chipState,
                        isSheetVisible = chipState is DownloadChipState.Downloaded
                    )
                }
            }
            if (result.isFailure) {
                _uiState.update { it.copy(isSheetVisible = false) }
            }
        }
    }

    fun dismissSheet() {
        _uiState.update { it.copy(isSheetVisible = false) }
    }

    suspend fun deleteFile(context: Context): Boolean {
        val url = currentUrl ?: return false
        val serviceId = currentServiceId
        val success = runCatching {
            DownloadStatusRepository.deleteFile(context.applicationContext, serviceId, url)
        }.getOrDefault(false)
        if (success) {
            _uiState.update { it.copy(isSheetVisible = false) }
        }
        return success
    }

    suspend fun removeLink(context: Context): Boolean {
        val url = currentUrl ?: return false
        val serviceId = currentServiceId
        val success = runCatching {
            DownloadStatusRepository.removeLink(context.applicationContext, serviceId, url)
        }.getOrDefault(false)
        if (success) {
            _uiState.update { it.copy(isSheetVisible = false) }
        }
        return success
    }

    private fun DownloadStatus.toChipState(): DownloadChipState = when (this) {
        DownloadStatus.None -> DownloadChipState.Hidden
        is DownloadStatus.InProgress -> DownloadChipState.Downloading
        is DownloadStatus.Completed -> DownloadChipState.Downloaded(info)
    }
}

data class DownloadUiState(
    val chipState: DownloadChipState = DownloadChipState.Hidden,
    val isSheetVisible: Boolean = false
)

sealed interface DownloadChipState {
    data object Hidden : DownloadChipState
    data object Downloading : DownloadChipState
    data class Downloaded(val info: CompletedDownload) : DownloadChipState
}
