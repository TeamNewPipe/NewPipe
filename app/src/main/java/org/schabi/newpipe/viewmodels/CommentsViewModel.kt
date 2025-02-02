package org.schabi.newpipe.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.rx3.await
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.paging.CommentsSource
import org.schabi.newpipe.ui.components.video.comment.CommentInfo
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.KEY_SERVICE_ID
import org.schabi.newpipe.util.KEY_URL
import org.schabi.newpipe.util.NO_SERVICE_ID
import org.schabi.newpipe.viewmodels.util.Resource

class CommentsViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val url = savedStateHandle.getStateFlow(KEY_URL, "")
    private val serviceId = savedStateHandle[KEY_SERVICE_ID] ?: NO_SERVICE_ID

    val streamState = url
        .map {
            try {
                Resource.Success(ExtractorHelper.getStreamInfo(serviceId, it, true).await())
            } catch (e: Exception) {
                Resource.Error(e)
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), Resource.Loading)

    val commentState = url
        .map {
            try {
                Resource.Success(CommentInfo(CommentsInfo.getInfo(it)))
            } catch (e: Exception) {
                Resource.Error(e)
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), Resource.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    val comments = commentState
        .filterIsInstance<Resource.Success<CommentInfo>>()
        .flatMapLatest {
            Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
                CommentsSource(it.data)
            }.flow
        }
        .cachedIn(viewModelScope)
}
