package org.schabi.newpipe.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.local.history.HistoryRecordManager

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val historyRecordManager = HistoryRecordManager(application)

    val history: StateFlow<List<StreamStatisticsEntry>> = historyRecordManager.getStreamStatistics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteHistoryEntry(entry: StreamStatisticsEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            historyRecordManager.deleteStreamHistoryAndState(entry.streamId)
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            historyRecordManager.deleteWholeStreamHistory()
            historyRecordManager.deleteCompleteStreamStateHistory()
        }
    }
}
