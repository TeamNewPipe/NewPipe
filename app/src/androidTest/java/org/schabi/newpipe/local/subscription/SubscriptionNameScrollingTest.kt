/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.subscription

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.text.Layout
import android.text.TextUtils
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.os.ConfigurationCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xwray.groupie.GroupieViewHolder
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.local.subscription.item.ChannelItem
import org.schabi.newpipe.settings.AppearanceSettingsFragment
import org.schabi.newpipe.settings.SettingsActivity
import org.schabi.newpipe.util.OnClickGesture

@RunWith(AndroidJUnit4::class)
class SubscriptionNameScrollingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val key = context.getString(R.string.scroll_subscription_names_key)
    private var previous: Boolean? = null

    @Before
    fun savePreference() {
        previous = if (preferences.contains(key)) preferences.getBoolean(key, false) else null
        preferences.edit().remove(key).commit()
    }

    @After
    fun restorePreference() {
        preferences.edit().apply {
            previous?.let { putBoolean(key, it) } ?: remove(key)
        }.commit()
    }

    @Test
    fun appearanceSwitchStartsOffAndPersistsOnlyTheSelectedChoice() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = AppearanceSettingsFragment()
                activity.supportFragmentManager.beginTransaction().replace(R.id.settings_fragment_holder, fragment).commitNow()
                assertFalse(fragment.findPreference<SwitchPreferenceCompat>(key)!!.isChecked)
                fragment.scrollToPreference(key)
            }
            instrumentation.waitForIdleSync()
            onView(withText(R.string.scroll_subscription_names_title)).perform(click())
            assertTrue(preferences.getBoolean(key, false))
            scenario.recreate()
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.settings_fragment_holder) as AppearanceSettingsFragment
                assertTrue(fragment.findPreference<SwitchPreferenceCompat>(key)!!.isChecked)
                fragment.scrollToPreference(key)
            }
            instrumentation.waitForIdleSync()
            onView(withText(R.string.scroll_subscription_names_title)).perform(click())
            assertFalse(preferences.getBoolean(key, true))
        }
    }

    @Test
    fun scrollingStaysWithinSubscriptionRowsAndPreservesTextFocusAndGestures() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = emptyHost(activity)
                ChannelItem.ItemVersion.entries.forEach { version ->
                    listOf(false, true).forEach { rtl ->
                        preferences.edit().putBoolean(key, false).commit()
                        val fixture = fixture(host, version, rtl, if (rtl) 1.8f else 1f)
                        try {
                            assertEquals(TextUtils.TruncateAt.END, fixture.title.ellipsize)
                            assertFalse(fixture.title.isSelected)
                            fixture.row.isFocusableInTouchMode = true
                            assertTrue(fixture.row.requestFocus())
                            val labelFocusable = fixture.title.isFocusable
                            var selected: ChannelInfoItem? = null
                            var held: ChannelInfoItem? = null
                            fixture.item.gesturesListener = object : OnClickGesture<ChannelInfoItem> {
                                override fun selected(selectedItem: ChannelInfoItem) {
                                    selected = selectedItem
                                }
                                override fun held(selectedItem: ChannelInfoItem) {
                                    held = selectedItem
                                }
                            }
                            fixture.item.bind(fixture.holder, 0)
                            preferences.edit().putBoolean(key, true).commit()
                            fixture.layout()

                            assertScrolling(fixture)
                            assertEquals(labelFocusable, fixture.title.isFocusable)
                            assertTrue("Marquee must not move focus off the channel row", fixture.row.hasFocus())
                            assertEquals(fixture.info.name, fixture.title.text.toString())
                            val node = AccessibilityNodeInfo.obtain()
                            try {
                                fixture.title.onInitializeAccessibilityNodeInfo(node)
                                assertEquals(fixture.info.name, node.text.toString())
                            } finally {
                                node.recycle()
                            }
                            assertTrue(fixture.row.performClick())
                            assertTrue(fixture.row.performLongClick())
                            assertSame(fixture.info, selected)
                            assertSame(fixture.info, held)

                            // Shared channel XML is also used outside subscriptions. Merely
                            // inflating that layout must not turn on scrolling there.
                            val other = LayoutInflater.from(fixture.row.context).inflate(R.layout.list_channel_mini_item, host, false)
                            other.layoutDirection = fixture.row.layoutDirection
                            val otherTitle = other.findViewById<TextView>(R.id.itemTitleView)
                            otherTitle.text = fixture.info.name
                            host.addView(other, FrameLayout.LayoutParams(fixture.width, ViewGroup.LayoutParams.WRAP_CONTENT))
                            measure(other, fixture.width)
                            assertTrue("The shared mini layout needs usable title width outside subscriptions: ${fixture.diagnostic}", otherTitle.width - otherTitle.compoundPaddingLeft - otherTitle.compoundPaddingRight > 0)
                            assertEquals(TextUtils.TruncateAt.END, otherTitle.ellipsize)
                            assertFalse(otherTitle.isSelected)
                            host.removeView(other)

                            preferences.edit().putBoolean(key, false).commit()
                            fixture.layout()
                            assertEquals(TextUtils.TruncateAt.END, fixture.title.ellipsize)
                            assertFalse(fixture.title.isSelected)
                        } finally {
                            fixture.item.unbind(fixture.holder)
                            host.removeView(fixture.row)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun recyclingAndDetachingStopOldNamesAndReattachmentReadsCurrentPreference() {
        preferences.edit().putBoolean(key, true).commit()
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = emptyHost(activity)
                val fixture = fixture(host, ChannelItem.ItemVersion.MINI)
                try {
                    assertScrolling(fixture)
                    val short = ChannelItem(ChannelInfoItem(0, "https://example.org/short", "Short"), 2, ChannelItem.ItemVersion.MINI)
                    short.bind(fixture.holder, 1)
                    fixture.layout()
                    assertEquals("Short", fixture.title.text.toString())
                    assertFalse("A recycled short name must not keep scrolling", fixture.title.isSelected)

                    fixture.item.bind(fixture.holder, 0)
                    fixture.layout()
                    assertScrolling(fixture)
                    host.removeView(fixture.row)
                    assertFalse(fixture.title.isSelected)
                    preferences.edit().putBoolean(key, false).commit()
                    host.addView(fixture.row)
                    fixture.layout()
                    assertEquals(TextUtils.TruncateAt.END, fixture.title.ellipsize)
                    assertFalse(fixture.title.isSelected)

                    preferences.edit().putBoolean(key, true).commit()
                    fixture.layout()
                    assertScrolling(fixture)
                    host.removeView(fixture.row)
                    assertFalse(fixture.title.isSelected)
                    host.addView(fixture.row)
                    fixture.layout()
                    assertScrolling(fixture)

                    fixture.item.unbind(fixture.holder)
                    assertFalse(fixture.title.isSelected)
                    assertEquals(TextUtils.TruncateAt.END, fixture.title.ellipsize)
                    preferences.edit().putBoolean(key, false).commit()
                    preferences.edit().putBoolean(key, true).commit()
                    fixture.layout()
                    assertFalse("An unbound view must no longer observe the preference", fixture.title.isSelected)
                    assertEquals(TextUtils.TruncateAt.END, fixture.title.ellipsize)
                } finally {
                    SubscriptionNameScrolling.unbind(fixture.title)
                    host.removeView(fixture.row)
                }
            }
        }
    }

    private fun emptyHost(activity: SettingsActivity): FrameLayout {
        val fragment = NameHostFragment()
        activity.supportFragmentManager.beginTransaction().replace(R.id.settings_fragment_holder, fragment).commitNow()
        return fragment.requireView() as FrameLayout
    }

    class NameHostFragment : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = FrameLayout(requireContext())
    }

    private fun fixture(host: FrameLayout, version: ChannelItem.ItemVersion, rtl: Boolean = false, fontScale: Float = 1f): Fixture {
        val configuration = Configuration(host.resources.configuration).apply {
            this.fontScale = fontScale
            val locale = if (rtl) Locale("ar") else Locale.ENGLISH
            setLocale(locale)
            setLayoutDirection(locale)
        }
        val themed = ContextThemeWrapper(host.context.createConfigurationContext(configuration), R.style.LightTheme)
        val name = if (rtl) "قناة ذات اسم طويل لعرض التمرير ".repeat(6) else "A subscription channel with a very long display name ".repeat(6)
        val info = ChannelInfoItem(0, "https://example.org/scrolling", name)
        val item = ChannelItem(info, 1, version)
        val row = LayoutInflater.from(themed).inflate(item.layout, host, false)
        row.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        val holder = GroupieViewHolder(row)
        item.bind(holder, 0)
        val width = ((if (version == ChannelItem.ItemVersion.GRID) 160 else 320) * themed.resources.displayMetrics.density).toInt()
        host.addView(row, FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT))
        return Fixture(info, item, row, holder, width).also {
            it.layout()
            assertUsableTitleBounds(it)
        }
    }

    private fun assertScrolling(fixture: Fixture) {
        assertUsableTitleBounds(fixture)
        val diagnostic = fixture.diagnostic
        val layout = requireNotNull(fixture.title.layout) { diagnostic }
        assertEquals(diagnostic, TextUtils.TruncateAt.MARQUEE, fixture.title.ellipsize)
        assertTrue("An attached overflowing name must start scrolling: $diagnostic", fixture.title.isSelected)
        assertEquals(diagnostic, 1, layout.lineCount)
        assertTrue("The fixture must overflow its usable title width: $diagnostic", layout.getLineWidth(0) > fixture.title.width - fixture.title.compoundPaddingLeft - fixture.title.compoundPaddingRight)
        assertTrue("The scrolling label must stay within its row/card: $diagnostic", fixture.title.width <= fixture.row.width)
    }

    private fun assertUsableTitleBounds(fixture: Fixture) {
        val root = fixture.row as ViewGroup
        val diagnostic = fixture.diagnostic
        val direction = root.resources.configuration.layoutDirection
        assertEquals(diagnostic, direction, root.layoutDirection)
        assertEquals(diagnostic, direction, fixture.title.layoutDirection)
        val usableWidth = fixture.title.width - fixture.title.compoundPaddingLeft - fixture.title.compoundPaddingRight
        assertTrue("A channel name needs positive usable width: $diagnostic", usableWidth > 0)
        assertTrue("A channel name needs positive usable height: $diagnostic", fixture.title.height - fixture.title.compoundPaddingTop - fixture.title.compoundPaddingBottom > 0)
        val rowBounds = Rect(0, 0, root.width, root.height)
        assertTrue("The complete title viewport must stay inside the row: $diagnostic", rowBounds.contains(bounds(root, fixture.title)))
        if (fixture.item.itemVersion == ChannelItem.ItemVersion.MINI) {
            val thumbnail = bounds(root, root.findViewById(R.id.itemThumbnailView))
            val details = root.findViewById<TextView>(R.id.itemAdditionalDetails)
            assertTrue("Mini-row details need positive usable width: $diagnostic", details.width - details.compoundPaddingLeft - details.compoundPaddingRight > 0)
            for (label in listOf(fixture.title, details)) {
                val rectangle = bounds(root, label)
                assertTrue("Mini-row labels must stay inside the row: $diagnostic", rowBounds.contains(rectangle))
                assertTrue(
                    "Mini-row labels belong beyond the thumbnail at logical end: $diagnostic",
                    if (direction == View.LAYOUT_DIRECTION_RTL) rectangle.right <= thumbnail.left else rectangle.left >= thumbnail.right
                )
            }
        }
    }

    private data class Fixture(val info: ChannelInfoItem, val item: ChannelItem, val row: View, val holder: GroupieViewHolder, val width: Int) {
        val title: TextView = row.findViewById(R.id.itemTitleView)
        val diagnostic: String
            get() = "version=${item.itemVersion} locale=${ConfigurationCompat.getLocales(row.resources.configuration)[0]} " +
                "direction=${row.layoutDirection} fontScale=${row.resources.configuration.fontScale} row=${row.width}x${row.height} " +
                "title=(${title.left},${title.top})-(${title.right},${title.bottom}) " +
                "usableWidth=${title.width - title.compoundPaddingLeft - title.compoundPaddingRight} " +
                "textWidth=${Layout.getDesiredWidth(title.text, title.paint)} attached=${title.isAttachedToWindow} selected=${title.isSelected}"
        fun layout() = measure(row, width)
    }

    companion object {
        private fun bounds(root: ViewGroup, view: View) = Rect().apply {
            view.getDrawingRect(this)
            root.offsetDescendantRectToMyCoords(view, this)
        }

        private fun measure(view: View, width: Int) {
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        }
    }
}
