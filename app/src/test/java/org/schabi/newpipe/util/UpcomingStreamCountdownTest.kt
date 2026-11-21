package org.schabi.newpipe.util

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

class UpcomingStreamCountdownTest {
    private fun live() = StreamInfoItem(
        0,
        "https://example.com/live",
        "Upcoming",
        StreamType.LIVE_STREAM
    )

    @Test
    fun supportsBothEpochSecondsAndMillisForScheduledStart() {
        val now = 1_700_000_000_000L
        val upcoming = live()
        upcoming.startAt = now / 1000 + 7200
        assertEquals(now + 7200_000L, UpcomingStreamCountdown.futureStartMillis(upcoming, now))

        upcoming.startAt = now + 7200_000L
        assertEquals(now + 7200_000L, UpcomingStreamCountdown.futureStartMillis(upcoming, now))
    }

    @Test
    fun fallsBackToFuturePremiereDateAndIgnoresPastOrUnknownItems() {
        val now = 1_700_000_000_000L
        val item = live()
        assertNull(UpcomingStreamCountdown.futureStartMillis(item, now))

        val start = now + 3600_000L
        item.uploadDate = DateWrapper(
            OffsetDateTime.ofInstant(Instant.ofEpochMilli(start), ZoneOffset.UTC)
        )
        assertEquals(start, UpcomingStreamCountdown.futureStartMillis(item, now))

        item.startAt = now / 1000 - 3600
        assertNull(UpcomingStreamCountdown.futureStartMillis(item, now))
    }
}
