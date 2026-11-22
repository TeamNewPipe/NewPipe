package org.schabi.newpipe.player.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PortraitActionPreviewModelTest {
    @Test
    fun changesOrderWithoutMutatingOriginalActions() {
        val original = listOf("captions", "share", "resize")
        val reordered = listOf("resize", "captions", "share")
        assertEquals(reordered, PortraitActionPreviewModel.visible(reordered))
        assertEquals("captions", original.first())
    }

    @Test
    fun limitsPreviewToEightControls() {
        val actions = (1..21).map(Int::toString)
        assertEquals(actions.take(8), PortraitActionPreviewModel.visible(actions))
    }
}
