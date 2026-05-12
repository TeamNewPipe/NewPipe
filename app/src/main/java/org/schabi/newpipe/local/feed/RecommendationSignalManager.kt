/*
 * SPDX-FileCopyrightText: 2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.feed

import android.content.Context
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.recommendation.model.RecommendationSignalEntity

class RecommendationSignalManager(context: Context) {
    private val signalTable = NewPipeDatabase.getInstance(context).recommendationSignalDAO()

    fun listTopBySignalType(
        signalType: String,
        limit: Int
    ): Flowable<List<RecommendationSignalEntity>> {
        return signalTable.listTopBySignalType(signalType, limit).subscribeOn(Schedulers.io())
    }

    fun upsertSignal(
        signalType: String,
        targetKey: String,
        score: Double,
        updatedAt: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)
    ): Completable {
        val entity = RecommendationSignalEntity(
            signalType = signalType,
            targetKey = targetKey,
            score = score,
            updatedAt = updatedAt.toEpochSecond()
        )
        return Completable.fromAction { signalTable.upsert(entity) }.subscribeOn(Schedulers.io())
    }

    fun clearSignalType(signalType: String): Completable {
        return Completable.fromAction { signalTable.deleteBySignalType(signalType) }
            .subscribeOn(Schedulers.io())
    }
}
