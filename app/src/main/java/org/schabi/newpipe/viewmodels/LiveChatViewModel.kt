package org.schabi.newpipe.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.util.KEY_SERVICE_ID
import org.schabi.newpipe.util.KEY_URL

class LiveChatViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val serviceId: Int = savedStateHandle[KEY_SERVICE_ID] ?: 0
    private val url: String = savedStateHandle[KEY_URL] ?: ""
    private val liveChatContinuation: String =
        savedStateHandle.get<String>(KEY_LIVE_CHAT_CONTINUATION) ?: ""

    private val _liveChatItems = MutableStateFlow<List<CommentsInfoItem>>(emptyList())
    val liveChatItems: StateFlow<List<CommentsInfoItem>> = _liveChatItems

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val service = NewPipe.getService(serviceId)
                val extractor = service.getCommentsExtractor(url)
                extractor.setLiveChatContinuation(liveChatContinuation)
                val info = CommentsInfo.getInfo(extractor)
                _liveChatItems.value = info.relatedItems
                var nextPage = info.nextPage
                while (isActive) {
                    delay(3000)
                    if (nextPage == null) continue
                    val result = CommentsInfo.getMoreItems(service, url, nextPage)
                    if (result.items.isNotEmpty()) {
                        _liveChatItems.value = result.items + _liveChatItems.value
                    }
                    nextPage = result.nextPage
                }
            } catch (e: Exception) {
                // Ignore initialization errors
            }
        }
    }

    companion object {
        const val KEY_LIVE_CHAT_CONTINUATION = "live_chat_continuation"
    }
}
