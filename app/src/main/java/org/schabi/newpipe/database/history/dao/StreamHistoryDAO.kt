/*
 * SPDX-FileCopyrightText: 2018-2022 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.history.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import java.time.OffsetDateTime
import org.schabi.newpipe.database.BasicDAO
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.history.model.StreamHistoryEntry
import org.schabi.newpipe.database.stream.StreamStatisticsEntry

@Dao
abstract class StreamHistoryDAO : BasicDAO<StreamHistoryEntity> {

    @Query(
        "SELECT DISTINCT service_id || ':' || url FROM streams " +
            "INNER JOIN stream_history ON uid = stream_id"
    )
    abstract fun discoveryWatchedKeys(): List<String>

    @Query(
        """
        SELECT DISTINCT service_id || ':' || url
        FROM streams
        INNER JOIN stream_history ON uid = stream_id
        WHERE profile_id = :profileId
        """
    )
    abstract fun discoveryWatchedKeysForProfile(profileId: String): List<String>

    @Query("SELECT * FROM stream_history")
    abstract override fun getAll(): Flowable<List<StreamHistoryEntity>>

    @Query("SELECT * FROM stream_history WHERE profile_id = :profileId")
    abstract fun getAllForProfile(profileId: String): Flowable<List<StreamHistoryEntity>>

    @Query("SELECT DISTINCT profile_id FROM stream_history")
    abstract fun getProfileIds(): List<String>

    @Query("SELECT * FROM stream_history ORDER BY access_date ASC")
    abstract fun getAllDirect(): List<StreamHistoryEntity>

    @Query(
        """
        SELECT * FROM stream_history
        WHERE profile_id = :profileId
        ORDER BY access_date ASC
        """
    )
    abstract fun getAllDirectForProfile(profileId: String): List<StreamHistoryEntity>

    @Query("DELETE FROM stream_history")
    abstract override fun deleteAll(): Int

    @Query("DELETE FROM stream_history WHERE profile_id = :profileId")
    abstract fun deleteAllForProfile(profileId: String): Int

    override fun listByService(serviceId: Int): Flowable<List<StreamHistoryEntity>> {
        throw UnsupportedOperationException()
    }

    @get:Query(
        """
        SELECT * FROM streams
        INNER JOIN stream_history ON uid = stream_id
        ORDER BY access_date DESC
        """
    )
    abstract val history: Flowable<MutableList<StreamHistoryEntry>>

    @Query(
        """
        SELECT * FROM streams
        INNER JOIN stream_history ON uid = stream_id
        WHERE profile_id = :profileId
        ORDER BY access_date DESC
        """
    )
    abstract fun getHistoryForProfile(profileId: String): Flowable<MutableList<StreamHistoryEntry>>

    @get:Query(
        """
        SELECT * FROM streams
        INNER JOIN stream_history ON uid = stream_id
        ORDER BY uid ASC
        """
    )
    abstract val historySortedById: Flowable<MutableList<StreamHistoryEntry>>

    @Query(
        """
        SELECT * FROM streams
        INNER JOIN stream_history ON uid = stream_id
        WHERE profile_id = :profileId
        ORDER BY uid ASC
        """
    )
    abstract fun getHistorySortedByIdForProfile(
        profileId: String
    ): Flowable<MutableList<StreamHistoryEntry>>

    @Query(
        """
        SELECT * FROM stream_history
        WHERE profile_id = :profileId AND stream_id = :streamId
        ORDER BY access_date DESC
        LIMIT 1
        """
    )
    abstract fun getLatestEntryForProfile(
        profileId: String,
        streamId: Long
    ): StreamHistoryEntity?

    @Query(
        """
        SELECT * FROM stream_history
        WHERE stream_id = :streamId
        ORDER BY access_date DESC
        LIMIT 1
        """
    )
    abstract fun getLatestEntry(streamId: Long): StreamHistoryEntity?

    @Query("DELETE FROM stream_history WHERE stream_id = :streamId")
    abstract fun deleteStreamHistory(streamId: Long): Int

    @Query(
        """
        DELETE FROM stream_history
        WHERE profile_id = :profileId AND stream_id = :streamId
        """
    )
    abstract fun deleteStreamHistoryForProfile(profileId: String, streamId: Long): Int

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
        LEFT JOIN (
            SELECT stream_id AS stream_id_alias, progress_time FROM stream_state
        )
        ON uid = stream_id_alias
        ORDER BY latestAccess DESC
        """
    )
    abstract fun getStatistics(): Flowable<MutableList<StreamStatisticsEntry>>

    /** Keep the state index usable on older SQLite versions that cannot flatten LEFT JOIN subqueries. */
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT streams.*, history.stream_id, history.latestAccess, history.watchCount,
            COALESCE(state.progress_time, 0) AS progress_time
        FROM (
            SELECT stream_id, MAX(access_date) AS latestAccess, SUM(repeat_count) AS watchCount
            FROM stream_history
            WHERE profile_id = :profileId
            GROUP BY stream_id
        ) AS history
        INNER JOIN streams ON streams.uid = history.stream_id
        LEFT JOIN stream_state AS state
            ON state.stream_id = streams.uid AND state.profile_id = :profileId
        ORDER BY latestAccess DESC, history.stream_id ASC
        """
    )
    abstract fun getStatisticsForProfile(
        profileId: String
    ): Flowable<MutableList<StreamStatisticsEntry>>

    /** Bound the aggregate before hydrating stream metadata and playback positions. */
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT streams.*, recent.stream_id, recent.latestAccess, recent.watchCount,
            COALESCE(state.progress_time, 0) AS progress_time
        FROM (
            SELECT stream_id, MAX(access_date) AS latestAccess, SUM(repeat_count) AS watchCount
            FROM stream_history
            WHERE profile_id = :profileId
            GROUP BY stream_id
            ORDER BY latestAccess DESC, stream_id ASC
            LIMIT :limit
        ) AS recent
        INNER JOIN streams ON streams.uid = recent.stream_id
        LEFT JOIN stream_state AS state
            ON state.stream_id = streams.uid AND state.profile_id = :profileId
        ORDER BY latestAccess DESC, recent.stream_id ASC
        """
    )
    abstract fun getRecentStatisticsForProfile(
        profileId: String,
        limit: Int
    ): Single<List<StreamStatisticsEntry>>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM stream_history
            WHERE stream_id = :streamId AND access_date = :date
        )
        """
    )
    abstract fun hasTakeoutEvent(streamId: Long, date: OffsetDateTime): Boolean

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM stream_history
            WHERE profile_id = :profileId
            AND stream_id = :streamId
            AND access_date = :date
        )
        """
    )
    abstract fun hasTakeoutEventForProfile(
        profileId: String,
        streamId: Long,
        date: OffsetDateTime
    ): Boolean
}
