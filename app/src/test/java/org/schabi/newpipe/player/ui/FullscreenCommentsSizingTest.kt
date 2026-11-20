package org.schabi.newpipe.player.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FullscreenCommentsSizingTest {
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
