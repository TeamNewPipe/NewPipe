package org.schabi.newpipe.local.feed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.database.feed.model.FeedGroupEntity

class GroupFeedSearchPolicyTest {
    @Test
    fun standaloneSubscriptionGroupHasSearchIcon() {
        assertTrue(GroupFeedSearchPolicy.showLocalSearch(12, false))
    }

    @Test
    fun homeTabsAndAllFeedUseExistingSearchAction() {
        assertFalse(GroupFeedSearchPolicy.showLocalSearch(12, true))
        assertFalse(GroupFeedSearchPolicy.showLocalSearch(FeedGroupEntity.GROUP_ALL_ID, false))
    }
}
