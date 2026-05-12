/*
 * SPDX-FileCopyrightText: 2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.recommendation.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.rxjava3.core.Flowable
import org.schabi.newpipe.database.BasicDAO
import org.schabi.newpipe.database.recommendation.model.RecommendationSignalEntity

@Dao
interface RecommendationSignalDAO : BasicDAO<RecommendationSignalEntity> {

    @Query("SELECT * FROM recommendation_signal")
    override fun getAll(): Flowable<List<RecommendationSignalEntity>>

    @Query("DELETE FROM recommendation_signal")
    override fun deleteAll(): Int

    override fun listByService(serviceId: Int): Flowable<List<RecommendationSignalEntity>> {
        throw UnsupportedOperationException()
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: RecommendationSignalEntity): Long

    @Query(
        "SELECT * FROM recommendation_signal " +
            "WHERE signal_type = :signalType ORDER BY score DESC, updated_at DESC LIMIT :limit"
    )
    fun listTopBySignalType(
        signalType: String,
        limit: Int
    ): Flowable<List<RecommendationSignalEntity>>

    @Query("DELETE FROM recommendation_signal WHERE signal_type = :signalType")
    fun deleteBySignalType(signalType: String): Int
}
