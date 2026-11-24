package org.schabi.newpipe.local.subscription

import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.Item
import org.schabi.newpipe.local.subscription.item.FeedGroupAddNewGridItem
import org.schabi.newpipe.local.subscription.item.FeedGroupAddNewItem
import org.schabi.newpipe.local.subscription.item.FeedGroupImportGridItem
import org.schabi.newpipe.local.subscription.item.FeedGroupImportItem

/** Reconciles only the optional shortcut prefix; All and the user's actual groups retain identity. */
internal object SubscriptionGroupShortcuts {
    fun update(adapter: GroupAdapter<*>, listMode: Boolean, showCreate: Boolean, showImport: Boolean) {
        val current = (0 until adapter.itemCount).map(adapter::getItem)
        val prefix = buildList<Item<*>> {
            if (showCreate) {
                add(
                    if (listMode) {
                        current.firstOrNull { it is FeedGroupAddNewItem } ?: FeedGroupAddNewItem()
                    } else {
                        current.firstOrNull { it is FeedGroupAddNewGridItem } ?: FeedGroupAddNewGridItem()
                    }
                )
            }
            if (showImport) {
                add(
                    if (listMode) {
                        current.firstOrNull { it is FeedGroupImportItem } ?: FeedGroupImportItem()
                    } else {
                        current.firstOrNull { it is FeedGroupImportGridItem } ?: FeedGroupImportGridItem()
                    }
                )
            }
        }
        current.filter(::isShortcut).filter { existing -> prefix.none { it === existing } }.forEach(adapter::remove)
        prefix.forEachIndexed { position, item ->
            val existingPosition = (0 until adapter.itemCount).firstOrNull { adapter.getItem(it) === item }
            if (existingPosition != position) {
                if (existingPosition != null) adapter.remove(item)
                adapter.add(position, item)
            }
        }
    }

    private fun isShortcut(item: Item<*>): Boolean = item is FeedGroupAddNewItem || item is FeedGroupAddNewGridItem ||
        item is FeedGroupImportItem || item is FeedGroupImportGridItem
}
