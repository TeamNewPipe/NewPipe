/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.settings.tabs

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.InputDevice
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.appbar.AppBarLayout
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.settings.SettingsActivity

@RunWith(AndroidJUnit4::class)
class HomeTabBottomSpacingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val tabsKey = context.getString(R.string.saved_tabs_key)
    private var previousTabs: String? = null

    @Before
    fun saveTabs() {
        previousTabs = preferences.getString(tabsKey, null)
        TabsManager.getManager(context).saveTabs(
            (0 until ITEM_COUNT).map { Tab.ChannelTab(0, "https://www.youtube.com/channel/spacing$it", "Spacing channel $it") }
        )
    }

    @After
    fun restoreTabs() {
        preferences.edit().apply {
            if (previousTabs == null) remove(tabsKey) else putString(tabsKey, previousTabs)
        }.commit()
    }

    @Test
    fun lastHomeSectionRemainsAboveAddButtonAndItsRemoveActionWorks() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.supportFragmentManager.beginTransaction()
                    .replace(R.id.settings_fragment_holder, ChooseTabsFragment()).commitNow()
                // A real scroll collapses Settings' app bar. A programmatic jump does not,
                // leaving the scrolling-behavior child partly below the physical window.
                activity.findViewById<AppBarLayout>(R.id.settings_app_bar).setExpanded(false, false)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.selectedTabs)
                assertEquals(ITEM_COUNT, (list.adapter as ConcatAdapter).adapters.last().itemCount)
                list.scrollToPosition(list.adapter!!.itemCount - 1)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val root = activity.supportFragmentManager.findFragmentById(R.id.settings_fragment_holder)!!.requireView() as ViewGroup
                val list = root.findViewById<RecyclerView>(R.id.selectedTabs)
                val row = list.findViewHolderForAdapterPosition(list.adapter!!.itemCount - 1)!!.itemView
                assertEquals("YouTube/Spacing channel ${ITEM_COUNT - 1}", row.findViewById<TextView>(R.id.tabName).text.toString())
                assertClearOfAddButton(root, row)
                val remove = row.findViewById<View>(R.id.remove)
                assertFullyVisibleInWindow(remove)
                tap(root, remove)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.selectedTabs)
                assertEquals(ITEM_COUNT - 1, (list.adapter as ConcatAdapter).adapters.last().itemCount)
                val lastRow = list.findViewHolderForAdapterPosition(list.adapter!!.itemCount - 1)!!.itemView
                assertEquals("YouTube/Spacing channel ${ITEM_COUNT - 2}", lastRow.findViewById<TextView>(R.id.tabName).text.toString())
            }
        }
    }

    @Test
    fun compactLargeTextAndRtlLayoutsKeepBothLastRowActionsTappable() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            listOf(false, true).forEach { rtl ->
                listOf(320 to 420, 640 to 260).forEach { (width, height) ->
                    var removed = false
                    var moved = false
                    var added = false
                    lateinit var root: ViewGroup
                    lateinit var themed: Context
                    scenario.onActivity { activity ->
                        val fragment = GeometryHostFragment()
                        activity.supportFragmentManager.beginTransaction().replace(R.id.settings_fragment_holder, fragment).commitNow()
                        val host = fragment.requireView() as FrameLayout
                        val configuration = Configuration(context.resources.configuration).apply {
                            fontScale = 1.8f
                            screenWidthDp = width
                            screenHeightDp = height
                            orientation = if (width > height) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
                            setLayoutDirection(if (rtl) Locale.forLanguageTag("ar") else Locale.ENGLISH)
                        }
                        themed = ContextThemeWrapper(context.createConfigurationContext(configuration), R.style.LightTheme)
                        root = LayoutInflater.from(themed).inflate(R.layout.fragment_choose_tabs, host, false) as ViewGroup
                        root.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                        val density = themed.resources.displayMetrics.density
                        host.addView(root, FrameLayout.LayoutParams((width * density).toInt(), (height * density).toInt()))
                        assertTrue("Dispatch into an attached view so Android can deliver posted click work", root.isAttachedToWindow)
                        val list = root.findViewById<RecyclerView>(R.id.selectedTabs)
                        list.layoutManager = LinearLayoutManager(themed)
                        root.findViewById<View>(R.id.addTabsButton).setOnClickListener { added = true }
                        val rows = object : RecyclerView.Adapter<Row>() {
                            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Row = Row(LayoutInflater.from(parent.context).inflate(R.layout.list_choose_tabs, parent, false))
                            override fun getItemCount() = ITEM_COUNT
                            override fun onBindViewHolder(holder: Row, position: Int) {
                                holder.itemView.findViewById<TextView>(R.id.tabName).text = "Home section $position with a long name"
                                holder.itemView.findViewById<View>(R.id.remove).setOnClickListener { removed = true }
                                holder.itemView.findViewById<View>(R.id.handle).setOnClickListener { moved = true }
                            }
                        }
                        list.adapter = ConcatAdapter(HomeTabsHelpAdapter(), rows)
                    }
                    instrumentation.waitForIdleSync()
                    scenario.onActivity {
                        val list = root.findViewById<RecyclerView>(R.id.selectedTabs)
                        layout(root, themed, width, height)
                        list.scrollToPosition(list.adapter!!.itemCount - 1)
                        layout(root, themed, width, height)
                        val row = list.findViewHolderForAdapterPosition(list.adapter!!.itemCount - 1)!!.itemView
                        assertClearOfAddButton(root, row)
                        tap(root, row.findViewById(R.id.remove))
                        tap(root, row.findViewById(R.id.handle))
                    }
                    instrumentation.waitForIdleSync()
                    scenario.onActivity {
                        assertTrue(removed)
                        assertTrue(moved)
                        assertFalse("Last-row taps must not open the Add chooser", added)
                    }
                }
            }
        }
    }

    class GeometryHostFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = FrameLayout(requireContext())
    }

    private fun assertClearOfAddButton(root: ViewGroup, row: View) {
        val addBounds = bounds(root, root.findViewById(R.id.addTabsButton))
        val viewport = bounds(root, root.findViewById(R.id.selectedTabs))
        listOf(R.id.remove, R.id.handle).forEach { id ->
            val control = row.findViewById<View>(id)
            val controlBounds = bounds(root, control)
            assertEquals(View.VISIBLE, control.visibility)
            assertTrue(controlBounds.width() > 0 && controlBounds.height() > 0)
            assertTrue("The complete last-row action must be above the Add button", controlBounds.bottom <= addBounds.top)
            assertTrue(controlBounds.top >= 0 && controlBounds.bottom <= root.height)
            assertTrue("Action $controlBounds must be inside the list touch viewport $viewport at ${root.width}×${root.height}", viewport.contains(controlBounds))
            assertTrue(controlBounds.left >= 0 && controlBounds.right <= root.width)
            assertFalse(Rect.intersects(addBounds, controlBounds))
        }
    }

    private fun assertFullyVisibleInWindow(view: View) {
        val visible = Rect()
        assertTrue(view.getLocalVisibleRect(visible))
        val complete = Rect(0, 0, view.width, view.height)
        assertTrue("The complete touch target $complete must be visible in the app window $visible", visible.contains(complete))
    }

    private fun bounds(root: ViewGroup, child: View) = Rect().also {
        child.getDrawingRect(it)
        root.offsetDescendantRectToMyCoords(child, it)
    }

    private fun tap(root: ViewGroup, child: View) {
        val rect = bounds(root, child)
        val now = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            val event = MotionEvent.obtain(now, now + if (action == MotionEvent.ACTION_UP) 30 else 0, action, rect.exactCenterX(), rect.exactCenterY(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                assertTrue("Action $action at $rect must reach ${child.resources.getResourceEntryName(child.id)} inside ${root.width}×${root.height}", root.dispatchTouchEvent(event))
            } finally {
                event.recycle()
            }
        }
    }

    private fun layout(root: View, context: Context, width: Int, height: Int) {
        val density = context.resources.displayMetrics.density
        root.measure(
            View.MeasureSpec.makeMeasureSpec((width * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((height * density).toInt(), View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    private class Row(view: View) : RecyclerView.ViewHolder(view)

    companion object {
        private const val ITEM_COUNT = 24
    }
}
