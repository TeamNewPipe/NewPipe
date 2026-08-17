package org.schabi.newpipe.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry
import org.schabi.newpipe.local.playlist.LocalPlaylistManager

class LocalPlaylistsViewModel(application: Application) : AndroidViewModel(application) {
    private val playlistManager = LocalPlaylistManager(NewPipeDatabase.getInstance(application))

    private val _playlists = MutableStateFlow<List<PlaylistMetadataEntry>>(emptyList())
    val playlists: StateFlow<List<PlaylistMetadataEntry>> = _playlists.asStateFlow()

    init {
        loadPlaylists()
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            playlistManager.getPlaylists()
                .collect { items ->
                    _playlists.value = items
                }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistManager.createPlaylist(name, emptyList())
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            // updatePlaylists takes items to update and items to delete
            playlistManager.updatePlaylists(emptyList(), listOf(playlistId))
        }
    }
}
