package org.schabi.newpipe.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import org.schabi.newpipe.paging.CommentsSource
import org.schabi.newpipe.util.KEY_SERVICE_ID
import org.schabi.newpipe.util.KEY_URL
import org.schabi.newpipe.util.NO_SERVICE_ID

class CommentsViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val serviceId = savedStateHandle[KEY_SERVICE_ID] ?: NO_SERVICE_ID
    private val url = savedStateHandle.get<String>(KEY_URL)

    val comments = Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
        CommentsSource(serviceId, url, null)
    }.flow
        .cachedIn(viewModelScope)
}
