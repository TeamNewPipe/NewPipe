package org.schabi.newpipe.local.actions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentUndoActionsTest {
    @Test
    fun journalIsProfileScopedBoundedAndExpires() {
        val store = UndoHistory(capacity = 2, maxAgeMillis = 100)
        val first = store.record("one", "A", { true }, now = 1000)
        store.record("two", "B", { true }, now = 1001)
        store.record("one", "C", { true }, now = 1002)
        assertNull(store.claim(first, "one", 1003))
        assertEquals(1, store.snapshot("two", 1003).size)
        assertEquals(1, store.snapshot("one", 1003).size)
        assertTrue(store.snapshot("one", 1200).isEmpty())
    }

    @Test
    fun claimPreventsDoubleUndoAndFailedUndoRemainsAvailable() {
        val store = UndoHistory()
        val id = store.record("one", "Playlist", { true }, now = 1)
        assertNull(store.claim(id, "two", 2))
        val operation = store.claim(id, "one", 2)
        assertTrue(operation != null)
        assertNull(store.claim(id, "one", 2))
        store.finish(id, false)
        assertFalse(store.snapshot("one", 3).isEmpty())
        store.claim(id, "one", 3)
        store.finish(id, true)
        assertTrue(store.snapshot("one", 4).isEmpty())
    }
}
