package org.schabi.newpipe.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.paging.CommentsSource
import org.schabi.newpipe.ui.components.video.comment.CommentInfo
import org.schabi.newpipe.util.KEY_URL
import org.schabi.newpipe.viewmodels.util.Resource

class CommentsViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    val uiState = savedStateHandle.getStateFlow(KEY_URL, "")
        .map {
            try {
                val info = CommentsInfo.getInfo(it)
                Resource.Success(CommentInfo(info))
            } catch (e: Exception) {
                Resource.Error(e)
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), Resource.Loading)

    // Separate flow for live chat items (not using Paging 3)
    private val _liveChatItems = MutableStateFlow<List<CommentsInfoItem>>(emptyList())
    val liveChatItems: StateFlow<List<CommentsInfoItem>> = _liveChatItems

    @OptIn(ExperimentalCoroutinesApi::class)
    val comments: Flow<PagingData<CommentsInfoItem>> = uiState
        .filterIsInstance<Resource.Success<CommentInfo>>()
        .flatMapLatest {
            val info = it.data
            if (info.isLiveChat) {
                _liveChatItems.value = info.comments
                startLiveChatPolling(info)
                // Return empty PagingData for live chat (items come from liveChatItems flow)
                kotlinx.coroutines.flow.flowOf(androidx.paging.PagingData.empty())
            } else {
                Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
                    CommentsSource(info)
                }.flow
            }
        }
        .cachedIn(viewModelScope)

    private fun startLiveChatPolling(info: CommentInfo) {
        var nextPage = info.nextPage

        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(3000)
                if (nextPage == null) {
                    continue
                }
                try {
                    val result = CommentsInfo.getMoreItems(
                        NewPipe.getService(info.serviceId),
                        info.url,
                        nextPage
                    )
                    if (result.items.isNotEmpty()) {
                        _liveChatItems.value = result.items + _liveChatItems.value
                    }
                    nextPage = result.nextPage
                } catch (e: Exception) {
                    // Silently ignore polling errors
                }
            }
        }
    }
}
