/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.settings.tabs

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.tabs.TabLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.util.TextTabLabelBinder

@RunWith(AndroidJUnit4::class)
class TextTabLabelReuseTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun recycledHomeTabsShowChannelLabelsAcrossRecreationAndKeepHomeIcons() {
        instrumentation.runOnMainSync {
            listOf(false, true).forEach { rtl ->
                val config = Configuration(instrumentation.targetContext.resources.configuration).apply {
                    fontScale = if (rtl) 1.5f else 1f
                    setLayoutDirection(if (rtl) java.util.Locale("ar") else java.util.Locale.ENGLISH)
                }
                val context = ContextThemeWrapper(instrumentation.targetContext.createConfigurationContext(config), R.style.LightTheme)
                val guard = drainPool(context)
                val home = TabLayout(context)
                val homeTabs = seedHome(home)
                home.removeAllTabs()
                try {
                    repeat(2) {
                        val root = LayoutInflater.from(context).inflate(R.layout.fragment_channel, FrameLayout(context), false)
                        val channel = root.findViewById<TabLayout>(R.id.tab_layout)
                        val titles = listOf(R.string.channel_tab_videos, R.string.channel_tab_shorts, R.string.channel_tab_livestreams, R.string.channel_tab_playlists)
                        val tabs = titles.map { title ->
                            channel.newTab().also { tab ->
                                assertTrue("Exercise real tabs returned by Material's shared pool", homeTabs.any { it === tab })
                                TextTabLabelBinder.bind(tab, context.getString(title))
                                channel.addTab(tab)
                            }
                        }
                        layout(channel, context)
                        tabs.forEachIndexed { index, tab ->
                            assertVisibleText(tab)
                            tab.view.performClick()
                            assertEquals(index, channel.selectedTabPosition)
                        }
                        releaseLabeled(channel)
                    }

                    homeTabs.indices.forEach { index ->
                        home.addTab(home.newTab().also { HomeTabTooltipBinder.bind(it, "Home $index", R.drawable.ic_tv) })
                    }
                    layout(home, context)
                    (0 until home.tabCount).forEach { index ->
                        val tab = home.getTabAt(index)!!
                        assertEquals(TabLayout.TAB_LABEL_VISIBILITY_UNLABELED, tab.tabLabelVisibility)
                        assertEquals("Home $index", tab.contentDescription.toString())
                        assertTrue(tab.icon != null)
                        assertFalse(textViews(tab.view).any { it.visibility == View.VISIBLE && it.text == tab.text })
                    }
                } finally {
                    releaseLabeled(home)
                    releaseLabeled(guard)
                }
            }
        }
    }

    @Test
    fun aboutActivityRestoresPooledTextLabelsAndRetainsThemAfterRecreation() {
        lateinit var guard: TabLayout
        lateinit var recycled: List<TabLayout.Tab>
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.LightTheme)
            guard = drainPool(context)
            val home = TabLayout(context)
            recycled = seedHome(home)
            home.removeAllTabs()
        }
        try {
            ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val tabs = activity.findViewById<TabLayout>(R.id.about_tabLayout)
                    assertTrue((0 until tabs.tabCount).any { index -> recycled.any { it === tabs.getTabAt(index) } })
                    layout(tabs, activity)
                    (0 until tabs.tabCount).forEach { assertVisibleText(tabs.getTabAt(it)!!) }
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    val tabs = activity.findViewById<TabLayout>(R.id.about_tabLayout)
                    layout(tabs, activity)
                    (0 until tabs.tabCount).forEach { assertVisibleText(tabs.getTabAt(it)!!) }
                }
            }
        } finally {
            instrumentation.runOnMainSync { releaseLabeled(guard) }
        }
    }

    private fun drainPool(context: Context) = TabLayout(context).apply {
        // Material's shared pool holds at most 16 tabs. Keep any earlier test's objects in use
        // so the next four tabs are known to be the Home objects we explicitly recycle.
        repeat(16) { addTab(newTab().also { TextTabLabelBinder.bind(it, "Guard") }) }
    }

    private fun seedHome(home: TabLayout): List<TabLayout.Tab> = List(4) { index ->
        home.newTab().also {
            HomeTabTooltipBinder.bind(it, "Home $index", R.drawable.ic_tv)
            home.addTab(it)
        }
    }

    private fun releaseLabeled(tabs: TabLayout) {
        (0 until tabs.tabCount).forEach { TextTabLabelBinder.bind(tabs.getTabAt(it)!!, "Released") }
        tabs.removeAllTabs()
    }

    private fun assertVisibleText(tab: TabLayout.Tab) {
        assertEquals(TabLayout.TAB_LABEL_VISIBILITY_LABELED, tab.tabLabelVisibility)
        assertTrue(tab.text!!.isNotBlank())
        assertEquals(tab.text, tab.contentDescription)
        val rendered = textViews(tab.view).single { it.text.toString() == tab.text.toString() }
        assertEquals(View.VISIBLE, rendered.visibility)
        assertTrue(rendered.width > 0 && rendered.height > 0)
        assertTrue(rendered.layout != null && rendered.layout.lineCount > 0)
    }

    private fun textViews(root: View): List<TextView> = when (root) {
        is TextView -> listOf(root)
        is ViewGroup -> (0 until root.childCount).flatMap { textViews(root.getChildAt(it)) }
        else -> emptyList()
    }

    private fun layout(view: View, context: Context) {
        val width = (360 * context.resources.displayMetrics.density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }
}
