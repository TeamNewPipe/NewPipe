package org.schabi.newpipe.settings.domain.repositories

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

interface HistoryRecordRepository {
    fun deleteCompleteStreamState(dispatcher: CoroutineDispatcher): Flow<Int>
    fun deleteWholeStreamHistory(dispatcher: CoroutineDispatcher): Flow<Int>
    fun removeOrphanedRecords(dispatcher: CoroutineDispatcher): Flow<Int>
    fun deleteCompleteSearchHistory(dispatcher: CoroutineDispatcher): Flow<Int>
}
