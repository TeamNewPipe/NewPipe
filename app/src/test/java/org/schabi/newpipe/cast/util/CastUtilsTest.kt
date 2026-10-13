package org.schabi.newpipe.cast.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CastUtilsTest {

    @Test
    fun testExtractStandardWatchUrl() {
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        assertEquals("dQw4w9WgXcQ", CastUtils.extractYouTubeVideoId(url))
    }

    @Test
    fun testExtractShortUrl() {
        val url = "https://youtu.be/dQw4w9WgXcQ"
        assertEquals("dQw4w9WgXcQ", CastUtils.extractYouTubeVideoId(url))
    }

    @Test
    fun testExtractShortsUrl() {
        val url = "https://www.youtube.com/shorts/dQw4w9WgXcQ"
        assertEquals("dQw4w9WgXcQ", CastUtils.extractYouTubeVideoId(url))
    }

    @Test
    fun testExtractEmbedUrl() {
        val url = "https://www.youtube.com/embed/dQw4w9WgXcQ"
        assertEquals("dQw4w9WgXcQ", CastUtils.extractYouTubeVideoId(url))
    }

    @Test
    fun testExtractMobileWatchUrlWithParams() {
        val url = "https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=42s&feature=shared"
        assertEquals("dQw4w9WgXcQ", CastUtils.extractYouTubeVideoId(url))
    }

    @Test
    fun testExtractRelativeWatchUrl() {
        val url = "/watch?v=dQw4w9WgXcQ"
        assertEquals("dQw4w9WgXcQ", CastUtils.extractYouTubeVideoId(url))
    }

    @Test
    fun testExtractRawVideoId() {
        val id = "dQw4w9WgXcQ"
        assertEquals("dQw4w9WgXcQ", CastUtils.extractYouTubeVideoId(id))
    }

    @Test
    fun testExtractNonYouTubeUrlReturnsNull() {
        val url = "https://peertube.example.com/videos/watch/12345"
        assertNull(CastUtils.extractYouTubeVideoId(url))
    }

    @Test
    fun testExtractNullOrBlankReturnsNull() {
        assertNull(CastUtils.extractYouTubeVideoId(null))
        assertNull(CastUtils.extractYouTubeVideoId(""))
        assertNull(CastUtils.extractYouTubeVideoId("   "))
    }
}
