package org.schabi.newpipe.local.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.ui.components.items.Stream
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.ServiceHelper
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class HistoryViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val historyDao = NewPipeDatabase.getInstance(getApplication()).streamHistoryDAO()
    private val dateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)

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
        .map { pagingData ->
            pagingData.map {
                val detail = Localization.concatenateStrings(
                    Localization.shortViewCount(getApplication(), it.watchCount),
                    dateTimeFormatter.format(it.latestAccessDate),
                    ServiceHelper.getNameOfServiceById(it.streamEntity.serviceId),
                )
                Stream(it.streamEntity, detail)
            }
        }
        .flowOn(Dispatchers.IO)
        .cachedIn(viewModelScope)

    fun updateOrder(sortKey: SortKey) {
        savedStateHandle[ORDER_KEY] = sortKey
    }

    fun deleteWatchHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            historyDao.deleteAll().await()
        }
    }

    companion object {
        private const val ORDER_KEY = "order"
    }
}
