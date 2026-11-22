package org.schabi.newpipe.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType

class VimeoCacheLifetimeTest {
    @Test
    fun recurringEventRechecksItsSelectedRecordingBeforeTheNextBroadcast() {
        val event = info("https://vimeo.com/event/4753126")
        assertEquals(30_000L, StreamCacheLifetime.expiration(event, 300_000L))
        assertEquals(10_000L, StreamCacheLifetime.expiration(event, 10_000L))
        assertEquals(300_000L, StreamCacheLifetime.expiration(info("https://vimeo.com/1247036786"), 300_000L))
    }

    @Test
    fun upcomingAndActiveBroadcastsAreNotKeptInTheNormalVideoCacheForFiveMinutes() {
        val upcoming = info("https://vimeo.com/1085320934").apply { isUpcoming = true }
        assertEquals(30_000L, StreamCacheLifetime.expiration(upcoming, 300_000L))
        val active = info("https://vimeo.com/1240101097").apply { streamType = StreamType.LIVE_STREAM }
        assertEquals(30_000L, StreamCacheLifetime.expiration(active, 300_000L))
    }

    private fun info(url: String): StreamInfo = StreamInfo(ServiceList.Vimeo.serviceId, "fixture", url, "Fixture").apply {
        streamType = StreamType.VIDEO_STREAM
    }
}
