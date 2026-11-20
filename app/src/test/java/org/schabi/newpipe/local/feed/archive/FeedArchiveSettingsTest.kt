package org.schabi.newpipe.local.feed.archive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedArchiveSettingsTest {
    @Test
    fun retentionPoliciesAreExplicitAndBounded() {
        assertNull(FeedArchiveSettings.option(0))
        assertEquals(FeedArchiveSettings.Mode(30, 2500), FeedArchiveSettings.option(1))
        assertEquals(FeedArchiveSettings.Mode(90, 5000), FeedArchiveSettings.option(2))
        assertEquals(FeedArchiveSettings.Mode(180, 10000), FeedArchiveSettings.option(3))
        assertNull(FeedArchiveSettings.option(99))
    }
}
