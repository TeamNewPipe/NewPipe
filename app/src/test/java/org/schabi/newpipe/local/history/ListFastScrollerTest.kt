package org.schabi.newpipe.local.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListFastScrollerTest {
    @Test
    fun requiresLargeListAndProducesBoundedPositionLabels() {
        assertFalse(ListFastScroller.shouldEnable(49))
        assertTrue(ListFastScroller.shouldEnable(50))
        assertEquals("1 / 50", ListFastScroller.label(0, 50))
        assertEquals("50 / 50", ListFastScroller.label(300, 50))
        assertEquals("0 / 0", ListFastScroller.label(0, 0))
    }
}
