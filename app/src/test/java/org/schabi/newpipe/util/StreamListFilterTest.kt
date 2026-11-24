package org.schabi.newpipe.util

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.database.history.model.StreamHistoryEntry
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

class StreamListFilterTest {
    @Test
    fun `unwatched accepts only streams without valid progress`() {
        val stream = stream(duration = 600)

        assertTrue(StreamListFilter.matches(StreamListFilter.UNWATCHED, stream, null))
        assertFalse(
            StreamListFilter.matches(
                StreamListFilter.UNWATCHED,
                stream,
                StreamStateEntity(1, 120_000)
            )
        )
    }

    @Test
    fun `partially watched excludes finished streams`() {
        val stream = stream(duration = 600)

        assertTrue(
            StreamListFilter.matches(
                StreamListFilter.PARTIALLY_WATCHED,
                stream,
                StreamStateEntity(1, 120_000)
            )
        )
        assertFalse(
            StreamListFilter.matches(
                StreamListFilter.PARTIALLY_WATCHED,
                stream,
                StreamStateEntity(1, 590_000)
            )
        )
    }

    @Test
    fun `live uses extractor stream types`() {
        assertTrue(
            StreamListFilter.matches(
                StreamListFilter.LIVE,
                stream(type = StreamType.LIVE_STREAM),
                null
            )
        )
        assertFalse(
            StreamListFilter.matches(
                StreamListFilter.LIVE,
                stream(type = StreamType.VIDEO_STREAM),
                null
            )
        )
    }

    @Test
    fun `upcoming is separate from live videos and shorts`() {
        val now = OffsetDateTime.parse("2026-09-08T12:00:00Z")
        val upcoming = stream(type = StreamType.LIVE_STREAM).apply {
            uploadDate = DateWrapper(OffsetDateTime.parse("2100-01-01T00:00:00Z"))
        }

        assertTrue(StreamListFilter.matches(StreamListFilter.UPCOMING, upcoming, null))
        assertFalse(StreamListFilter.matches(StreamListFilter.LIVE, upcoming, null))
        assertEquals(StreamListFilter.UPCOMING, StreamListFilter.categoryOf(upcoming, now))
    }

    @Test
    fun `explicit status handles delayed and undated events and overrides stale future dates`() {
        val now = OffsetDateTime.parse("2026-09-08T12:00:00Z")
        for (date in listOf(null, now.minusHours(2), now.plusHours(2))) {
            val scheduled = stream().apply {
                upcomingStatus = true
                uploadDate = date?.let { DateWrapper(it) }
            }
            assertEquals(StreamListFilter.UPCOMING, StreamListFilter.categoryOf(scheduled, now))
        }
        for (type in listOf(StreamType.LIVE_STREAM, StreamType.VIDEO_STREAM, StreamType.POST_LIVE_STREAM)) {
            val available = stream(type = type).apply {
                upcomingStatus = false
                uploadDate = DateWrapper(now.plusHours(2))
            }
            val expected = if (type == StreamType.LIVE_STREAM) StreamListFilter.LIVE else StreamListFilter.VIDEOS
            assertEquals(expected, StreamListFilter.categoryOf(available, now))
            available.upcomingStatus = null
            assertEquals(StreamListFilter.UPCOMING, StreamListFilter.categoryOf(available, now))
        }
    }

    @Test
    fun `entity and local item conversions preserve nullable provider status`() {
        val date = OffsetDateTime.parse("2026-09-08T12:00:00Z")
        for (status in listOf(true, false, null)) {
            val entity = StreamEntity(stream().apply { upcomingStatus = status })
            val restored = listOf(
                entity.toStreamInfoItem(),
                StreamStatisticsEntry(entity, 0, 1, date, 1).toStreamInfoItem(),
                StreamHistoryEntry(entity, 1, date, 1).toStreamInfoItem(),
                PlaylistStreamEntry(entity, 0, 1, 0).toStreamInfoItem()
            )
            restored.forEach { assertEquals(status, it.upcomingStatus) }
        }
    }

