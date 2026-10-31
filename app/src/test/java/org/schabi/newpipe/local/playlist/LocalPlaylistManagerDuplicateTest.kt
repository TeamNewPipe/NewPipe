package org.schabi.newpipe.local.playlist

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalPlaylistManagerDuplicateTest {

    @Test
    fun testCleanTimingFromUrlStandardUrl() {
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", LocalPlaylistManager.cleanTimingFromUrl(url))
    }

    @Test
    fun testCleanTimingFromUrlWithTimeParameter() {
        val url1 = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=30s"
        val url2 = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=120"
        val expected = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

        assertEquals(expected, LocalPlaylistManager.cleanTimingFromUrl(url1))
        assertEquals(expected, LocalPlaylistManager.cleanTimingFromUrl(url2))
    }

    @Test
    fun testCleanTimingFromUrlShortLinkWithTime() {
        val url = "https://youtu.be/dQw4w9WgXcQ?t=45"
        assertEquals("https://youtu.be/dQw4w9WgXcQ", LocalPlaylistManager.cleanTimingFromUrl(url))
    }

    @Test
    fun testCleanTimingFromUrlWithTimeContinueParameter() {
        val url = "https://www.youtube.com/watch?time_continue=15&v=dQw4w9WgXcQ"
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", LocalPlaylistManager.cleanTimingFromUrl(url))
    }

    @Test
    fun testCleanTimingFromUrlWithHashFragment() {
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ#t=1m20s"
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", LocalPlaylistManager.cleanTimingFromUrl(url))
    }

    @Test
    fun testCleanTimingFromUrlWithStartParameter() {
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&start=60"
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", LocalPlaylistManager.cleanTimingFromUrl(url))
    }
}
