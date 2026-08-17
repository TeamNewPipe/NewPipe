package org.schabi.newpipe.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.playlist.PlaylistInfo

class PlaylistViewModel : ViewModel() {
    private val _playlistInfo = MutableStateFlow<PlaylistInfo?>(null)
    val playlistInfo: StateFlow<PlaylistInfo?> = _playlistInfo.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadPlaylist(serviceId: Int, url: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val info = withContext(Dispatchers.IO) {
                    PlaylistInfo.getInfo(NewPipe.getService(serviceId), url)
                }
                _playlistInfo.value = info
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
