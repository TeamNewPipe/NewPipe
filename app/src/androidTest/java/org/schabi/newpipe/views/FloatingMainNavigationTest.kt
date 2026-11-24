/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.views

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager.widget.ViewPager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.navigationrail.NavigationRailView
import com.google.android.material.tabs.TabLayout
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.fragments.MainFragment
import org.schabi.newpipe.fragments.list.search.SearchFragment
import org.schabi.newpipe.local.subscription.SubscriptionFragment
import org.schabi.newpipe.player.gesture.CustomBottomSheetBehavior
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.settings.AppearanceSettingsFragment
import org.schabi.newpipe.settings.SettingsActivity
import org.schabi.newpipe.settings.tabs.Tab
import org.schabi.newpipe.settings.tabs.TabsManager
import org.schabi.newpipe.themes.ThemePack
import org.schabi.newpipe.themes.ThemePacks
import org.schabi.newpipe.util.KEY_MAIN_PAGE_CHANGE
import org.schabi.newpipe.util.KEY_THEME_CHANGE
import org.schabi.newpipe.util.OpenServiceLinkDialog
import org.schabi.newpipe.util.ServiceHelper

/** Exercises the real owners of page, search and player clearance, not a second layout formula. */
class FloatingMainNavigationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val kidsPreferences = context.getSharedPreferences("wizestream_kids_mode", Context.MODE_PRIVATE)
    private lateinit var savedPreferences: Map<String, Any?>
    private var savedKidsProfiles: Set<String>? = null

    @Before
    fun isolateNavigationPreferences() {
        val keys = listOf(
            R.string.bottom_navigation_style_key, R.string.floating_navigation_spacing_key,
            R.string.main_tabs_position_key, R.string.bottom_navigation_labels_key,
            R.string.subscription_actions_style_key, R.string.search_button_behavior_key,
            R.string.current_service_key, R.string.saved_tabs_key, R.string.tablet_mode_key,
            R.string.tablet_navigation_portrait_position_key, R.string.tablet_navigation_landscape_position_key,
            R.string.show_remote_search_suggestions_key, R.string.show_local_search_suggestions_key
        ).map(context::getString) + listOf(
            "home_startup_tab_json",
            "github_star_prompt_completed",
            ThemePacks.KEY,
            KEY_THEME_CHANGE,
            KEY_MAIN_PAGE_CHANGE
        )
        savedPreferences = keys.associateWith { preferences.all[it] }
        savedKidsProfiles = kidsPreferences.getStringSet("profiles", null)?.toSet()
        kidsPreferences.edit().remove("profiles").commit()
        preferences.edit()
            .remove(key(R.string.bottom_navigation_style_key))
            .remove(key(R.string.floating_navigation_spacing_key))
            .remove("home_startup_tab_json")
            .remove(ThemePacks.KEY)
            .putBoolean(KEY_THEME_CHANGE, false)
            .putBoolean(KEY_MAIN_PAGE_CHANGE, false)
            .putBoolean("github_star_prompt_completed", true)
            .putBoolean(key(R.string.main_tabs_position_key), true)
            .putString(key(R.string.tablet_mode_key), key(R.string.tablet_mode_off_key))
            .putString(key(R.string.bottom_navigation_labels_key), key(R.string.bottom_navigation_labels_always_value))
            .putString(key(R.string.subscription_actions_style_key), "classic")
            .putString(key(R.string.search_button_behavior_key), key(R.string.search_button_behavior_current_tab_value))
            .putBoolean(key(R.string.show_remote_search_suggestions_key), false)
            .putBoolean(key(R.string.show_local_search_suggestions_key), false)
            .commit()
        ServiceHelper.setSelectedServiceId(context, ServiceList.YouTube.serviceId)
        TabsManager.getManager(context).saveTabs(listOf(Tab.Type.SUBSCRIPTIONS.tab, Tab.Type.BLANK.tab))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @After
    fun restoreNavigationPreferences() {
        ServiceList.unregister(LINK_SERVICE_ID)
        preferences.edit().apply {
            savedPreferences.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Boolean -> putBoolean(key, value)
                    else -> remove(key)
                }
            }
        }.commit()
        kidsPreferences.edit().apply {
            savedKidsProfiles?.let { putStringSet("profiles", it) } ?: remove("profiles")
        }.commit()
    }

    @Test
    fun appearanceDefaultsToClassicAndSpacingResetRestoresEightDp() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val appearance = AppearanceSettingsFragment()
                activity.supportFragmentManager.beginTransaction()
                    .replace(R.id.settings_fragment_holder, appearance).commitNow()
                val style = appearance.requirePreference<ListPreference>(R.string.bottom_navigation_style_key)
                val spacing = appearance.requirePreference<SeekBarPreference>(R.string.floating_navigation_spacing_key)
                val reset = appearance.requirePreference<Preference>(R.string.floating_navigation_spacing_reset_key)
                val actions = appearance.requirePreference<ListPreference>(R.string.subscription_actions_style_key)
                assertEquals("classic", style.value)
                assertEquals("classic", actions.value)
                assertEquals(8, spacing.value)
                assertEquals(0, spacing.min)
                assertEquals(24, spacing.max)
                assertFalse(spacing.isEnabled)
                assertFalse(reset.isEnabled)
                // ListPreference dispatches its listener before persisting the user's choice.
                assertTrue(style.callChangeListener("floating"))
                style.value = "floating"
                assertTrue(spacing.isEnabled)
                assertTrue(reset.isEnabled)
                spacing.value = 24
                assertEquals(24, preferences.getInt(key(R.string.floating_navigation_spacing_key), -1))
                reset.performClick()
                assertEquals(8, spacing.value)
                assertEquals(8, preferences.getInt(key(R.string.floating_navigation_spacing_key), -1))
                assertTrue(style.callChangeListener("classic"))
                style.value = "classic"
                assertFalse(spacing.isEnabled)
                assertFalse(reset.isEnabled)
            }
        }
    }

    @Test
    fun liveSpacingAndSystemInsetsKeepPageAndMiniPlayerAboveTheSameBar() {
        launchHome().use { scenario ->
            awaitHome(scenario)
            scenario.onActivity { activity ->
                val behavior = BottomSheetBehavior.from(activity.findViewById<View>(R.id.fragment_player_holder))
                behavior.peekHeight = dp(56)
                behavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
            awaitActivity(scenario, "The mini-player did not reach its collapsed position") { activity ->
                BottomSheetBehavior.from(activity.findViewById<View>(R.id.fragment_player_holder)).state == BottomSheetBehavior.STATE_COLLAPSED
            }
            scenario.onActivity { activity ->
                val navigation = activity.findViewById<FloatingBottomNavigationView>(R.id.main_bottom_navigation)
                val background = navigation.background
                val originalOutline = navigation.outlineProvider
                val player = activity.findViewById<View>(R.id.fragment_player_holder)
                val behavior = BottomSheetBehavior.from(player) as CustomBottomSheetBehavior
                val root = activity.findViewById<View>(R.id.main_content).parent as View
                val insetCases = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    listOf(24, 48).map { safeBottom ->
                        WindowInsetsCompat.Builder()
                            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(dp(3), dp(20), dp(5), dp(safeBottom)))
                            .build()
                    }
                } else {
                    // Before native typed insets, dispatch rewraps WindowInsets using the real
                    // root's stable insets to distinguish navigation bars from the IME. Replay
                    // that complete platform snapshot; a synthetic larger bottom is an IME,
                    // not a navigation bar, and compat-only overrides do not survive dispatch.
                    listOf(requireNotNull(ViewCompat.getRootWindowInsets(root)))
                }
                // Classic, default Floating, extremes, and an invalid restored value all use the
                // same actual inset dispatch and MainFragment/BottomSheet layout callbacks.
                for ((style, storedGap, gap) in listOf(
                    Triple("classic", 8, 0),
                    Triple("floating", 8, 8),
                    Triple("floating", 0, 0),
                    Triple("floating", 24, 24),
                    Triple("floating", 200, 24),
                    Triple("classic", 24, 0)
                )) {
                    preferences.edit().putString(key(R.string.bottom_navigation_style_key), style)
                        .putInt(key(R.string.floating_navigation_spacing_key), storedGap).commit()
                    for (direction in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
                        root.layoutDirection = direction
                        for (insets in insetCases) {
                            val safeInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                            repeat(2) { ViewCompat.dispatchApplyWindowInsets(root, insets) }
                            layout(root, 360, 720)
                            val margins = navigation.layoutParams as ViewGroup.MarginLayoutParams
                            assertEquals(safeInsets.bottom + dp(gap), margins.bottomMargin)
                            assertEquals(navigation.height + dp(gap), navigation.occupiedHeight)
                            assertEquals(dp(56 + gap) + navigation.height + safeInsets.bottom, behavior.peekHeight)
                            assertTrue(navigation.left >= safeInsets.left)
                            assertTrue(navigation.right <= root.width - safeInsets.right)
                            assertEquals(2, navigation.menu.size())
                            val pager = activity.findViewById<View>(R.id.pager)
                            val content = activity.findViewById<View>(R.id.fragment_holder)
                            assertEquals(navigation.height + dp(gap), (pager.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin)
                            assertEquals(dp(56), content.paddingBottom)
                            assertEquals(bounds(navigation).top - content.paddingBottom, bounds(pager).bottom)
                            assertEquals("The collapsed player must start exactly below the page", bounds(pager).bottom, bounds(player).top)
                            assertSame("Style changes must preserve the theme/Glass renderer", background, navigation.background)
                            if (style == "floating") assertTrue(navigation.clipToOutline) else assertSame(originalOutline, navigation.outlineProvider)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun floatingGeometrySurvivesRecreationAndExpandedPlayerReturnsToItsOriginalGap() {
        preferences.edit().putString(key(R.string.bottom_navigation_style_key), "floating")
            .putInt(key(R.string.floating_navigation_spacing_key), 18).commit()
        val pack = context.assets.open("themes/glass-record.json").use(ThemePack::read)
        preferences.edit().putString(ThemePacks.KEY, pack.encode()).commit()
        launchHome().use { scenario ->
            awaitHome(scenario)
            scenario.recreate()
            awaitHome(scenario)
            scenario.onActivity { activity ->
                val navigation = activity.findViewById<FloatingBottomNavigationView>(R.id.main_bottom_navigation)
                assertEquals(navigation.height + dp(18), navigation.occupiedHeight)
                assertTrue(navigation.clipToOutline)
                assertEquals("GlassBackdrop", navigation.background.javaClass.simpleName)
                val behavior = BottomSheetBehavior.from(activity.findViewById<View>(R.id.fragment_player_holder))
                behavior.peekHeight = dp(56)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
            awaitActivity(scenario, "Expanded player must hide the floating navigation") { activity ->
                activity.findViewById<View>(R.id.main_bottom_navigation).visibility == View.INVISIBLE
            }
            scenario.onActivity { activity ->
                BottomSheetBehavior.from(activity.findViewById<View>(R.id.fragment_player_holder)).state = BottomSheetBehavior.STATE_HIDDEN
            }
            awaitActivity(scenario, "Dismissing the player must restore floating navigation") { activity ->
                activity.findViewById<View>(R.id.main_bottom_navigation).visibility == View.VISIBLE
            }
            scenario.onActivity { activity ->
                val navigation = activity.findViewById<FloatingBottomNavigationView>(R.id.main_bottom_navigation)
                assertEquals(0f, navigation.translationY, 0f)
                assertEquals(navigation.height + dp(18), navigation.occupiedHeight)
                assertEquals(navigation.occupiedHeight, (activity.findViewById<View>(R.id.pager).layoutParams as ViewGroup.MarginLayoutParams).bottomMargin)
            }
        }
    }

    @Test
    fun topTabsLargeTabListsAndTabletRailRetainTheirNavigationModes() {
        preferences.edit().putString(key(R.string.bottom_navigation_style_key), "floating")
            .putBoolean(key(R.string.main_tabs_position_key), false).commit()
        launchHome().use { scenario ->
            awaitHome(scenario, bottomBar = false)
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<TabLayout>(R.id.main_tab_layout).isShown)
                assertEquals(0, (activity.findViewById<View>(R.id.pager).layoutParams as ViewGroup.MarginLayoutParams).bottomMargin)
            }
            val tabs = listOf(Tab.Type.SUBSCRIPTIONS, Tab.Type.BLANK, Tab.Type.BOOKMARKS, Tab.Type.HISTORY, Tab.Type.FEED, Tab.Type.DOWNLOADS)
            scenario.onActivity {
                preferences.edit().putBoolean(key(R.string.main_tabs_position_key), true).commit()
                TabsManager.getManager(context).saveTabs(tabs.map { it.tab })
            }
            scenario.recreate()
            awaitActivity(scenario, "Six destinations must stay in the scrollable tab layout") { activity ->
                activity.findViewById<TabLayout>(R.id.main_tab_layout)?.let { it.isShown && it.tabCount == 6 } == true
            }
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.findViewById<View>(R.id.main_bottom_navigation).visibility)
                assertEquals(6, activity.findViewById<ViewPager>(R.id.pager).adapter!!.count)
                preferences.edit().putString(key(R.string.tablet_mode_key), key(R.string.tablet_mode_on_key))
                    .putString(key(R.string.tablet_navigation_portrait_position_key), key(R.string.tablet_navigation_position_left_value))
                    .putString(key(R.string.tablet_navigation_landscape_position_key), key(R.string.tablet_navigation_position_left_value)).commit()
            }
            scenario.recreate()
            awaitActivity(scenario, "Tablet navigation must retain its rail and all destinations") { activity ->
                activity.findViewById<NavigationRailView>(R.id.main_navigation_rail)?.let { it.isShown && it.menu.size() == 6 } == true
            }
            scenario.onActivity { activity ->
                val rail = activity.findViewById<NavigationRailView>(R.id.main_navigation_rail)
                assertEquals(0, MainNavigationGeometry.occupiedBottomHeight(rail))
                assertEquals(0, (activity.findViewById<View>(R.id.pager).layoutParams as ViewGroup.MarginLayoutParams).bottomMargin)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.main_bottom_navigation).visibility)
            }
        }
    }

    @Test
    fun floatingSearchSharesLocalGlobalRoutingAndSearchPageClearance() {
        preferences.edit().putString(key(R.string.bottom_navigation_style_key), "floating")
            .putInt(key(R.string.floating_navigation_spacing_key), 24)
            .putString(key(R.string.subscription_actions_style_key), "floating").commit()
        launchHome().use { scenario ->
            awaitHome(scenario)
            awaitActivity(scenario, "Only the relocated search action should be visible") { activity ->
                activity.findViewById<View>(R.id.subscription_floating_search)?.isShown == true &&
                    activity.findViewById<Toolbar>(R.id.toolbar).menu.findItem(R.id.action_search)?.isVisible == false
            }
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.subscription_floating_search).performClick()
                assertTrue(home(activity).isContextualSearchOpen())
                assertFalse(home(activity).isConfiguredSearchAvailable())
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.toolbar_contextual_search_container).visibility)
                assertFalse(activity.findViewById<View>(R.id.subscription_floating_actions).isShown)
                assertEquals(
                    activity.findViewById<FloatingBottomNavigationView>(R.id.main_bottom_navigation).occupiedHeight + activity.resources.getDimensionPixelSize(R.dimen.margin_normal),
                    (activity.findViewById<View>(R.id.contextual_global_search_fab).layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
                )
                activity.findViewById<View>(R.id.contextual_search_close).performClick()
                preferences.edit().putString(key(R.string.search_button_behavior_key), key(R.string.search_button_behavior_current_service_value)).commit()
                home(activity).performConfiguredSearchAction()
                activity.supportFragmentManager.executePendingTransactions()
            }
            awaitActivity(scenario, "Configured global search must open SearchFragment with the same navigation clearance") { activity ->
                activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) is SearchFragment &&
                    activity.findViewById<View>(R.id.main_bottom_navigation).isShown
            }
            scenario.onActivity { activity ->
                val navigation = activity.findViewById<FloatingBottomNavigationView>(R.id.main_bottom_navigation)
                val holder = activity.findViewById<View>(R.id.fragment_holder)
                assertEquals(navigation.height + dp(24), (holder.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin)
                preferences.edit().putInt(key(R.string.floating_navigation_spacing_key), 4).commit()
                assertEquals(navigation.height + dp(4), (holder.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin)
            }
        }
    }

    @Test
    fun linkOnlyPluginAndKidsModeKeepTheConfiguredActionPolicy() {
        ServiceList.register(object : YoutubeService(LINK_SERVICE_ID) {
            override fun getServiceInfo(): ServiceInfo = ServiceInfo("Navigation link fixture", emptyList())
            override fun getBaseUrl(): String = "https://navigation-fixture.invalid"
            override fun getSearchQHFactory(): SearchQueryHandlerFactory? = null
        })
        launchHome().use { scenario ->
            awaitHome(scenario)
            scenario.onActivity { activity ->
                val main = home(activity)
                preferences.edit().putString(key(R.string.search_button_behavior_key), key(R.string.search_button_behavior_current_service_value)).commit()
                ServiceHelper.setSelectedServiceId(context, LINK_SERVICE_ID)
                assertTrue(main.isConfiguredSearchAvailable())
                assertEquals(activity.getString(R.string.open_link), main.getConfiguredSearchLabel())
                assertEquals(R.drawable.ic_link, main.getConfiguredSearchIcon())
                main.performConfiguredSearchAction()
                activity.supportFragmentManager.executePendingTransactions()
                val dialog = activity.supportFragmentManager.fragments.filterIsInstance<OpenServiceLinkDialog>().single()
                assertTrue(dialog.requireDialog().isShowing)
                dialog.dismissNow()
                ServiceHelper.setSelectedServiceId(context, ServiceList.YouTube.serviceId)
                kidsPreferences.edit().putStringSet("profiles", setOf(ProfileManager.getActiveProfileId(context))).commit()
                assertFalse(main.isConfiguredSearchAvailable())
                main.performConfiguredSearchAction()
                activity.supportFragmentManager.executePendingTransactions()
                assertSame(main, home(activity))
                assertFalse(main.isContextualSearchOpen())
                preferences.edit().putString(key(R.string.search_button_behavior_key), key(R.string.search_button_behavior_current_tab_value)).commit()
                assertTrue("Kids mode still permits searching the user's own subscriptions", main.isConfiguredSearchAvailable())
                assertEquals(activity.getString(R.string.search), main.getConfiguredSearchLabel())
                main.performConfiguredSearchAction()
                assertTrue(main.isContextualSearchOpen())
            }
        }
    }

    private fun launchHome(): ActivityScenario<MainActivity> = ActivityScenario.launch(
        Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
    )

    private fun awaitHome(scenario: ActivityScenario<MainActivity>, bottomBar: Boolean = true) {
        awaitActivity(scenario, "The local home page did not finish layout") { activity ->
            val main = activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) as? MainFragment
            val subscriptions = main?.childFragmentManager?.fragments?.filterIsInstance<SubscriptionFragment>()?.firstOrNull()
            subscriptions?.isResumed == true && activity.findViewById<View>(R.id.pager)?.height?.let { it > 0 } == true &&
                (!bottomBar || activity.findViewById<View>(R.id.main_bottom_navigation).isShown)
        }
    }

    private fun awaitActivity(scenario: ActivityScenario<MainActivity>, message: String, ready: (MainActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            var done = false
            scenario.onActivity { done = ready(it) }
            if (done) return
            SystemClock.sleep(50)
        }
        throw AssertionError(message)
    }

    private fun home(activity: MainActivity): MainFragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) as MainFragment

    private fun layout(root: View, widthDp: Int, heightDp: Int) {
        repeat(2) {
            root.measure(View.MeasureSpec.makeMeasureSpec(dp(widthDp), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(dp(heightDp), View.MeasureSpec.EXACTLY))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        }
    }

    private fun bounds(view: View): Rect = IntArray(2).also(view::getLocationInWindow).let { Rect(it[0], it[1], it[0] + view.width, it[1] + view.height) }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()

    private fun key(resource: Int): String = context.getString(resource)

    companion object {
        private const val LINK_SERVICE_ID = 123499
    }
}
