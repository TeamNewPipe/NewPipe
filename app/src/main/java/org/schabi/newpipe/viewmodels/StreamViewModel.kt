package org.schabi.newpipe.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.rx3.awaitSingleOrNull
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.history.HistoryRecordManager

class StreamViewModel(application: Application) : AndroidViewModel(application) {
    private val historyRecordManager = HistoryRecordManager(application)

    suspend fun getStreamState(infoItem: InfoItem): StreamStateEntity? {
        return historyRecordManager.loadStreamState(infoItem).awaitSingleOrNull()
    }

    fun markAsWatched(stream: StreamInfoItem) {
        viewModelScope.launch {
            historyRecordManager.markAsWatched(stream).await()
        }
    }
}
