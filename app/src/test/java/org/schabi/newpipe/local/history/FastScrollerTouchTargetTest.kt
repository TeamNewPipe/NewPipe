package org.schabi.newpipe.local.history

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FastScrollerTouchTargetTest {
    @Test
    fun onlyThumbTouchBeginsDragAndFeedRowActionTapsPassThrough() {
        assertTrue(FastScrollerTouchTarget.isOnThumb(39f, 200f, 40f, 200f, 16f, 30f))
        assertFalse(FastScrollerTouchTarget.isOnThumb(5f, 200f, 40f, 200f, 16f, 30f))
        assertFalse(FastScrollerTouchTarget.isOnThumb(40f, 400f, 40f, 200f, 16f, 30f))
    }

    @Test
    fun rejectsInvalidCoordinates() {
        assertFalse(
            FastScrollerTouchTarget.isOnThumb(
                Float.NaN,
                200f,
                40f,
                200f,
                16f,
                30f
            )
        )
    }
}
