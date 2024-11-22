package org.schabi.newpipe.settings.domain.repositories

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import org.schabi.newpipe.database.history.model.SearchHistoryEntry
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity

class HistoryRecordRepositoryFake : HistoryRecordRepository {
    private val _searchHistory: MutableStateFlow<List<SearchHistoryEntry>> = MutableStateFlow(
        emptyList()
    )
    val searchHistory = _searchHistory.asStateFlow()
    private val _streamHistory = MutableStateFlow<List<StreamHistoryEntity>>(emptyList())
    val streamHistory = _streamHistory.asStateFlow()
    private val _streams = MutableStateFlow<List<StreamEntity>>(emptyList())
    val streams = _streams.asStateFlow()
    private val _streamStates = MutableStateFlow<List<StreamStateEntity>>(emptyList())
    val streamStates = _streamStates.asStateFlow()

    override fun deleteCompleteStreamState(dispatcher: CoroutineDispatcher): Flow<Int> = flow {
        val count = streamStates.value.size
        _streamStates.update {
            emptyList()
        }
        emit(count)
    }.flowOn(dispatcher)

    override fun deleteWholeStreamHistory(dispatcher: CoroutineDispatcher): Flow<Int> = flow {
        val count = streamHistory.value.size
        _streamHistory.update {
            emptyList()
        }
        emit(count)
    }.flowOn(dispatcher)

    override fun removeOrphanedRecords(dispatcher: CoroutineDispatcher): Flow<Int> = flow {
        val orphanedStreams = streams.value.filter { stream ->
            !streamHistory.value.any { it.streamUid == stream.uid }
        }

        val deletedCount = orphanedStreams.size

        _streams.update { oldStreams ->
            oldStreams.filter { it !in orphanedStreams }
        }

        emit(deletedCount)
    }.flowOn(dispatcher)

    override fun deleteCompleteSearchHistory(dispatcher: CoroutineDispatcher): Flow<Int> = flow {
        val count = searchHistory.value.size
        _searchHistory.update {
            emptyList()
        }
        emit(count)
    }.flowOn(dispatcher)
}
