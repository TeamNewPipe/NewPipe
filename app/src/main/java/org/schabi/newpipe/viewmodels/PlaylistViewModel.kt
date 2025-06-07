package org.schabi.newpipe.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.withContext
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.paging.PlaylistItemsSource
import org.schabi.newpipe.ui.components.playlist.PlaylistScreenInfo
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.KEY_SERVICE_ID
import org.schabi.newpipe.util.KEY_TITLE
import org.schabi.newpipe.util.KEY_URL
import org.schabi.newpipe.viewmodels.util.Resource

class PlaylistViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    val serviceId = savedStateHandle.get<Int>(KEY_SERVICE_ID)!!
    val url = savedStateHandle.get<String>(KEY_URL)!!
    val playlistTitle = savedStateHandle.get<String>(KEY_TITLE)!!

    private val _uiState = MutableStateFlow<Resource<PlaylistScreenInfo>>(Resource.Loading)
    val uiState = _uiState.asStateFlow()

    private val remotePlaylistDao = NewPipeDatabase.getInstance(application).playlistRemoteDAO()
    val bookmarkFlow = remotePlaylistDao.getPlaylist(serviceId, url)

    @OptIn(ExperimentalCoroutinesApi::class)
    val streamItems = _uiState
        .filterIsInstance<Resource.Success<PlaylistScreenInfo>>()
        .flatMapLatest {
            Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
                PlaylistItemsSource(it.data)
            }.flow
        }
        .flowOn(Dispatchers.IO)
        .cachedIn(viewModelScope)

    init {
        loadPlaylist()
    }

    fun loadPlaylist() {
        _uiState.value = Resource.Loading
        viewModelScope.launch {
            _uiState.value =
                try {
                    val extractorInfo = withContext(Dispatchers.IO) {
                        ExtractorHelper.getPlaylistInfo(serviceId, url, true).await()
                    }
                    Resource.Success(PlaylistScreenInfo(extractorInfo))
                } catch (e: Exception) {
                    Resource.Error(e)
                }
        }
    }

    fun toggleBookmark() {
        viewModelScope.launch {
            val bookmark = bookmarkFlow.firstOrNull()
            if (bookmark != null) {
                remotePlaylistDao.deletePlaylist(bookmark.uid)
            } else {
                val info = _uiState
                    .filterIsInstance<Resource.Success<PlaylistScreenInfo>>()
                    .firstOrNull()
                    ?.data
                if (info != null) {
                    val entity = PlaylistRemoteEntity(info)
                    remotePlaylistDao.upsert(entity)
                }
            }
        }
    }
}
