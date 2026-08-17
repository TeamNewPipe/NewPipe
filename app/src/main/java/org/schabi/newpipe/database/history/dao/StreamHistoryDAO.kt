/*
 * SPDX-FileCopyrightText: 2018-2022 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.history.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import kotlinx.coroutines.flow.Flow
import org.schabi.newpipe.database.BasicDAO
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.history.model.StreamHistoryEntry
import org.schabi.newpipe.database.stream.StreamStatisticsEntry

@Dao
abstract class StreamHistoryDAO : BasicDAO<StreamHistoryEntity> {

    @Query("SELECT * FROM stream_history")
    abstract override fun getAll(): Flow<List<StreamHistoryEntity>>

    @Query("DELETE FROM stream_history")
    abstract override suspend fun deleteAll(): Int

    override fun listByService(serviceId: Int): Flow<List<StreamHistoryEntity>> {
        throw UnsupportedOperationException()
    }

    @Query("SELECT * FROM streams INNER JOIN stream_history ON uid = stream_id ORDER BY access_date DESC")
    abstract fun getHistory(): Flow<List<StreamHistoryEntry>>

    @Query("SELECT * FROM streams INNER JOIN stream_history ON uid = stream_id ORDER BY uid ASC")
    abstract fun getHistorySortedById(): Flow<List<StreamHistoryEntry>>

    @Query("SELECT * FROM stream_history WHERE stream_id = :streamId ORDER BY access_date DESC LIMIT 1")
    abstract suspend fun getLatestEntry(streamId: Long): StreamHistoryEntity?

    @Query("DELETE FROM stream_history WHERE stream_id = :streamId")
    abstract suspend fun deleteStreamHistory(streamId: Long): Int

    // Select the latest entry and watch count for each stream id on history table
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT * FROM streams

        INNER JOIN (
            SELECT stream_id, MAX(access_date) AS latestAccess, SUM(repeat_count) AS watchCount
            FROM stream_history
            GROUP BY stream_id
        )
        ON uid = stream_id

        LEFT JOIN (SELECT stream_id AS stream_id_alias, progress_time FROM stream_state )
        ON uid = stream_id_alias
        """
    )
    abstract fun getStatistics(): Flow<List<StreamStatisticsEntry>>
}
