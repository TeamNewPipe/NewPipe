/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.StreamingService

class StreamUpcomingStatusTest {
    @Test
    fun legacyExtractorsKeepUnknownAvailabilityButRetainPositiveUpcomingFlags() {
        val extractor = Mockito.mock(StreamExtractor::class.java)
        Mockito.`when`(extractor.upcomingStatus).thenCallRealMethod()
        assertNull(extractor.upcomingStatus)
        Mockito.`when`(extractor.isUpcoming).thenReturn(true)
        assertEquals(true, extractor.upcomingStatus)

        val info = StreamInfo(1, "https://example.com/video", "https://example.com/video", StreamType.VIDEO_STREAM, "video", "Title", 0)
        info.uploaderName = "Channel"
        assertFalse(info.isUpcoming)
        assertNull(info.upcomingStatus)
        assertNull(StreamEntity(info).upcomingStatus)
        info.isUpcoming = true
        assertEquals(true, StreamEntity(info).upcomingStatus)
    }

    @Test
    fun fullInfoExtractionAndEntityRoundTripKeepAllThreeProviderStates() {
        for (status in listOf(true, false, null)) {
            val extractor = Mockito.mock(StreamExtractor::class.java)
            Mockito.`when`(extractor.service).thenReturn(Mockito.mock(StreamingService::class.java))
            Mockito.`when`(extractor.url).thenReturn("https://example.com/video")
            Mockito.`when`(extractor.originalUrl).thenReturn("https://example.com/video")
            Mockito.`when`(extractor.id).thenReturn("video")
            Mockito.`when`(extractor.name).thenReturn("Title")
            Mockito.`when`(extractor.streamType).thenReturn(StreamType.VIDEO_STREAM)
            Mockito.`when`(extractor.uploaderName).thenReturn("Channel")
            Mockito.`when`(extractor.isUpcoming).thenReturn(status == true)
            Mockito.`when`(extractor.upcomingStatus).thenReturn(status)
            Mockito.`when`(extractor.hlsUrl).thenReturn("https://example.com/playable.m3u8")
            val info = StreamInfo.getInfo(extractor)
            assertEquals(status, info.upcomingStatus)
            assertEquals(status, StreamEntity(info).toStreamInfoItem().upcomingStatus)
        }
    }
}
