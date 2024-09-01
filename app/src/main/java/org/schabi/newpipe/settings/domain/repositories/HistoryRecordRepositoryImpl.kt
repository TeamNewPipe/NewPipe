package org.schabi.newpipe.settings.domain.repositories

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.schabi.newpipe.database.history.dao.SearchHistoryDAO
import org.schabi.newpipe.database.history.dao.StreamHistoryDAO
import org.schabi.newpipe.database.stream.dao.StreamDAO
import org.schabi.newpipe.database.stream.dao.StreamStateDAO

class HistoryRecordRepositoryImpl(
    private val streamStateDao: StreamStateDAO,
    private val streamHistoryDAO: StreamHistoryDAO,
    private val streamDAO: StreamDAO,
    private val searchHistoryDAO: SearchHistoryDAO,
) : HistoryRecordRepository {
    override fun deleteCompleteStreamState(dispatcher: CoroutineDispatcher): Flow<Int> =
        flow {
            val deletedCount = streamStateDao.deleteAll()
            emit(deletedCount)
        }.flowOn(dispatcher)

    override fun deleteWholeStreamHistory(dispatcher: CoroutineDispatcher): Flow<Int> =
        flow {
            val deletedCount = streamHistoryDAO.deleteAll()
            emit(deletedCount)
        }.flowOn(dispatcher)

    override fun removeOrphanedRecords(dispatcher: CoroutineDispatcher): Flow<Int> = flow {
        val deletedCount = streamDAO.deleteOrphans()
        emit(deletedCount)
    }.flowOn(dispatcher)

    override fun deleteCompleteSearchHistory(dispatcher: CoroutineDispatcher): Flow<Int> = flow {
        val deletedCount = searchHistoryDAO.deleteAll()
        emit(deletedCount)
    }.flowOn(dispatcher)
}
