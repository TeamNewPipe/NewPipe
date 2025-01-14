package org.schabi.newpipe.local.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.paging.Pager
import androidx.paging.PagingConfig
import kotlinx.coroutines.flow.flatMapLatest
import org.schabi.newpipe.App
import org.schabi.newpipe.NewPipeDatabase

class HistoryViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    private val historyDao = NewPipeDatabase.getInstance(App.instance).streamHistoryDAO()

    val sortKey = savedStateHandle.getStateFlow(ORDER_KEY, SortKey.MOST_PLAYED)
    val historyItems = sortKey
        .flatMapLatest {
            Pager(PagingConfig(pageSize = 20)) {
                when (it) {
                    SortKey.LAST_PLAYED -> historyDao.getHistoryOrderedByLastWatched()
                    SortKey.MOST_PLAYED -> historyDao.getHistoryOrderedByViewCount()
                }
            }.flow
        }

    fun updateOrder(sortKey: SortKey) {
        savedStateHandle[ORDER_KEY] = sortKey
    }

    companion object {
        private const val ORDER_KEY = "order"
    }
}
