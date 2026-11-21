package org.schabi.newpipe.local.feed

/**
 * Home tabs may restore with their app bar collapsed even when the feed list is at its top.
 * Let those tabs pull to refresh; keep the standalone screen's header-expansion gesture.
 */
internal object FeedPullRefreshPolicy {
    fun shouldBlockGesture(
        feedHeaderExpanded: Boolean,
        listCanScrollUp: Boolean,
        insideHomeTab: Boolean
    ): Boolean = listCanScrollUp || (!insideHomeTab && !feedHeaderExpanded)
}
