package org.schabi.newpipe.viewmodels

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.schabi.newpipe.MainActivity.DEBUG
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.ktx.isNetworkRelated
import org.schabi.newpipe.util.KEY_SERVICE_ID
import org.schabi.newpipe.util.KEY_URL

class LiveChatViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val serviceId: Int = savedStateHandle[KEY_SERVICE_ID] ?: 0
    private val url: String = savedStateHandle[KEY_URL] ?: ""
    private val liveChatContinuation: String =
        savedStateHandle.get<String>(KEY_LIVE_CHAT_CONTINUATION) ?: ""

    private val _isLoading = MutableStateFlow<Boolean>(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _liveChatItems = MutableStateFlow<List<CommentsInfoItem>>(emptyList())
    val liveChatItems: StateFlow<List<CommentsInfoItem>> = _liveChatItems

    private val _error = MutableStateFlow<Throwable?>(null)
    val error: StateFlow<Throwable?> = _error

    private val _hasNetworkIssues = MutableStateFlow<Boolean>(false)
    val hasNetworkIssues: StateFlow<Boolean> = _hasNetworkIssues

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val service = NewPipe.getService(serviceId)
                val extractor = service.getCommentsExtractor(url)
                extractor.setLiveChatContinuation(liveChatContinuation)
                val info = CommentsInfo.getInfo(extractor)
                _liveChatItems.value = info.relatedItems.take(MAX_LIVE_CHAT_ITEMS)
                var nextPage = info.nextPage
                _isLoading.value = false

                while (isActive) {
                    delay(3.seconds)
                    if (nextPage == null) {
                        break
                    }
                    try {
                        val result = CommentsInfo.getMoreItems(service, url, nextPage)
                        if (result.items.isNotEmpty()) {
                            _liveChatItems.value = buildList(MAX_LIVE_CHAT_ITEMS) {
                                addAll(result.items.take(MAX_LIVE_CHAT_ITEMS))
                                if (size < MAX_LIVE_CHAT_ITEMS) {
                                    addAll(
                                        _liveChatItems.value.take(MAX_LIVE_CHAT_ITEMS - size)
                                    )
                                }
                            }
                        }
                        nextPage = result.nextPage
                        if (_hasNetworkIssues.value) {
                            _hasNetworkIssues.value = false
                        }
                    } catch (e: Exception) {
                        if (DEBUG) {
                            Log.e(TAG, "Error loading live chat comments", e)
                        }
                        if (e.isNetworkRelated) {
                            // retry on network related exceptions
                            // and do not stop the comment polling
                            _hasNetworkIssues.value = true
                        } else {
                            throw e
                        }
                    }
                }
            } catch (e: Exception) {
                if (DEBUG) {
                    Log.e(TAG, "Error loading live chat comments", e)
                }
                _error.value = e
            }
        }
    }

    companion object {
        private const val MAX_LIVE_CHAT_ITEMS = 200
        const val KEY_LIVE_CHAT_CONTINUATION = "live_chat_continuation"

        private const val TAG = "LiveChatViewModel"
    }
}
