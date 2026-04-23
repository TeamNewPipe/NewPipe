package org.schabi.newpipe.viewmodels

import android.util.Log
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
import kotlinx.coroutines.flow.flowOf
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
    companion object {
        private const val TAG = "CommentsViewModel"
    }

    val uiState = savedStateHandle.getStateFlow(KEY_URL, "")
        .map {
            try {
                val info = CommentsInfo.getInfo(it)
                Log.i(
                    TAG,
                    "Loaded CommentsInfo: disabled=${info.isCommentsDisabled}, " +
                        "liveChat=${info.isLiveChat}, items=${info.relatedItems.size}, " +
                        "nextPage=${info.nextPage != null}"
                )
                Resource.Success(CommentInfo(info))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load comments", e)
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
            Log.i(TAG, "flatMapLatest: isLiveChat=${info.isLiveChat}, items=${info.comments.size}")
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
        Log.i(TAG, "startLiveChatPolling() items=${info.comments.size}, nextPage=${nextPage != null}")

        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(3000)
                if (nextPage == null) {
                    Log.d(TAG, "liveChatPolling: nextPage is null, skipping")
                    continue
                }
                try {
                    Log.d(TAG, "liveChatPolling: fetching more items...")
                    val result = CommentsInfo.getMoreItems(
                        NewPipe.getService(info.serviceId),
                        info.url,
                        nextPage
                    )
                    Log.i(TAG, "liveChatPolling: fetched ${result.items.size} items, nextPage=${result.nextPage != null}")
                    if (result.items.isNotEmpty()) {
                        _liveChatItems.value = _liveChatItems.value + result.items
                    }
                    nextPage = result.nextPage
                } catch (e: Exception) {
                    Log.e(TAG, "liveChatPolling: failed to fetch more items", e)
                }
            }
        }
    }
}
