package org.schabi.newpipe.local.subscription

import android.app.Application
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.Item
import com.xwray.groupie.viewbinding.GroupieViewHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.schabi.newpipe.databinding.FeedItemCarouselBinding
import org.schabi.newpipe.local.subscription.item.FeedGroupAddNewGridItem
import org.schabi.newpipe.local.subscription.item.FeedGroupAddNewItem
import org.schabi.newpipe.local.subscription.item.FeedGroupCardGridItem
import org.schabi.newpipe.local.subscription.item.FeedGroupCardItem
import org.schabi.newpipe.local.subscription.item.FeedGroupImportGridItem
import org.schabi.newpipe.local.subscription.item.FeedGroupImportItem

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SubscriptionGroupShortcutsTest {
    @Test
    fun switchingBothPreferencesPreservesAllAndUserGroupsInEitherCarouselLayout() {
        for (listMode in listOf(true, false)) {
            val adapter = GroupAdapter<GroupieViewHolder<FeedItemCarouselBinding>>()
            val groups: List<Item<*>> = if (listMode) {
                listOf(
                    FeedGroupCardItem(name = "All", icon = FeedGroupIcon.WHATS_NEW),
                    FeedGroupCardItem(42, "My group", FeedGroupIcon.EDUCATION)
                )
            } else {
                listOf(
                    FeedGroupCardGridItem(name = "All", icon = FeedGroupIcon.WHATS_NEW),
                    FeedGroupCardGridItem(42, "My group", FeedGroupIcon.EDUCATION)
                )
            }
            adapter.addAll(groups)
            for ((showCreate, showImport) in listOf(true to true, false to true, false to false, true to false, true to true)) {
                SubscriptionGroupShortcuts.update(adapter, listMode, showCreate, showImport)
                val prefixSize = (if (showCreate) 1 else 0) + (if (showImport) 1 else 0)
                assertEquals(groups.size + prefixSize, adapter.itemCount)
                if (showCreate) {
                    assertTrue(if (listMode) adapter.getItem(0) is FeedGroupAddNewItem else adapter.getItem(0) is FeedGroupAddNewGridItem)
                }
                if (showImport) {
                    val item = adapter.getItem(if (showCreate) 1 else 0)
                    assertTrue(if (listMode) item is FeedGroupImportItem else item is FeedGroupImportGridItem)
                }
                groups.forEachIndexed { index, group -> assertSame(group, adapter.getItem(prefixSize + index)) }

                val before = (0 until adapter.itemCount).map(adapter::getItem)
                SubscriptionGroupShortcuts.update(adapter, listMode, showCreate, showImport)
                assertEquals(before.size, adapter.itemCount)
                before.forEachIndexed { index, item -> assertSame(item, adapter.getItem(index)) }
            }
        }
    }
}
