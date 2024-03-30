package org.schabi.newpipe.settings.tabs

import org.junit.Assert
import org.junit.Test
import org.schabi.newpipe.settings.tabs.Tab.BlankTab.getTabId
import org.schabi.newpipe.settings.tabs.Tab.ChannelTab.getTabId
import org.schabi.newpipe.settings.tabs.Tab.DefaultKioskTab.getTabId
import org.schabi.newpipe.settings.tabs.Tab.KioskTab.getTabId
import org.schabi.newpipe.settings.tabs.Tab.SubscriptionsTab.getTabId
import org.schabi.newpipe.settings.tabs.Tab.Type.getTabId
import org.schabi.newpipe.settings.tabs.Tab.getTabId

class TabTest {
    @Test
    fun checkIdDuplication() {
        val usedIds: MutableSet<Int> = HashSet()
        for (type in Tab.Type.org.schabi.newpipe.settings.tabs.Tab.Type.entries.toTypedArray()) {
            val added = usedIds.add(type.getTabId())
            Assert.assertTrue("Id was already used: " + type.getTabId(), added)
        }
    }
}
