package org.schabi.newpipe.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullscreenCommentsSizingTest {
    @Test
    fun portraitPhoneUsesVerticalSpaceWhileWideAndTvLayoutsKeepTheirSidebar() {
        assertTrue(FullscreenCommentsSizing.useBottomPanel(1080, 2400, 3f, false))
        assertFalse(FullscreenCommentsSizing.useBottomPanel(2400, 1080, 3f, false))
        assertFalse(FullscreenCommentsSizing.useBottomPanel(1800, 2400, 3f, false))
        assertFalse(FullscreenCommentsSizing.useBottomPanel(1080, 2400, 3f, true))
    }

    @Test
    fun portraitPanelKeepsSpaceForPlaybackEvenAfterExtremeResize() {
        assertEquals(360, FullscreenCommentsSizing.height(800, 240, null))
        assertEquals(520, FullscreenCommentsSizing.height(800, 240, 1f))
        assertEquals(240, FullscreenCommentsSizing.height(800, 240, -1f))
        assertEquals(280, FullscreenCommentsSizing.height(640, 360, 0.8f))
        assertEquals(360, FullscreenCommentsSizing.height(800, 240, Float.NaN))
        assertEquals(1, FullscreenCommentsSizing.height(0, 240, null))
    }

    @Test
    fun defaultUsesExistingWidthUntilUserResizes() {
        assertEquals(320, FullscreenCommentsSizing.width(1000, 320, null))
        assertEquals(450, FullscreenCommentsSizing.width(1000, 700, null))
    }

    @Test
    fun userRatioAndDragStayWithinSafeBounds() {
        assertEquals(600, FullscreenCommentsSizing.width(1000, 320, 0.6f))
        assertEquals(250, FullscreenCommentsSizing.width(1000, 320, 0.1f))
        assertEquals(650, FullscreenCommentsSizing.clampWidth(900, 1000))
        assertEquals(250, FullscreenCommentsSizing.clampWidth(50, 1000))
        assertEquals(1, FullscreenCommentsSizing.width(0, 320, 0.4f))
    }
}
