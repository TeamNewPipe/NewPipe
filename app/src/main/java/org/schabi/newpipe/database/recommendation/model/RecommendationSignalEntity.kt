/*
 * SPDX-FileCopyrightText: 2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.recommendation.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = RecommendationSignalEntity.RECOMMENDATION_SIGNAL_TABLE,
    primaryKeys = [
        RecommendationSignalEntity.SIGNAL_TYPE,
        RecommendationSignalEntity.TARGET_KEY
    ],
    indices = [
        Index(value = [RecommendationSignalEntity.SCORE]),
        Index(value = [RecommendationSignalEntity.UPDATED_AT])
    ]
)
data class RecommendationSignalEntity(
    @ColumnInfo(name = SIGNAL_TYPE)
    val signalType: String,

    @ColumnInfo(name = TARGET_KEY)
    val targetKey: String,

    @ColumnInfo(name = SCORE)
    val score: Double,

    @ColumnInfo(name = UPDATED_AT)
    val updatedAt: Long
) {
    companion object {
        const val RECOMMENDATION_SIGNAL_TABLE: String = "recommendation_signal"
        const val SIGNAL_TYPE: String = "signal_type"
        const val TARGET_KEY: String = "target_key"
        const val SCORE: String = "score"
        const val UPDATED_AT: String = "updated_at"
    }
}
