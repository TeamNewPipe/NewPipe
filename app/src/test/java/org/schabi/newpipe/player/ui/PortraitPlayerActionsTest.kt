package org.schabi.newpipe.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortraitPlayerActionsTest {
    @Test
    fun emptyOrInvalidOrderReturnsDefaultUniqueSet() {
        val empty = PortraitPlayerActions.normalize(null)
        assertTrue(empty.isNotEmpty())
        assertEquals(empty.size, empty.toSet().size)
        assertEquals(empty, PortraitPlayerActions.normalize("unknown,unknown"))
    }

    @Test
    fun customOrderIsStableAndNewActionsAppendWithoutDuplicatingKeys() {
        val default = PortraitPlayerActions.normalize(null)
        val revised = PortraitPlayerActions.normalize("cast,share,cast,unknown")
        assertEquals(listOf("cast", "share"), revised.take(2))
        assertEquals(default.toSet(), revised.toSet())
        assertEquals(default.size, revised.size)
    }
}
