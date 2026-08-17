package org.schabi.newpipe.settings.tabs

import org.junit.Assert.assertTrue
import org.junit.Test

class TabTest {
    @Test
    fun checkIdDuplication() {
        val usedIds = HashSet<Int>()

        for (type in Tab.Type.entries) {
            val added = usedIds.add(type.tabId)
            assertTrue("Id was already used: " + type.tabId, added)
        }
    }
}