    @Test
    fun `videos keep normal short-duration videos but exclude actual Shorts and live streams`() {
        assertTrue(
            StreamListFilter.matches(
                StreamListFilter.VIDEOS,
                stream(duration = 30),
                null
            )
        )
        assertTrue(
            StreamListFilter.matches(
                StreamListFilter.VIDEOS,
                stream(duration = 180),
                null
            )
        )
        assertFalse(
            StreamListFilter.matches(
                StreamListFilter.VIDEOS,
                stream(duration = 180, isShort = true),
                null
            )
        )
        assertFalse(
            StreamListFilter.matches(
                StreamListFilter.VIDEOS,
                stream(url = "https://www.youtube.com/shorts/abcdefghijk", duration = -1),
                null
            )
        )
        assertFalse(
            StreamListFilter.matches(
                StreamListFilter.VIDEOS,
                stream(duration = 600, type = StreamType.LIVE_STREAM),
                null
            )
        )
    }

    @Test
    fun `shorts require extractor metadata or an explicit shorts url`() {
        assertFalse(
            StreamListFilter.matches(
                StreamListFilter.SHORTS,
                stream(duration = 30),
                null
            )
        )
        assertTrue(
            StreamListFilter.matches(
                StreamListFilter.SHORTS,
                stream(duration = 600, isShort = true),
                null
            )
        )
        assertTrue(
            StreamListFilter.matches(
                StreamListFilter.SHORTS,
                stream(url = "https://www.youtube.com/shorts/abcdefghijk", duration = -1),
                null
            )
        )
    }

    @Test
    fun `stream entity preserves extractor short-form metadata`() {
        val item = stream(
            url = "https://example.com/watch/short-metadata",
            duration = 600,
            isShort = true
        )

        val restored = StreamEntity(item).toStreamInfoItem()

        assertTrue(restored.isShortFormContent)
        assertEquals(StreamListFilter.SHORTS, StreamListFilter.categoryOf(restored))
    }

    @Test
    fun `history entries support every stream filter using saved progress`() {
        assertTrue(
            StreamListFilter.matches(
                StreamListFilter.UNWATCHED,
                historyEntry(progressMillis = 0)
            )
        )
        assertTrue(
            StreamListFilter.matches(
                StreamListFilter.LIVE,
                historyEntry(type = StreamType.LIVE_STREAM)
            )
        )
        assertTrue(
            StreamListFilter.matches(
                StreamListFilter.VIDEOS,
                historyEntry(duration = 600)
            )
        )
        assertTrue(
            StreamListFilter.matches(
                StreamListFilter.SHORTS,
                historyEntry(duration = 120, isShort = true)
            )
        )
        assertTrue(
            StreamListFilter.matches(
                StreamListFilter.UPCOMING,
                historyEntry(uploadDate = OffsetDateTime.parse("2100-01-01T00:00:00Z"))
            )
        )
        assertTrue(
            StreamListFilter.matches(
                StreamListFilter.PARTIALLY_WATCHED,
                historyEntry(duration = 600, progressMillis = 120_000)
            )
        )
        assertFalse(
            StreamListFilter.matches(
                StreamListFilter.PARTIALLY_WATCHED,
                historyEntry(duration = 600, progressMillis = 590_000)
            )
        )
    }

    private fun stream(
        url: String = "https://example.com/watch/video",
        duration: Long = 60,
        type: StreamType = StreamType.VIDEO_STREAM,
        isShort: Boolean = false
    ) = StreamInfoItem(0, url, "Title", type).apply {
        this.duration = duration
        uploaderName = "Uploader"
        setShortFormContent(isShort)
    }

    private fun historyEntry(
        url: String = "https://example.com/watch/video",
        duration: Long = 600,
        type: StreamType = StreamType.VIDEO_STREAM,
        progressMillis: Long = 0,
        uploadDate: OffsetDateTime? = null,
        isShort: Boolean = false
    ) = StreamStatisticsEntry(
        streamEntity = StreamEntity(
            serviceId = 0,
            url = url,
            title = "Title",
            streamType = type,
            duration = duration,
            uploader = "Uploader",
            isShortFormContent = isShort
        ).apply { this.uploadDate = uploadDate },
        progressMillis = progressMillis,
        streamId = 1,
        latestAccessDate = OffsetDateTime.parse("2026-08-21T12:00:00Z"),
        watchCount = 1
    )
}
