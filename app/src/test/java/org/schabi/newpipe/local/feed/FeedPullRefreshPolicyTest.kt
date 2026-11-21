package org.schabi.newpipe.local.feed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedPullRefreshPolicyTest {
    @Test
    fun homeTabAllowsPullToRefreshWhenListIsAtTopEvenWithCollapsedHeader() {
        assertFalse(FeedPullRefreshPolicy.shouldBlockGesture(false, false, true))
        assertFalse(FeedPullRefreshPolicy.shouldBlockGesture(true, false, true))
    }

    @Test
    fun listContentPreventsRefreshUntilScrolledToTop() {
        assertTrue(FeedPullRefreshPolicy.shouldBlockGesture(false, true, true))
        assertTrue(FeedPullRefreshPolicy.shouldBlockGesture(true, true, false))
    }

    @Test
    fun standaloneFeedStillExpandsCollapsedHeaderFirst() {
        assertTrue(FeedPullRefreshPolicy.shouldBlockGesture(false, false, false))
        assertFalse(FeedPullRefreshPolicy.shouldBlockGesture(true, false, false))
    }
}
