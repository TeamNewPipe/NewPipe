package org.schabi.newpipe.fragments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class HomeTabRetentionTest {
    private data class Tab(val id: Int)

    @Test
    fun discardedHomeTabDoesNotAccumulateInRetainedList() {
        val first = Tab(1)
        val second = Tab(2)
        val retained = mutableListOf(first, second)
        HomeTabRetention.forget(retained, first)
        assertEquals(1, retained.size)
        assertSame(second, retained.single())
    }

    @Test
    fun keepsDistinctInstanceWithEqualContent() {
        val removed = Tab(1)
        val replacement = Tab(1)
        val retained = mutableListOf(removed, replacement)
        HomeTabRetention.forget(retained, removed)
        assertSame(replacement, retained.single())
    }
}
