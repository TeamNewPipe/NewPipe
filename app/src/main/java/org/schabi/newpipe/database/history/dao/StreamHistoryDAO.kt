package org.schabi.newpipe.database.history.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.history.model.StreamHistoryEntry
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.local.history.SortKey

@Dao
interface StreamHistoryDAO {
    @Insert
    fun insert(entity: StreamHistoryEntity): Long

    @Delete
    fun delete(entity: StreamHistoryEntity)

    @Query("DELETE FROM stream_history")
    fun deleteAll(): Completable

    @Query("SELECT * FROM streams INNER JOIN stream_history ON uid = stream_id ORDER BY uid ASC")
    fun getHistorySortedById(): Flowable<List<StreamHistoryEntry>>

    @Query("SELECT * FROM stream_history WHERE stream_id = :streamId ORDER BY access_date DESC LIMIT 1")
    fun getLatestEntry(streamId: Long): StreamHistoryEntity?

    @Query("DELETE FROM stream_history WHERE stream_id = :streamId")
    fun deleteStreamHistory(streamId: Long): Completable

    @Query("SELECT * FROM streams INNER JOIN stream_history ON uid = stream_id ORDER BY access_date DESC")
    fun getHistory(): Flowable<List<StreamHistoryEntry>>

    @RawQuery(
        observedEntities = [StreamStatisticsEntry::class, StreamEntity::class, StreamHistoryEntity::class]
    )
    fun getOrderedHistoryByRaw(query: SupportSQLiteQuery): PagingSource<Int, StreamStatisticsEntry>

    fun getOrderedHistory(key: SortKey): PagingSource<Int, StreamStatisticsEntry> {
        val orderBy = when (key) {
            SortKey.LAST_PLAYED -> "latestAccess"
            SortKey.MOST_PLAYED -> "watchCount"
        }
        return getOrderedHistoryByRaw(
            SimpleSQLiteQuery(
                """SELECT * FROM streams INNER JOIN
                (SELECT stream_id, MAX(access_date) AS latestAccess,
                SUM(repeat_count) AS watchCount FROM stream_history GROUP BY stream_id)
                ON uid = stream_id LEFT JOIN
                (SELECT stream_id AS stream_id_alias, progress_time FROM stream_state)
                ON uid = stream_id_alias
                ORDER BY $orderBy DESC
                """.trimIndent()
            )
        )
    }
}
