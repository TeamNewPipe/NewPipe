package org.schabi.newpipe.ui.components.menu

import org.junit.Assert.assertEquals
import org.junit.Test

class LongPressActionTest {
    @Test
    fun `LongPressAction Type ids are unique`() {
        val ids = LongPressAction.Type.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
