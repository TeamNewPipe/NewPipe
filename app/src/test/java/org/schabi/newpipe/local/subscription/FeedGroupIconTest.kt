package org.schabi.newpipe.local.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedGroupIconTest {
    @Test
    fun `New group icons preserve existing persisted icon ids`() {
        assertEquals(FeedGroupIcon.ALL, FeedGroupIcon.fromId(0))
        assertEquals(FeedGroupIcon.WHATS_NEW, FeedGroupIcon.fromId(38))
        assertEquals(FeedGroupIcon.HISTORY, FeedGroupIcon.fromId(39))
        assertEquals(FeedGroupIcon.DOCUMENTS, FeedGroupIcon.fromId(54))
        for (icon in FeedGroupIcon.entries) {
            assertEquals(icon, FeedGroupIcon.fromDrawableResource(icon.drawableResource))
        }
    }

    @Test
    fun `No gaps and repeated ids`() {
        val usedIds = HashSet<Int>()

        for ((shouldBeId, currentIcon) in FeedGroupIcon.entries.withIndex()) {
            val added = usedIds.add(currentIcon.id)
            assertTrue("Repeated ids (current item: ${currentIcon.name} - ${currentIcon.id})", added)

            assertEquals(
                "Gap between ids detected (current item: ${currentIcon.name} - ${currentIcon.id} → should be: $shouldBeId)",
                shouldBeId,
                currentIcon.id
            )
        }
    }

    @Test
    fun `No icons pointing to the same attr`() {
        val usedIcons = HashSet<Int>()

        for (groupIcon in FeedGroupIcon.entries) {
            val added = usedIcons.add(groupIcon.drawableResource)
            assertTrue("Repeated icon (current item: ${groupIcon.name} - ${groupIcon.id})", added)
        }
    }
}
