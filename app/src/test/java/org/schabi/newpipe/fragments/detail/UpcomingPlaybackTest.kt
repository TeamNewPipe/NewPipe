package org.schabi.newpipe.fragments.detail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.schabi.newpipe.extractor.stream.StreamInfo

class UpcomingPlaybackTest {
    @Test
    fun upcomingStreamsCannotStartPlaybackButOtherStreamDetailsCan() {
        val scheduled = Mockito.mock(StreamInfo::class.java)
        Mockito.`when`(scheduled.isUpcoming).thenReturn(true)
        val available = Mockito.mock(StreamInfo::class.java)
        Mockito.`when`(available.isUpcoming).thenReturn(false)

        assertTrue(UpcomingPlayback.shouldBlock(scheduled))
        assertFalse(UpcomingPlayback.shouldBlock(available))
        assertFalse(UpcomingPlayback.shouldBlock(null))
    }
}
