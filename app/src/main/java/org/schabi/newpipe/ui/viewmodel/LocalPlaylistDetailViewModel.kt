package org.schabi.newpipe.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.playlist.LocalPlaylistManager

class LocalPlaylistDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val playlistManager = LocalPlaylistManager(NewPipeDatabase.getInstance(application))

    private val _playlistStreams = MutableStateFlow<List<StreamInfoItem>>(emptyList())
    val playlistStreams: StateFlow<List<StreamInfoItem>> = _playlistStreams.asStateFlow()

    private val _playlistName = MutableStateFlow("")
    val playlistName: StateFlow<String> = _playlistName.asStateFlow()

    private var currentPlaylistId: Long? = null
    private var allStreamEntries: List<PlaylistStreamEntry> = emptyList()

    fun loadPlaylist(playlistId: Long) {
        currentPlaylistId = playlistId
        viewModelScope.launch {
            // Fetch playlist name on Dispatchers.IO
            val playlists = withContext(Dispatchers.IO) {
                playlistManager.getPlaylists().firstOrNull() ?: emptyList()
            }
            _playlistName.value = playlists.find { it.uid == playlistId }?.orderingName ?: "Playlist"

            playlistManager.getPlaylistStreams(playlistId)
                .collect { entries ->
                    allStreamEntries = entries
                    val items = withContext(Dispatchers.Default) {
                        entries.map { it.toStreamInfoItem() }
                    }
                    _playlistStreams.value = items
                }
        }
    }

    fun removeVideo(index: Int) {
        val playlistId = currentPlaylistId ?: return
        if (index < 0 || index >= allStreamEntries.size) return
        
        viewModelScope.launch {
            val newStreamIds = withContext(Dispatchers.Default) {
                val list = allStreamEntries.map { it.streamId }.toMutableList()
                if (index < list.size) list.removeAt(index)
                list
            }
            
            withContext(Dispatchers.IO) {
                playlistManager.updateJoin(playlistId, newStreamIds)
            }
        }
    }
}
