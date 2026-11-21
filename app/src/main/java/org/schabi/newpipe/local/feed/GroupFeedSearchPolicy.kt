package org.schabi.newpipe.local.feed

import org.schabi.newpipe.database.feed.model.FeedGroupEntity

/** Home tabs provide contextual search; standalone group feeds need a menu action. */
internal object GroupFeedSearchPolicy {
    fun showLocalSearch(groupId: Long, insideHomeTab: Boolean): Boolean {
        return groupId != FeedGroupEntity.GROUP_ALL_ID && !insideHomeTab
    }
}
