/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.subscription

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.viewbinding.GroupieViewHolder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FeedItemCarouselBinding
import org.schabi.newpipe.local.subscription.item.FeedGroupAddNewGridItem
import org.schabi.newpipe.local.subscription.item.FeedGroupAddNewItem
import org.schabi.newpipe.local.subscription.item.FeedGroupCardGridItem
import org.schabi.newpipe.local.subscription.item.FeedGroupCardItem
import org.schabi.newpipe.local.subscription.item.FeedGroupCarouselItem
import org.schabi.newpipe.local.subscription.item.FeedGroupImportGridItem

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class GroupRowsRecyclerViewTest {
    private lateinit var activity: ActivityController<Activity>
    private lateinit var parent: ScrollParent
    private lateinit var recycler: GroupRowsRecyclerView
    private val preferences get() = PreferenceManager.getDefaultSharedPreferences(activity.get())

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java)
        activity.get().setTheme(R.style.LightTheme)
        activity.setup()
        preferences.edit().clear().commit()
        parent = ScrollParent(activity.get())
        activity.get().setContentView(parent)
        recycler = LayoutInflater.from(activity.get()).inflate(R.layout.feed_item_carousel, parent, false) as GroupRowsRecyclerView
        parent.addView(recycler)
    }

    @After
    fun tearDown() {
        activity.pause().stop().destroy()
    }

    @Test
    fun defaultRemainsUnlimitedAndShortListsKeepTheirNaturalHeight() {
        populate(false, 12)
        measure()
        val unlimited = recycler.measuredHeight
        assertFalse(recycler.canScrollVertically(1))
        assertFalse(recycler.isVerticalScrollBarEnabled)

        preferences.edit().putString(SubscriptionGroupRows.KEY, "3").commit()
        measure()
        assertTrue(recycler.measuredHeight < unlimited)
        assertTrue(recycler.canScrollVertically(1))
        assertTrue(recycler.isVerticalScrollBarEnabled)

        populate(false, 2)
        measure()
        val short = recycler.measuredHeight
        assertFalse(recycler.isVerticalScrollBarEnabled)
        preferences.edit().putString(SubscriptionGroupRows.KEY, "0").commit()
        measure()
        assertEquals(short, recycler.measuredHeight)
    }

    @Test
    fun listAndGridCanReachTheLastGroupWithinTheBoundedViewport() {
        preferences.edit().putString(SubscriptionGroupRows.KEY, "3").commit()
        for (grid in listOf(false, true)) {
            populate(grid, 40)
            measure()
            val height = recycler.height
            assertTrue(recycler.canScrollVertically(1))
            recycler.scrollBy(0, 100_000)
            assertFalse(recycler.canScrollVertically(1))
            assertTrue(recycler.canScrollVertically(-1))
            assertEquals(40, (recycler.layoutManager as LinearLayoutManager).findLastVisibleItemPosition())
            assertEquals(height, recycler.height)
        }
    }

    @Test
    fun gridRecalculatesForColumnsWidthAndChangedTitles() {
        preferences.edit().putString(SubscriptionGroupRows.KEY, "2").commit()
        val adapter = populate(true, 30)
        measure(480)
        (recycler.layoutManager as GridLayoutManager).spanCount = 1
        measure(200)
        val singleColumnHeight = recycler.height
        assertTrue(recycler.height > 0)
        assertTrue(recycler.canScrollVertically(1))
        adapter.update(listOf(FeedGroupAddNewGridItem()) + (1..30).map { FeedGroupCardGridItem(it.toLong(), "A longer group title spanning several words $it", FeedGroupIcon.EDUCATION) })
        measure(200)
        assertTrue(recycler.height >= singleColumnHeight)
        recycler.layoutDirection = View.LAYOUT_DIRECTION_RTL
        measure(200)
        assertTrue(recycler.canScrollVertically(1))
    }

    @Test
    fun enlargedTextUsesMeasuredRowHeightsInBothLayouts() {
        preferences.edit().putString(SubscriptionGroupRows.KEY, "2").commit()
        for (grid in listOf(false, true)) {
            populate(grid, 30)
            measure(320)
            val normalHeight = recycler.height
            val configuration = Configuration(activity.get().resources.configuration).apply { fontScale = 2f }
            val largeContext = ContextThemeWrapper(activity.get().createConfigurationContext(configuration), R.style.LightTheme)
            parent.removeView(recycler)
            recycler = GroupRowsRecyclerView(largeContext)
            parent.addView(recycler)
            populate(grid, 30)
            measure(320)
            assertTrue(recycler.height >= normalHeight)
            assertTrue(recycler.canScrollVertically(1))
            assertCompleteRows(2)
            parent.removeView(recycler)
            recycler = GroupRowsRecyclerView(activity.get())
            parent.addView(recycler)
        }
    }

    @Test
    fun theGridCapIncludesBothShortcutHeightsAndCompleteVisibleRows() {
        preferences.edit().putString(SubscriptionGroupRows.KEY, "2").commit()
        val adapter = populate(true, 30)
        adapter.add(1, FeedGroupImportGridItem())
        measure(360)
        assertCompleteRows(2)
        assertTrue(recycler.canScrollVertically(1))
    }

    @Test
    fun gesturesAreReleasedToTheOuterListAtTheInnerBoundary() {
        preferences.edit().putString(SubscriptionGroupRows.KEY, "2").commit()
        populate(false, 30)
        measure()
        touch(MotionEvent.ACTION_DOWN, 80f)
        touch(MotionEvent.ACTION_MOVE, 30f)
        assertTrue(parent.disallowed)
        touch(MotionEvent.ACTION_UP, 30f)

        recycler.scrollBy(0, 100_000)
        touch(MotionEvent.ACTION_DOWN, 80f)
        touch(MotionEvent.ACTION_MOVE, 30f)
        assertFalse(parent.disallowed)
        touch(MotionEvent.ACTION_CANCEL, 30f)
    }

    @Test
    fun consumingTheLastPixelsOfASwipeReleasesTheOuterListImmediately() {
        preferences.edit().putString(SubscriptionGroupRows.KEY, "2").commit()
        populate(false, 30)
        measure()
        recycler.scrollBy(0, 100_000)
        recycler.scrollBy(0, -20)
        assertTrue(recycler.canScrollVertically(1))
        touch(MotionEvent.ACTION_DOWN, 100f)
        touch(MotionEvent.ACTION_MOVE, 0f)
        // Intercepting the card's gesture starts dragging at this position; a later MOVE
        // supplies the scroll delta that consumes the remaining pixels.
        assertEquals(RecyclerView.SCROLL_STATE_DRAGGING, recycler.scrollState)
        assertTrue(recycler.canScrollVertically(1))
        assertTrue(parent.disallowed)
        touch(MotionEvent.ACTION_MOVE, -100f)
        assertFalse(recycler.canScrollVertically(1))
        assertFalse("RecyclerView must not reclaim the gesture after reaching its edge", parent.disallowed)
        touch(MotionEvent.ACTION_CANCEL, -100f)
    }

    @Test
    fun horizontalGesturesAndCancellationAreReleasedToTheParent() {
        preferences.edit().putString(SubscriptionGroupRows.KEY, "2").commit()
        populate(false, 30)
        measure()
        touch(MotionEvent.ACTION_DOWN, 100f, 20f)
        touch(MotionEvent.ACTION_MOVE, 98f, 150f)
        assertFalse(parent.disallowed)
        touch(MotionEvent.ACTION_CANCEL, 98f, 150f)
        assertFalse(parent.disallowed)
    }

    @Test
    fun accessibilityExposesTheAvailableScrollDirections() {
        preferences.edit().putString(SubscriptionGroupRows.KEY, "2").commit()
        populate(false, 30)
        measure()
        val start = recycler.createAccessibilityNodeInfo()
        assertTrue(start.isScrollable)
        assertTrue(start.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD })
        recycler.scrollBy(0, 100_000)
        val end = recycler.createAccessibilityNodeInfo()
        assertTrue(end.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD })
        assertFalse(end.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD })
    }

    @Test
    fun rebindingUnchangedGroupLayoutPreservesItsScrollPosition() {
        preferences.edit().putString(SubscriptionGroupRows.KEY, "2").commit()
        val adapter = populate(false, 30)
        val item = FeedGroupCarouselItem(adapter, true)
        val binding = FeedItemCarouselBinding.bind(recycler)
        item.bind(binding, 0)
        measure()
        recycler.scrollBy(0, 500)
        val manager = recycler.layoutManager as LinearLayoutManager
        val position = manager.findFirstVisibleItemPosition()
        assertTrue(position > 0)
        item.bind(binding, 0, mutableListOf(FeedGroupCarouselItem.PAYLOAD_UPDATE_LIST_VIEW_MODE))
        measure()
        assertSame(manager, recycler.layoutManager)
        assertEquals(position, manager.findFirstVisibleItemPosition())
    }

    @Test
    fun savingASnapshotDoesNotRewindALaterFullBind() {
        preferences.edit().putString(SubscriptionGroupRows.KEY, "2").commit()
        val adapter = populate(false, 30)
        val item = FeedGroupCarouselItem(adapter, true)
        val binding = FeedItemCarouselBinding.bind(recycler)
        item.bind(binding, 0)
        measure()
        recycler.scrollBy(0, 400)
        assertNotNull(item.onSaveInstanceState())
        recycler.scrollBy(0, 400)
        val manager = recycler.layoutManager as LinearLayoutManager
        val position = manager.findFirstVisibleItemPosition()
        val offset = manager.findViewByPosition(position)!!.top
        item.bind(binding, 0)
        measure()
        assertEquals(position, manager.findFirstVisibleItemPosition())
        assertEquals(offset, manager.findViewByPosition(position)!!.top)
    }

    @Test
    fun recyclingAndRebindingTheCarouselRestoresItsInnerScrollAnchor() {
        preferences.edit().putString(SubscriptionGroupRows.KEY, "2").commit()
        val adapter = populate(false, 30)
        val item = FeedGroupCarouselItem(adapter, true)
        val binding = FeedItemCarouselBinding.bind(recycler)
        item.bind(binding, 0)
        measure()
        recycler.scrollBy(0, 500)
        val manager = recycler.layoutManager as LinearLayoutManager
        val position = manager.findFirstVisibleItemPosition()
        val offset = manager.findViewByPosition(position)!!.top
        item.unbind(GroupieViewHolder(binding))
        parent.removeView(recycler)
        recycler = LayoutInflater.from(activity.get()).inflate(R.layout.feed_item_carousel, parent, false) as GroupRowsRecyclerView
        parent.addView(recycler)
        item.bind(FeedItemCarouselBinding.bind(recycler), 0)
        measure()
        val replacement = recycler.layoutManager as LinearLayoutManager
        assertEquals(position, replacement.findFirstVisibleItemPosition())
        assertEquals(offset, replacement.findViewByPosition(position)!!.top)
    }

    @Test
    fun malformedPreferencesFallBackToTheExistingUnlimitedLayout() {
        for (value in listOf("-1", "11", "invalid")) {
            preferences.edit().putString(SubscriptionGroupRows.KEY, value).commit()
            assertEquals(SubscriptionGroupRows.UNLIMITED, SubscriptionGroupRows.get(activity.get()))
        }
    }

    private fun populate(grid: Boolean, count: Int): GroupAdapter<GroupieViewHolder<FeedItemCarouselBinding>> {
        val adapter = GroupAdapter<GroupieViewHolder<FeedItemCarouselBinding>>()
        if (grid) {
            adapter.add(FeedGroupAddNewGridItem())
            adapter.addAll((1..count).map { FeedGroupCardGridItem(it.toLong(), "Group $it", FeedGroupIcon.EDUCATION) })
        } else {
            adapter.add(FeedGroupAddNewItem())
            adapter.addAll((1..count).map { FeedGroupCardItem(it.toLong(), "Group $it", FeedGroupIcon.EDUCATION) })
        }
        recycler.layoutManager = if (grid) GridLayoutManager(activity.get(), 3) else LinearLayoutManager(activity.get())
        recycler.adapter = adapter
        return adapter
    }

    private fun measure(width: Int = 360) {
        recycler.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        recycler.layout(0, 0, recycler.measuredWidth, recycler.measuredHeight)
    }

    private fun assertCompleteRows(rows: Int) {
        val manager = recycler.layoutManager as LinearLayoutManager
        val columns = (manager as? GridLayoutManager)?.spanCount ?: 1
        val last = requireNotNull(manager.findViewByPosition(rows * columns - 1))
        val bounds = Rect()
        manager.getDecoratedBoundsWithMargins(last, bounds)
        assertEquals("The cap must fit full measured rows, including margins", recycler.height - recycler.paddingBottom, bounds.bottom)
    }

    private fun touch(action: Int, y: Float, x: Float = 20f) {
        val event = MotionEvent.obtain(0, 20, action, x, y, 0)
        recycler.dispatchTouchEvent(event)
        event.recycle()
    }

    private class ScrollParent(context: Context) : FrameLayout(context) {
        var disallowed = false
        override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            disallowed = disallowIntercept
            super.requestDisallowInterceptTouchEvent(disallowIntercept)
        }
    }
}
