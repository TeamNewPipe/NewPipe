package org.schabi.newpipe.settings.tabs

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchableTabChooserTest {
    private val names = listOf(
        "Custom content" to null,
        "My subscriptions" to "YouTube",
        "Favorite playlists" to "Local",
        "More content" to null,
        "Science" to "BiliBili"
    )
    private val headers = listOf(true, false, false, true, false)

    @Test fun emptyQueryRetainsHeadersAndOrder() {
        assertEquals(
            listOf(0, 1, 2, 3, 4),
            SearchableTabChooser.visibleIndices(names, headers, "")
        )
    }

    @Test fun searchesNamesAndDescriptionsWithoutReturningHeaders() {
        assertEquals(listOf(1), SearchableTabChooser.visibleIndices(names, headers, "tube"))
        assertEquals(listOf(4), SearchableTabChooser.visibleIndices(names, headers, " BILIBILI "))
        assertEquals(listOf(2), SearchableTabChooser.visibleIndices(names, headers, "favorite"))
    }

    @Test fun noMatchesReturnsEmptyList() {
        assertEquals(emptyList<Int>(), SearchableTabChooser.visibleIndices(names, headers, "missing"))
    }
}
