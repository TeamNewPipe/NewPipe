/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.subscription.activity

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.database.feed.dao.ChannelActivityRecord
import org.schabi.newpipe.database.feed.dao.ChannelActivitySnapshot
import org.schabi.newpipe.extractor.stream.StreamType

class ChannelActivityStatisticsTest {
    private val cutoff = Instant.parse("2026-09-01T00:00:00Z")
    private val now = Instant.parse("2026-11-26T12:00:00Z")

    @Test
    fun countsDistinctPublicationsAndIncludesEmptyMonthsWithoutInventingUploads() {
        val september = item(1, "2026-09-01T00:00:00Z")
        val november = item(2, "2026-11-26T12:00:00Z")
        val result = statistics(listOf(september, september, november))
        assertEquals(listOf(1, 0, 1), result.buckets(false).map { it.count })
        assertEquals(2, result.uploadDates.size)
        assertEquals(LocalDate.parse("2026-09-01"), result.oldestObservedUpload)
        assertEquals(LocalDate.parse("2026-11-26"), result.latestKnownUpload)
        assertEquals(LocalDate.parse("2026-11-26"), result.buckets(false).last().end)
    }

    @Test
    fun syntheticAndMissingDatesDoNotBecomePublicationDates() {
        val records = listOf(
            item(1, "2026-10-05T00:00:00Z").copy(approximate = true, dateText = ""),
            item(2, null),
            item(3, "2026-10-08T00:00:00Z").copy(approximate = true, dateText = "one month ago"),
            item(4, "2026-10-09T00:00:00Z")
        )
        val result = statistics(records)
        assertEquals(2, result.uploadDates.size)
        assertEquals(2, result.undatedItems)
        assertEquals(1, result.approximateDates)
    }

    @Test
    fun futureUpcomingAndOngoingLiveItemsAreExcludedButEndedArchivesCount() {
        val result = statistics(
            listOf(
                item(1, "2026-11-27T00:00:00Z"),
                item(2, "2026-10-01T00:00:00Z").copy(upcoming = true),
                item(3, "2026-10-01T00:00:00Z").copy(streamType = StreamType.LIVE_STREAM),
                item(4, "2026-10-01T00:00:00Z").copy(streamType = StreamType.AUDIO_LIVE_STREAM),
                item(5, "2026-10-01T00:00:00Z").copy(streamType = StreamType.POST_LIVE_STREAM),
                item(6, "2026-10-02T00:00:00Z").copy(streamType = StreamType.POST_LIVE_AUDIO_STREAM)
            )
        )
        assertEquals(2, result.uploadDates.size)
        assertEquals(0, result.undatedItems)
        assertEquals(LocalDate.parse("2026-10-02"), result.latestKnownUpload)
    }

    @Test
    fun oldRetainedLastUploadIsVisibleWithoutBeingCountedInsideTheWindow() {
        val result = statistics(listOf(item(1, "2025-01-01T00:00:00Z")))
        assertTrue(result.uploadDates.isEmpty())
        assertEquals(LocalDate.parse("2025-01-01"), result.latestKnownUpload)
        assertNull(result.oldestObservedUpload)
        assertTrue(result.buckets(true).all { it.count == 0 })
    }

    @Test
    fun weeksUseLocalPublicationDatesAndIncludePartialBoundaryWeeks() {
        val result = ChannelActivityStatistics.from(
            ChannelActivitySnapshot(listOf(item(1, "2026-11-02T00:30:00Z")), null),
            Instant.parse("2026-10-31T00:00:00Z"),
            Instant.parse("2026-11-05T00:00:00Z"),
            ZoneId.of("America/Los_Angeles")
        )
        assertEquals(LocalDate.parse("2026-11-01"), result.uploadDates.single())
        assertEquals(listOf(1, 0), result.buckets(true).map { it.count })
        assertEquals(LocalDate.parse("2026-10-30"), result.buckets(true).first().start)
        assertEquals(LocalDate.parse("2026-11-04"), result.buckets(true).last().end)
    }

    @Test
    fun leapMonthsAndEmptySnapshotsHaveFiniteBuckets() {
        val result = ChannelActivityStatistics.from(
            ChannelActivitySnapshot(emptyList(), null),
            Instant.parse("2024-02-01T00:00:00Z"),
            Instant.parse("2024-03-02T00:00:00Z"),
            ZoneOffset.UTC
        )
        assertEquals(2, result.buckets(false).size)
        assertEquals(LocalDate.parse("2024-02-29"), result.buckets(false).first().end)
        assertNull(result.latestKnownUpload)
        assertNull(result.lastUpdated)
    }

    private fun statistics(records: List<ChannelActivityRecord>): ChannelActivityStatistics = ChannelActivityStatistics.from(
        ChannelActivitySnapshot(records, OffsetDateTime.parse("2026-11-26T11:00:00Z")),
        cutoff,
        now,
        ZoneOffset.UTC
    )

    private fun item(id: Long, date: String?): ChannelActivityRecord = ChannelActivityRecord(
        id,
        0,
        date?.let(OffsetDateTime::parse),
        false,
        date,
        StreamType.VIDEO_STREAM,
        false
    )
}
