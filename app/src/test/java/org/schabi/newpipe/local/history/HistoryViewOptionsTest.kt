/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.history

import java.time.LocalDate
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType

class HistoryViewOptionsTest {
    private fun entry(
        id: Long,
        name: String,
        date: String,
        watches: Long
    ): StreamStatisticsEntry = StreamStatisticsEntry(
        streamEntity = StreamEntity(
            serviceId = 0,
            url = "https://example.org/$id",
            title = name,
            streamType = StreamType.VIDEO_STREAM,
            duration = 120,
            uploader = "Example"
        ),
        progressMillis = 0,
        streamId = id,
        latestAccessDate = OffsetDateTime.parse(date),
        watchCount = watches
    )

    @Test
    fun rangeIsInclusiveAndCanBeClearedWithoutRemovingHistory() {
        val from = LocalDate.parse("2026-11-10").toEpochDay()
        val to = LocalDate.parse("2026-11-15").toEpochDay()
        assertFalse(HistoryViewOptions.inDateRange(LocalDate.parse("2026-11-09"), from, to))
        assertTrue(HistoryViewOptions.inDateRange(LocalDate.parse("2026-11-10"), from, to))
        assertTrue(HistoryViewOptions.inDateRange(LocalDate.parse("2026-11-15"), from, to))
        assertFalse(HistoryViewOptions.inDateRange(LocalDate.parse("2026-11-16"), from, to))
        assertTrue(
            HistoryViewOptions.inDateRange(
                LocalDate.parse("2020-01-01"),
                Long.MIN_VALUE,
                Long.MAX_VALUE
            )
        )
    }

    @Test
    fun allFiveOrdersPreserveItemsWithoutChangingDatabase() {
        val newest = entry(3, "Beta", "2026-11-20T10:00:00Z", 2)
        val oldest = entry(1, "alpha", "2026-11-01T08:00:00Z", 8)
        val middle = entry(2, "Gamma", "2026-11-10T09:00:00Z", 2)
        val source = listOf(newest, middle, oldest)
        assertEquals(source, HistoryViewOptions.sort(source, "LAST_PLAYED"))
        assertEquals(
            listOf(oldest, middle, newest),
            HistoryViewOptions.sort(source, "EARLIEST_PLAYED")
        )
        assertEquals(
            listOf(oldest, newest, middle),
            HistoryViewOptions.sort(source, "MOST_PLAYED")
        )
        assertEquals(
            listOf(newest, middle, oldest),
            HistoryViewOptions.sort(source, "LEAST_PLAYED")
        )
        assertEquals(
            listOf(oldest, newest, middle),
            HistoryViewOptions.sort(source, "TITLE")
        )
        assertEquals(source, HistoryViewOptions.sort(source, "UNKNOWN"))
        assertEquals("Beta", source.first().streamEntity.title)
    }
}
