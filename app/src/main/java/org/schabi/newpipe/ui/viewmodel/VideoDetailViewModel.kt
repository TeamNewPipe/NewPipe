package org.schabi.newpipe.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.local.playlist.LocalPlaylistManager
import org.schabi.newpipe.util.ExtractorHelper

class VideoDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val historyRecordManager = HistoryRecordManager(application)
    private val playlistManager = LocalPlaylistManager(NewPipeDatabase.getInstance(application))

    private val _streamInfo = MutableStateFlow<StreamInfo?>(null)
    val streamInfo: StateFlow<StreamInfo?> = _streamInfo.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Playlist / Continuous playback state
    private var currentServiceId: Int = -1
    private var currentPlaylistId: Long? = null
    private var playlistQueue: List<StreamInfoItem> = emptyList()
    
    // Allows the UI to observe and load the next URL
    private val _nextVideoUrl = MutableSharedFlow<String>()
    val nextVideoUrl = _nextVideoUrl.asSharedFlow()

    fun loadVideo(serviceId: Int, url: String, playlistId: Long? = null) {
        currentServiceId = serviceId
        currentPlaylistId = playlistId
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // If we are playing from a playlist, load the queue asynchronously
                if (playlistId != null && playlistQueue.isEmpty()) {
                    val entries = withContext(Dispatchers.IO) {
                        playlistManager.getPlaylistStreams(playlistId).firstOrNull() ?: emptyList()
                    }
                    playlistQueue = withContext(Dispatchers.Default) {
                        entries.map { it.toStreamInfoItem() }
                    }
                }

                // Fast Stream Info fetch on Dispatchers.IO
                val info: StreamInfo = ExtractorHelper.getStreamInfo(serviceId, url, false)
                _streamInfo.value = info

                // Non-blocking background history record update
                launch(Dispatchers.IO) {
                    try {
                        historyRecordManager.onViewed(info)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun playNext() {
        val playlistId = currentPlaylistId ?: return
        val currentUrl = _streamInfo.value?.url ?: return
        if (playlistQueue.isEmpty()) return

        val currentIndex = playlistQueue.indexOfFirst { it.url == currentUrl }
        if (currentIndex != -1 && currentIndex + 1 < playlistQueue.size) {
            val nextUrl = playlistQueue[currentIndex + 1].url
            viewModelScope.launch {
                _nextVideoUrl.emit(nextUrl)
            }
        }
    }

    // Saving to Playlist methods with optimized IO dispatchers
    fun saveVideoToPlaylist(playlistId: Long, info: StreamInfo, onSaved: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val streamEntity = StreamEntity(info)
                playlistManager.appendToPlaylist(playlistId, listOf(streamEntity))
            }
            withContext(Dispatchers.Main.immediate) {
                onSaved()
            }
        }
    }

    fun createAndSaveToPlaylist(playlistName: String, info: StreamInfo, onSaved: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val streamEntity = StreamEntity(info)
                playlistManager.createPlaylist(playlistName, listOf(streamEntity))
            }
            withContext(Dispatchers.Main.immediate) {
                onSaved()
            }
        }
    }
}
