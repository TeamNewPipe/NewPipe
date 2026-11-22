/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.feed

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.appcompat.widget.Toolbar
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt
import org.hamcrest.Matcher
import org.hamcrest.Matchers.anything
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.databinding.FragmentFeedBinding
import org.schabi.newpipe.settings.SettingsActivity
import org.schabi.newpipe.testUtil.TestDatabase
import org.schabi.newpipe.util.StreamFilterControls

@RunWith(AndroidJUnit4::class)
class FeedFilterControlsLayoutTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun contentWatchAndDownloadRowsRemainInsideTheCompleteHeader() {
        instrumentation.runOnMainSync {
            cases().forEach { case ->
                val fixture = createFeed(case)
                measure(fixture)
                assertFilterRowsFit(fixture)
                if (case.name == "phone" || case.name == "rtl-large") {
                    savePreview(fixture, case.name)
                }
            }
        }
    }

    @Test
    fun refreshProgressUsesACompactRowAndFiltersRestoreTheirHeight() {
        instrumentation.runOnMainSync {
            cases().forEach { case ->
                val fixture = createFeed(case)
                val binding = fixture.binding
                measure(fixture)
                assertFilterRowsFit(fixture)
                val filtersHeight = binding.feedControlsRow.height

                binding.streamFilterChips.root.visibility = View.GONE
                binding.refreshProgressContainer.visibility = View.VISIBLE
                binding.cancelRefreshButton.visibility = View.VISIBLE
                binding.loadingProgressText.text = "38 / 100"
                binding.loadingChannelText.text = "A channel currently being refreshed"
                binding.feedRefreshProgressBar.max = 100
                binding.feedRefreshProgressBar.progress = 38
                measure(fixture)

                assertContained(binding.feedControlsRow, binding.refreshProgressContainer)
                assertContained(binding.feedControlsRow, binding.cancelRefreshButton)
                assertContained(binding.feedHeader, binding.feedControlsRow)
                assertTextFits(binding.loadingProgressText)
                assertTextFits(binding.loadingChannelText)
                assertTrue("The refresh row must keep its 48dp target", binding.feedControlsRow.height >= dp(binding.root.context, 48))
                assertTrue("Refresh must release the space used by the three filter rows", binding.feedControlsRow.height < filtersHeight)
                assertEquals(
                    "Refresh should only reserve space for its visible controls",
                    maxOf(dp(binding.root.context, 48), binding.refreshProgressContainer.height, binding.cancelRefreshButton.height),
                    binding.feedControlsRow.height
                )

                binding.streamFilterChips.root.visibility = View.VISIBLE
                binding.refreshProgressContainer.visibility = View.GONE
                binding.cancelRefreshButton.visibility = View.GONE
                measure(fixture)
                assertFilterRowsFit(fixture)
                assertEquals("Ending refresh must restore the complete filter header", filtersHeight, binding.feedControlsRow.height)
            }
        }
    }

    @SuppressLint("RestrictedApi")
    @Test(timeout = 60_000)
    fun feedOptionsStayInOverflowAndKeepDateControlsReachableWithoutChangingChipFilters() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val booleanValues = mapOf(
            R.string.feed_show_watched_items_key to false,
            R.string.feed_show_partially_watched_items_key to true,
            R.string.feed_show_future_items_key to false,
            R.string.feed_show_live_items_key to false,
            R.string.feed_show_video_items_key to true,
            R.string.feed_show_shorts_items_key to false,
            R.string.feed_show_members_only_items_key to false,
            R.string.hide_members_only_videos_key to true
        ).mapKeys { context.getString(it.key) }
        val dateKey = context.getString(R.string.feed_date_filter_epoch_day_key)
        val filterKey = FeedFragment.streamFilterPreferenceKey(FeedGroupEntity.GROUP_ALL_ID)
        val keys = booleanValues.keys + dateKey + filterKey
        val savedPreferences = keys.associateWith { preferences.all[it] }
        val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply { isAccessible = true }
        val previousDatabase = databaseField.get(null)
        val database = TestDatabase.createReplacingNewPipeDatabase()
        try {
            preferences.edit().apply {
                booleanValues.forEach { (key, value) -> putBoolean(key, value) }
                putLong(dateKey, LocalDate.of(2026, 11, 15).toEpochDay())
                putString(filterKey, "UNWATCHED,VIDEOS,DOWNLOADED")
            }.commit()
            val configuredPreferences = keys.associateWith { preferences.all[it] }
            ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
                lateinit var toolbar: Toolbar
                scenario.onActivity { activity ->
                    val fragment = FeedFragment()
                    activity.supportFragmentManager.beginTransaction()
                        .replace(R.id.settings_fragment_holder, fragment).commitNow()
                    toolbar = Toolbar(activity)
                    toolbar.inflateMenu(R.menu.menu_feed_fragment)
                    val options = toolbar.menu.findItem(R.id.menu_item_feed_toggle_played_items) as MenuItemImpl
                    assertEquals(activity.getString(R.string.feed_options_title), options.title.toString())
                    assertFalse("Feed options must not request toolbar space", options.requestsActionButton())
                    assertFalse("Feed options must not require a toolbar action", options.requiresActionButton())
                    assertNull("The old eye icon must not remain on Feed options", options.icon)
                    toolbar.setOnMenuItemClickListener(fragment::onOptionsItemSelected)
                    toolbar.menu.performIdentifierAction(options.itemId, 0)
                }
                onView(withText(R.string.feed_default_visibility_title)).inRoot(isDialog()).check(matches(isDisplayed()))
                var footerPosition = -1
                onView(isAssignableFrom(ListView::class.java)).inRoot(isDialog()).check { view, error ->
                    if (error != null) throw error
                    val choices = view as ListView
                    assertEquals("Date controls must share the list's scrolling surface", 1, choices.footerViewsCount)
                    footerPosition = choices.adapter.count - 1
                    assertNull("The footer must not act as a visibility choice", choices.adapter.getItem(footerPosition))
                    assertFalse(choices.adapter.isEnabled(footerPosition))
                    val titles = (0 until choices.adapter.count).mapNotNull { choices.adapter.getItem(it)?.toString() }
                    listOf(
                        R.string.feed_show_watched,
                        R.string.feed_show_partially_watched,
                        R.string.feed_show_upcoming,
                        R.string.channel_tab_livestreams,
                        R.string.videos_string,
                        R.string.channel_tab_shorts,
                        R.string.members_only
                    ).forEach { title ->
                        assertTrue("Existing visibility choice must remain available: ${context.getString(title)}", context.getString(title) in titles)
                    }
                    assertFalse(choices.isItemChecked(titles.indexOf(context.getString(R.string.feed_show_watched))))
                    assertTrue(choices.isItemChecked(titles.indexOf(context.getString(R.string.feed_show_partially_watched))))
                    assertFalse(choices.isItemChecked(titles.indexOf(context.getString(R.string.members_only))))
                }
                footerChild(footerPosition, context.getString(R.string.feed_default_visibility_help, context.getString(R.string.filter_all_visible)))
                    .check(matches(isDisplayed()))
                clearDateFromFooter(footerPosition)
                onView(withText(R.string.cancel)).inRoot(isDialog()).perform(click())
                assertEquals("Cancelling a date edit must retain saved filters and limits", configuredPreferences, keys.associateWith { preferences.all[it] })

                scenario.onActivity {
                    toolbar.menu.performIdentifierAction(R.id.menu_item_feed_toggle_played_items, 0)
                }
                onView(withText(R.string.feed_default_visibility_title)).inRoot(isDialog()).check(matches(isDisplayed()))
                clearDateFromFooter(footerPosition)
                onView(withText(R.string.ok)).inRoot(isDialog()).perform(click())
                assertFalse("Confirming Clear must remove the publication cutoff", preferences.contains(dateKey))
                assertEquals("Changing the cutoff must retain the selected chip filters", configuredPreferences[filterKey], preferences.getString(filterKey, null))
                assertEquals("The footer must not alter other visibility preferences", configuredPreferences + (dateKey to null), keys.associateWith { preferences.all[it] })
            }
        } finally {
            databaseField.set(null, previousDatabase)
            database.close()
            preferences.edit().apply {
                savedPreferences.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> putBoolean(key, value)
                        is Long -> putLong(key, value)
                        is String -> putString(key, value)
                        else -> remove(key)
                    }
                }
            }.commit()
        }
    }

    private fun clearDateFromFooter(position: Int) {
        footerChild(position, context.getString(R.string.feed_date_filter_clear))
            .perform(scrollFooterChildIntoView())
            .check(matches(isDisplayed()))
            .check { view, error ->
                if (error != null) throw error
                assertTrue("The reachable Clear button must retain its measured height", view.height > 0)
            }
            .perform(click())
        footerChild(position, context.getString(R.string.feed_date_filter))
            .check(matches(isDisplayed()))
            .check { view, error ->
                if (error != null) throw error
                assertTrue("The date picker must remain available after clearing the cutoff", view.height > 0)
            }
    }

    private fun scrollFooterChildIntoView() = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isDescendantOfA(isAssignableFrom(ListView::class.java))

        override fun getDescription() = "scroll the footer control into view without flinging"

        override fun perform(uiController: UiController, view: View) {
            // Loading a footer can leave its last button clipped. A fast swipe can leave Android's
            // fling or edge stretch consuming the next tap, so reveal the control synchronously.
            val bounds = Rect()
            view.getDrawingRect(bounds)
            view.requestRectangleOnScreen(bounds, true)
            uiController.loopMainThreadUntilIdle()
        }
    }

    private fun footerChild(position: Int, text: String) = onData(anything())
        .inRoot(isDialog())
        .inAdapterView(isAssignableFrom(ListView::class.java))
        .atPosition(position)
        .onChildView(withText(text))

    @After
    fun preservePreviews() {
        val files = File(context.getExternalFilesDir(null), "player-layout-previews")
            .listFiles().orEmpty().filter { it.name.startsWith(PREVIEW_PREFIX) }
        if (files.isEmpty()) return
        val destination = "/sdcard/Download/wizestream-player-layout-previews"
        val commands = listOf("mkdir -p $destination") + files.map { "cp ${it.absolutePath} $destination/" }
        val automation = instrumentation.uiAutomation
        commands.forEach { command ->
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }
    }

    private fun createFeed(case: Case): Fixture {
        val configuration = Configuration(context.resources.configuration).apply {
            val locale = Locale.forLanguageTag(case.language)
            setLocale(locale)
            setLayoutDirection(locale)
            fontScale = case.fontScale
            screenWidthDp = case.widthDp
            screenHeightDp = case.heightDp
            smallestScreenWidthDp = minOf(case.widthDp, case.heightDp)
            orientation = if (case.widthDp > case.heightDp) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        }
        val configuredContext = context.createConfigurationContext(configuration)
        val palette = object : ContextThemeWrapper(configuredContext, R.style.LightTheme) {
            override fun getApplicationInfo(): ApplicationInfo = ApplicationInfo(super.getApplicationInfo()).apply {
                flags = flags or ApplicationInfo.FLAG_SUPPORTS_RTL
            }
        }
        val binding = FragmentFeedBinding.inflate(LayoutInflater.from(palette), FrameLayout(palette), false)
        binding.root.layoutDirection = configuration.layoutDirection
        binding.feedControlsRow.layoutDirection = configuration.layoutDirection
        binding.streamFilterChips.root.layoutDirection = configuration.layoutDirection
        binding.refreshRootView.visibility = View.VISIBLE
        binding.refreshText.text = "Feed last updated: moments ago"
        binding.refreshSubtitleText.visibility = View.GONE
        binding.itemsList.visibility = View.VISIBLE
        StreamFilterControls.bind(binding.streamFilterChips, "", true) { }
        binding.streamFilterChips.filterAll.setText(R.string.filter_all_visible)
        binding.streamFilterChips.filterWatchAll.setText(R.string.filter_all_visible)
        return Fixture(binding, dp(palette, case.widthDp), dp(palette, case.heightDp))
    }

    private fun assertFilterRowsFit(fixture: Fixture) {
        val binding = fixture.binding
        val filters = binding.streamFilterChips
        val rows = listOf(filters.streamFilterScroll, filters.watchFilterScroll, filters.filterDownloaded.parent as View)
        rows.forEach { row ->
            assertEquals(View.VISIBLE, row.visibility)
            assertTrue("Each filter row must have a visible height", row.height > 0)
            assertContained(binding.feedControlsRow, row)
            assertContained(binding.feedHeader, row)
        }
        val rectangles = rows.map { bounds(binding.feedControlsRow, it) }
        rectangles.zipWithNext().forEach { (above, below) ->
            assertTrue("Content, watch status and downloaded filters must occupy separate rows", above.bottom <= below.top)
        }
        listOf(filters.filterAll, filters.filterWatchAll, filters.filterDownloaded).forEach(::assertTextFits)
        assertContained(binding.feedHeader, binding.feedControlsRow)
        assertContained(binding.root, binding.feedHeader)
        assertTrue("Feed content must start below the complete header", binding.swipeRefreshLayout.top >= binding.feedHeader.bottom)
    }

    private fun assertTextFits(view: TextView) {
        assertTrue("${view.resources.getResourceEntryName(view.id)} must have a laid-out label", view.lineCount > 0)
        val textHeight = view.layout.getLineBottom(view.lineCount - 1)
        assertTrue("${view.resources.getResourceEntryName(view.id)} must not clip its label vertically", textHeight <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
    }

    private fun measure(fixture: Fixture) {
        fixture.binding.root.requestLayout()
        fixture.binding.root.measure(exactly(fixture.width), exactly(fixture.height))
        fixture.binding.root.layout(0, 0, fixture.width, fixture.height)
    }

    private fun assertContained(container: ViewGroup, child: View) {
        val rectangle = bounds(container, child)
        assertTrue("Filter controls must fit inside their measured ancestor: $rectangle in ${container.width}x${container.height}", Rect(0, 0, container.width, container.height).contains(rectangle))
    }

    private fun bounds(container: ViewGroup, child: View): Rect = Rect().also {
        child.getDrawingRect(it)
        container.offsetDescendantRectToMyCoords(child, it)
    }

    private fun savePreview(fixture: Fixture, name: String) {
        val height = minOf(fixture.height, fixture.binding.feedHeader.bottom + dp(fixture.binding.root.context, 16))
        val bitmap = Bitmap.createBitmap(fixture.width, height, Bitmap.Config.ARGB_8888)
        try {
            fixture.binding.root.draw(Canvas(bitmap))
            val folder = File(context.getExternalFilesDir(null), "player-layout-previews").apply { mkdirs() }
            File(folder, "$PREVIEW_PREFIX$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
    }

    private fun cases() = listOf(
        Case("phone", 360),
        Case("phone-large", 360, fontScale = 2f),
        Case("wide", 960, heightDp = 640, fontScale = 1.3f),
        Case("rtl-large", 360, fontScale = 1.5f, language = "ar")
    )

    private fun exactly(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)

    private fun dp(palette: Context, value: Int) = (value * palette.resources.displayMetrics.density).roundToInt()

    private data class Case(val name: String, val widthDp: Int, val heightDp: Int = 800, val fontScale: Float = 1f, val language: String = "en")

    private data class Fixture(val binding: FragmentFeedBinding, val width: Int, val height: Int)

    companion object {
        private const val PREVIEW_PREFIX = "feed-filter-controls-"
    }
}
