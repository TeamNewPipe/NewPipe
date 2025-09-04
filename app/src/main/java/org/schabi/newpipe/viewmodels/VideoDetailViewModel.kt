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
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.paging.CommentsSource
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.ui.components.video.comment.CommentInfo
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.KEY_SERVICE_ID
import org.schabi.newpipe.util.KEY_TITLE
import org.schabi.newpipe.util.KEY_URL
import org.schabi.newpipe.util.NO_SERVICE_ID
import org.schabi.newpipe.viewmodels.util.Resource

class VideoDetailViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    val url get() = savedStateHandle[KEY_URL] ?: ""
    val title get() = savedStateHandle[KEY_TITLE] ?: ""
    val serviceId get() = savedStateHandle[KEY_SERVICE_ID] ?: NO_SERVICE_ID

    var addToBackStack = false

    var playQueue: PlayQueue?
        get() = savedStateHandle[KEY_PLAY_QUEUE]
        set(value) = savedStateHandle.set(KEY_PLAY_QUEUE, value)

    private val _streamState = MutableStateFlow<Resource<StreamInfo>>(Resource.Loading)
    val streamState = _streamState.asStateFlow()

    private val _commentState = MutableStateFlow<Resource<CommentInfo>>(Resource.Loading)
    val commentState = _commentState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val comments = _commentState
        .filterIsInstance<Resource.Success<CommentInfo>>()
        .flatMapLatest {
            Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
                CommentsSource(it.data)
            }.flow
        }
        .cachedIn(viewModelScope)

    init {
        startLoading()
    }

    fun startLoading(forceLoad: Boolean = true) {
        _streamState.value = Resource.Loading
        _commentState.value = Resource.Loading

        viewModelScope.launch(Dispatchers.IO) {
            val url = url
            val streamInfoTask = async {
                if (url.isNotEmpty()) {
                    Resource.Success(ExtractorHelper.getStreamInfo(serviceId, url, forceLoad).await())
                } else {
                    Resource.Loading
                }
            }
            val commentInfoTask = async {
                if (url.isNotEmpty()) {
                    Resource.Success(CommentInfo(CommentsInfo.getInfo(url)))
                } else {
                    Resource.Loading
                }
            }

            _streamState.value = streamInfoTask.await()
            _commentState.value = commentInfoTask.await()
        }
    }

    fun updateStreamState(info: StreamInfo) {
        _streamState.value = Resource.Success(info)
    }

    fun updateData(serviceId: Int, url: String?, title: String, playQueue: PlayQueue?) {
        savedStateHandle[KEY_URL] = url
        savedStateHandle[KEY_SERVICE_ID] = serviceId
        savedStateHandle[KEY_TITLE] = title
        savedStateHandle[KEY_PLAY_QUEUE] = playQueue
    }

    companion object {
        const val KEY_PLAY_QUEUE = "playQueue"
    }
}
