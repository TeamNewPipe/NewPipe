/*
 * SPDX-FileCopyrightText: 2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.feed

import android.content.Context
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.local.history.HistoryRecordManager

/**
 * Builds lightweight recommendation signals from local watch history.
 * This is intentionally rule-based and fully local.
 */
class RecommendationBootstrapper(context: Context) {
    private val recommendationSignalManager = RecommendationSignalManager(context)
    private val historyRecordManager = HistoryRecordManager(context)

    fun rebuildChannelAffinitySignals(limit: Int = 500): Completable {
        return Completable.fromAction {
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            val entries = historyRecordManager.getStreamStatistics().blockingFirst()
                .sortedWith(compareByDescending<StreamStatisticsEntry> { it.latestAccessDate }
                    .thenByDescending { it.watchCount })
            val channelScores = linkedMapOf<String, Double>()

            entries.take(limit).forEach { entry ->
                val channelKey = "${entry.streamEntity.serviceId}:${entry.streamEntity.uploaderUrl}"
                if (entry.streamEntity.uploaderUrl.isNullOrBlank()) {
                    return@forEach
                }

                val completionRatio = if (entry.streamEntity.duration > 0) {
                    entry.progressMillis.toDouble() / (entry.streamEntity.duration * 1000.0)
                } else {
                    0.0
                }.coerceIn(0.0, 1.0)

                val watchWeight = entry.watchCount.toDouble().coerceAtMost(20.0)
                val score = watchWeight + (completionRatio * 5.0)
                channelScores[channelKey] = (channelScores[channelKey] ?: 0.0) + score
            }

            recommendationSignalManager.clearSignalType(SIGNAL_TYPE_CHANNEL_AFFINITY).blockingAwait()
            channelScores.forEach { (channelKey, score) ->
                recommendationSignalManager
                    .upsertSignal(SIGNAL_TYPE_CHANNEL_AFFINITY, channelKey, score, now)
                    .blockingAwait()
            }
        }.subscribeOn(Schedulers.io())
    }

    companion object {
        const val SIGNAL_TYPE_CHANNEL_AFFINITY = "channel_affinity"
    }
}
