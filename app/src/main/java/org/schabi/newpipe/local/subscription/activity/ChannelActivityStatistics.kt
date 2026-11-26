/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.subscription.activity

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import org.schabi.newpipe.database.feed.dao.ChannelActivityRecord
import org.schabi.newpipe.database.feed.dao.ChannelActivitySnapshot
import org.schabi.newpipe.database.stream.StreamUploadDate
import org.schabi.newpipe.extractor.stream.StreamType

/** Counts observed publications; an empty bucket is not evidence that a channel was inactive. */
data class ChannelActivityStatistics(
    val windowStart: LocalDate,
    val windowEnd: LocalDate,
    val uploadDates: List<LocalDate>,
    val latestKnownUpload: LocalDate?,
    val oldestObservedUpload: LocalDate?,
    val approximateDates: Int,
    val undatedItems: Int,
    val lastUpdated: Instant?
) {
    data class Bucket(val start: LocalDate, val end: LocalDate, val count: Int)

    fun buckets(weekly: Boolean): List<Bucket> {
        fun start(date: LocalDate): LocalDate = if (weekly) {
            date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        } else {
            date.withDayOfMonth(1)
        }
        val counts = uploadDates.groupingBy(::start).eachCount()
        val result = mutableListOf<Bucket>()
        var cursor = start(windowStart)
        while (cursor <= windowEnd) {
            val next = if (weekly) cursor.plusWeeks(1) else cursor.plusMonths(1)
            result += Bucket(maxOf(cursor, windowStart), minOf(next.minusDays(1), windowEnd), counts[cursor] ?: 0)
            cursor = next
        }
        return result
    }

    companion object {
        fun from(snapshot: ChannelActivitySnapshot, cutoff: Instant, now: Instant, zone: ZoneId): ChannelActivityStatistics {
            require(!cutoff.isAfter(now))
            val published = snapshot.records.distinctBy { it.streamId }.filter {
                it.upcoming != true && it.streamType in setOf(
                    StreamType.VIDEO_STREAM,
                    StreamType.AUDIO_STREAM,
                    StreamType.POST_LIVE_STREAM,
                    StreamType.POST_LIVE_AUDIO_STREAM
                ) && (it.publishedAt == null || !it.publishedAt.toInstant().isAfter(now))
            }
            fun dated(item: ChannelActivityRecord): Boolean = item.publishedAt != null &&
                !StreamUploadDate.isSynthetic(item.serviceId, item.approximate, item.dateText)
            val known = published.filter(::dated)
            val recent = known.filter { !requireNotNull(it.publishedAt).toInstant().isBefore(cutoff) }
            val dates = recent.map { requireNotNull(it.publishedAt).atZoneSameInstant(zone).toLocalDate() }.sorted()
            return ChannelActivityStatistics(
                cutoff.atZone(zone).toLocalDate(),
                now.atZone(zone).toLocalDate(),
                dates,
                known.maxOfOrNull { requireNotNull(it.publishedAt).atZoneSameInstant(zone).toLocalDate() },
                dates.firstOrNull(),
                recent.count { it.approximate == true },
                published.count { !dated(it) },
                snapshot.lastUpdated?.toInstant()
            )
        }
    }
}
