package org.schabi.newpipe.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserSeekHistoryTest {
    @Test
    fun tracksUserSeeksAndSupportsBackForward() {
        val h = UserSeekHistory()
        h.record("video", 10_000, 25_000)
        h.record("video", 25_000, 40_000)
        assertEquals(25_000L, h.back("video"))
        assertEquals(10_000L, h.back("video"))
        assertFalse(h.canBack("video"))
        assertEquals(25_000L, h.forward("video"))
        h.record("video", 25_000, 37_000)
        assertFalse(h.canForward("video"))
        assertEquals(25_000L, h.back("video"))
        assertTrue(h.canForward("video"))
    }

    @Test
    fun ignoresTinyAndInvalidSeeksAndResetsOnMediaChange() {
        val h = UserSeekHistory(3)
        h.record("video-a", 1000, 1500)
        assertFalse(h.canBack("video-a"))
        h.record("video-a", 2000, 5000)
        h.record("video-a", 5000, 8000)
        h.record("video-a", 8000, 12000)
        assertEquals(8000L, h.back("video-a"))
        assertFalse(h.canBack("video-b"))
        assertNull(h.back("video-a"))
    }
}
