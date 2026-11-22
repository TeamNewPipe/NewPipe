package org.schabi.newpipe.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryContentCategoryTest {
    @Test
    fun trustsOnlyExplicitProviderCategories() {
        assertEquals("TRACKS", HistoryContentCategory.fromProvider("Track"))
        assertEquals("PODCASTS", HistoryContentCategory.fromProvider("Podcast Episode"))
        assertEquals("COURSES", HistoryContentCategory.fromProvider("course"))
        assertNull(HistoryContentCategory.fromProvider("Music"))
        assertNull(HistoryContentCategory.fromProvider("Education"))
        assertNull(HistoryContentCategory.fromProvider("some podcast maybe"))
        assertNull(HistoryContentCategory.fromProvider(null))
    }
}
