package org.schabi.newpipe.util.urlfinder

import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeUrlNormalizerTest {
    @Test
    fun normalizesMobileYouTubeWatchUrl() {
        assertEquals(
            WATCH_URL,
            YouTubeUrlNormalizer.normalize("https://m.youtube.com/watch?v=dQw4w9WgXcQ&feature=share")
        )
    }

    @Test
    fun normalizesShortYoutuBeUrl() {
        assertEquals(
            WATCH_URL,
            YouTubeUrlNormalizer.normalize("https://youtu.be/dQw4w9WgXcQ?t=43")
        )
    }

    @Test
    fun normalizesMusicYouTubeWatchUrl() {
        assertEquals(
            WATCH_URL,
            YouTubeUrlNormalizer.normalize("https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=RDAMVM")
        )
    }

    @Test
    fun normalizesShortsUrl() {
        assertEquals(
            WATCH_URL,
            YouTubeUrlNormalizer.normalize("https://www.youtube.com/shorts/dQw4w9WgXcQ")
        )
    }

    @Test
    fun normalizesYoutubeNoCookieEmbedUrl() {
        assertEquals(
            WATCH_URL,
            YouTubeUrlNormalizer.normalize("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ")
        )
    }

    companion object {
        private const val WATCH_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    }
}
