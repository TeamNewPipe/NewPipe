package org.schabi.newpipe.about.changelog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogSearchTest {
    @Test
    fun findsCaseInsensitiveTextWithoutOverlappingMatches() {
        assertEquals(listOf(0..3, 9..12), ChangelogSearch.findMatches("Feed and FEED", "feed"))
        assertTrue(ChangelogSearch.findMatches("feed", "").isEmpty())
    }

    @Test
    fun boundsMatchesInLargeReleaseHistory() {
        assertEquals(2, ChangelogSearch.findMatches("a a a a", "a", limit = 2).size)
    }
}
