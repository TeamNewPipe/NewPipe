/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.subscription

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomsheet.BottomSheetBehavior
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.databinding.FragmentSubscriptionBinding
import org.schabi.newpipe.databinding.ItemSubscriptionSortBinding
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.fragments.MainFragment
import org.schabi.newpipe.player.gesture.CustomBottomSheetBehavior
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.settings.tabs.Tab
import org.schabi.newpipe.settings.tabs.TabsManager
import org.schabi.newpipe.testUtil.TestDatabase
import org.schabi.newpipe.themes.ThemePacks
import org.schabi.newpipe.util.KEY_MAIN_PAGE_CHANGE
import org.schabi.newpipe.util.KEY_THEME_CHANGE
import org.schabi.newpipe.util.ServiceHelper

/** Uses the real editor, page hierarchy and player behavior without a network playback fixture. */
@RunWith(AndroidJUnit4::class)
class SubscriptionEditorPlayerClearanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply { isAccessible = true }
    private var previousDatabase: Any? = null
    private lateinit var database: AppDatabase
    private lateinit var previousPreferences: Map<String, Any?>

    @Before
    fun setUp() {
        val profile = ProfileManager.getActiveProfileId(context)
        val keys = listOf(
            R.string.bottom_navigation_style_key, R.string.floating_navigation_spacing_key,
            R.string.main_tabs_position_key, R.string.bottom_navigation_labels_key,
            R.string.subscription_actions_style_key, R.string.current_service_key,
            R.string.saved_tabs_key, R.string.tablet_mode_key,
            R.string.tablet_navigation_portrait_position_key, R.string.tablet_navigation_landscape_position_key
        ).map(context::getString) + listOf(
            "home_startup_tab_json",
            "github_star_prompt_completed",
            ThemePacks.KEY,
            KEY_THEME_CHANGE,
            KEY_MAIN_PAGE_CHANGE,
            SubscriptionGroupViewPreferences.KEY,
            SubscriptionGroupsCollapse.key(profile),
            "subscription_channels_collapsed_$profile",
            "subscription_layout_$profile"
        )
        previousPreferences = keys.associateWith { preferences.all[it] }
        preferences.edit()
            .putString(key(R.string.bottom_navigation_style_key), "classic")
            .putInt(key(R.string.floating_navigation_spacing_key), 24)
            .putBoolean(key(R.string.main_tabs_position_key), true)
            .putString(key(R.string.bottom_navigation_labels_key), key(R.string.bottom_navigation_labels_always_value))
            .putString(key(R.string.subscription_actions_style_key), "floating")
            .putString(key(R.string.tablet_mode_key), key(R.string.tablet_mode_off_key))
            .remove(SubscriptionGroupViewPreferences.KEY)
            .putBoolean(SubscriptionGroupsCollapse.key(profile), true)
            .putBoolean("subscription_channels_collapsed_$profile", false)
            .remove("subscription_layout_$profile")
            .remove("home_startup_tab_json")
            .remove(ThemePacks.KEY)
            .putBoolean(KEY_THEME_CHANGE, false)
            .putBoolean(KEY_MAIN_PAGE_CHANGE, false)
            .putBoolean("github_star_prompt_completed", true)
            .commit()
        ServiceHelper.setSelectedServiceId(context, ServiceList.YouTube.serviceId)
        TabsManager.getManager(context).saveTabs(listOf(Tab.Type.SUBSCRIPTIONS.tab, Tab.Type.BLANK.tab))
        previousDatabase = databaseField.get(null)
        database = TestDatabase.createReplacingNewPipeDatabase()
        database.subscriptionDAO().insertAll(
            (0 until CHANNEL_COUNT).map { index ->
                SubscriptionEntity(
                    serviceId = ServiceList.YouTube.serviceId,
                    url = "https://www.youtube.com/channel/editor-clearance-$index",
                    name = if (index == CHANNEL_COUNT - 1) LAST_CHANNEL else "Channel ${index.toString().padStart(2, '0')}",
                    profileId = profile
                )
            }
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @After
    fun tearDown() {
        try {
            preferences.edit().apply {
                previousPreferences.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Int -> putInt(key, value)
                        is Boolean -> putBoolean(key, value)
                        else -> remove(key)
                    }
                }
            }.commit()
        } finally {
            databaseField.set(null, previousDatabase)
            database.close()
        }
    }

    @Test
    fun lastChannelClearsThePlayerWhenPlaybackAndNavigationChangeWhileEditing() {
        launchHome().use { scenario ->
            rotate(scenario, landscape = false)
            setMiniPlayerVisible(scenario, false)
            openEditor(scenario)
            assertLastChannelVisible(scenario, playerVisible = false)
            for (style in listOf("classic", "floating")) {
                scenario.onActivity { preferences.edit().putString(key(R.string.bottom_navigation_style_key), style).commit() }
                setMiniPlayerVisible(scenario, true)
                val playingHeight = assertLastChannelVisible(scenario, playerVisible = true)
                savePreview("subscription-editor-clearance-$style-portrait")
                setMiniPlayerVisible(scenario, false)
                val hiddenHeight = assertLastChannelVisible(scenario, playerVisible = false)
                assertEquals("Hiding the player must release its space once", miniPlayerHeight(), hiddenHeight - playingHeight)
            }
            setMiniPlayerVisible(scenario, true)
            closeEditor(scenario)
            openEditor(scenario)
            assertLastChannelVisible(scenario, playerVisible = true)
        }
    }

    @Test
    fun lastChannelClearsThePlayerWhenSubscriptionsIsTheOnlyHomeTab() {
        TabsManager.getManager(context).saveTabs(listOf(Tab.Type.SUBSCRIPTIONS.tab))
        launchHome(bottomNavigationVisible = false).use { scenario ->
            rotate(scenario, landscape = false)
            setMiniPlayerVisible(scenario, false)
            openEditor(scenario)
            val hiddenHeight = assertLastChannelVisible(scenario, playerVisible = false)
            setMiniPlayerVisible(scenario, true)
            val playingHeight = assertLastChannelVisible(scenario, playerVisible = true)
            assertEquals("The player must reserve its height without a navigation bar", miniPlayerHeight(), hiddenHeight - playingHeight)
            savePreview("subscription-editor-clearance-single-tab-portrait")
            setMiniPlayerVisible(scenario, false)
            assertEquals(hiddenHeight, assertLastChannelVisible(scenario, playerVisible = false))
        }
    }

    @Test
    fun lastChannelRemainsVisibleWhenTheEditorRotatesWithTheMiniPlayer() {
        preferences.edit().putString(key(R.string.bottom_navigation_style_key), "floating").commit()
        launchHome().use { scenario ->
            rotate(scenario, landscape = false)
            setMiniPlayerVisible(scenario, true)
            openEditor(scenario)
            assertLastChannelVisible(scenario, playerVisible = true)
            rotate(scenario, landscape = true)
            assertLastChannelVisible(scenario, playerVisible = true)
            savePreview("subscription-editor-clearance-landscape")
            rotate(scenario, landscape = false)
            assertLastChannelVisible(scenario, playerVisible = true)
        }
    }

    private fun launchHome(bottomNavigationVisible: Boolean = true): ActivityScenario<MainActivity> = ActivityScenario.launch<MainActivity>(
        Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
    ).also { scenario ->
        awaitActivity(scenario, "The subscriptions home page did not become ready") { activity ->
            val main = activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) as? MainFragment
            main?.childFragmentManager?.fragments?.filterIsInstance<SubscriptionFragment>()?.any { it.isResumed } == true &&
                activity.findViewById<View>(R.id.main_bottom_navigation).isShown == bottomNavigationVisible &&
                (bottomNavigationVisible || !activity.findViewById<View>(R.id.main_tab_layout).isShown)
        }
    }

    private fun openEditor(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { binding(it).itemsList.scrollToPosition(0) }
        awaitActivity(scenario, "The visible channels header did not become ready") { activity ->
            binding(activity).itemsList.findViewById<View>(R.id.channels_header_edit)?.isShown == true
        }
        scenario.onActivity { activity ->
            assertTrue(binding(activity).itemsList.findViewById<View>(R.id.channels_header_edit).performClick())
        }
        awaitActivity(scenario, "The inline editor did not load all channels") { activity ->
            val binding = binding(activity)
            binding.subscriptionSortList.isShown && binding.subscriptionSortList.adapter?.itemCount == CHANNEL_COUNT + 1
        }
    }

    private fun closeEditor(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { binding(it).subscriptionSortList.scrollToPosition(0) }
        awaitEditorLayout(scenario)
        scenario.onActivity { activity ->
            val done = binding(activity).subscriptionSortList.findViewById<View>(R.id.channels_header_edit)
            assertTrue("Done remains reachable by scrolling to the editor header", done.isShown)
            assertTrue(done.performClick())
        }
    }

    private fun setMiniPlayerVisible(scenario: ActivityScenario<MainActivity>, visible: Boolean) {
        val state = if (visible) BottomSheetBehavior.STATE_COLLAPSED else BottomSheetBehavior.STATE_HIDDEN
        val contentPadding = if (visible) miniPlayerHeight() else 0
        scenario.onActivity { activity ->
            val behavior = playerBehavior(activity)
            if (visible) behavior.peekHeight = miniPlayerHeight()
            behavior.state = state
        }
        awaitActivity(scenario, "The mini-player did not finish changing visibility to $visible") { activity ->
            playerBehavior(activity).state == state &&
                activity.findViewById<View>(R.id.fragment_holder).paddingBottom == contentPadding
        }
        instrumentation.waitForIdleSync()
    }

    private fun rotate(scenario: ActivityScenario<MainActivity>, landscape: Boolean) {
        val orientation = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        scenario.onActivity {
            it.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        awaitActivity(scenario, "The activity did not finish rotating; landscape=$landscape") { activity ->
            val root = activity.findViewById<View>(R.id.main_content)
            activity.resources.configuration.orientation == orientation &&
                root.width > 0 && root.height > 0 && (root.width > root.height) == landscape
        }
        instrumentation.waitForIdleSync()
    }

    private fun assertLastChannelVisible(scenario: ActivityScenario<MainActivity>, playerVisible: Boolean): Int {
        try {
            awaitEditorLayout(scenario)
            scenario.onActivity { activity ->
                val list = binding(activity).subscriptionSortList
                list.scrollToPosition(list.adapter!!.itemCount - 1)
            }
            awaitEditorLayout(scenario)
            scenario.onActivity { activity ->
                val list = binding(activity).subscriptionSortList
                list.scrollBy(0, list.height)
            }
            awaitEditorLayout(scenario)
            var listHeight = 0
            scenario.onActivity { activity ->
                val binding = binding(activity)
                val list = binding.subscriptionSortList
                val state = if (playerVisible) BottomSheetBehavior.STATE_COLLAPSED else BottomSheetBehavior.STATE_HIDDEN
                assertEquals("The player must retain its requested state through layout changes", state, playerBehavior(activity).state)
                assertEquals("The page must reserve only the visible player", if (playerVisible) miniPlayerHeight() else 0, activity.findViewById<View>(R.id.fragment_holder).paddingBottom)
                assertTrue("The editor must have a nonempty scrolling viewport", list.height > 0)
                val holder = list.findViewHolderForAdapterPosition(list.adapter!!.itemCount - 1)
                    ?: throw AssertionError("The last channel was not attached after the editor completed layout")
                val row = ItemSubscriptionSortBinding.bind(holder.itemView)
                assertEquals(LAST_CHANNEL, row.sortLabel.text.toString())
                if (activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                    assertEquals("The long-name fixture must exercise a second text line", 2, row.sortLabel.lineCount)
                }
                assertFalse("The editor must be scrolled all the way to its final row", list.canScrollVertically(1))
                assertFalse("Browsing actions must not overlay the inline editor", binding.subscriptionFloatingActions.isShown)
                assertEquals("The editor must use the page's full available height", bounds(activity.findViewById(R.id.pager)).bottom, bounds(list).bottom)
                val navigation = activity.findViewById<View>(R.id.main_bottom_navigation)
                val page = activity.findViewById<View>(R.id.fragment_holder)
                val unobstructedBottom = when {
                    playerVisible -> bounds(activity.findViewById(R.id.fragment_player_holder)).top
                    navigation.isShown -> bounds(navigation).top
                    else -> bounds(page).bottom - page.paddingBottom
                }
                assertTrue("The editor viewport must end within the unobstructed page", bounds(list).bottom <= unobstructedBottom)
                assertTrue("The editor viewport (${list.height}px) must fit a complete channel row (${row.root.height}px)", list.height >= row.root.height)
                for (view in listOf(row.root, row.sortAvatar, row.sortLabel, row.sortDrag)) {
                    val visible = Rect()
                    assertTrue("The final channel element ${view.id} must be visible", view.getLocalVisibleRect(visible))
                    assertEquals("The final channel element ${view.id} must not be clipped", Rect(0, 0, view.width, view.height), visible)
                }
                listHeight = list.height
            }
            return listHeight
        } catch (failure: AssertionError) {
            var geometry = ""
            scenario.onActivity { geometry = describeGeometry(it) }
            runCatching { savePreview("subscription-editor-clearance-failure") }
            throw AssertionError("${failure.message}; $geometry", failure)
        }
    }

    private fun awaitEditorLayout(scenario: ActivityScenario<MainActivity>) {
        awaitActivity(scenario, "The editor did not complete its pending layout") { activity ->
            val list = binding(activity).subscriptionSortList
            !activity.findViewById<View>(R.id.main_content).isLayoutRequested &&
                !list.isLayoutRequested && !list.isComputingLayout && !list.hasPendingAdapterUpdates()
        }
    }

    private fun describeGeometry(activity: MainActivity): String {
        val binding = binding(activity)
        val list = binding.subscriptionSortList
        val panel = binding.subscriptionSortPanel
        val configuration = activity.resources.configuration
        val children = (0 until panel.childCount).joinToString { index ->
            panel.getChildAt(index).let { "${it.id}:${it.height}px" }
        }
        return "orientation=${configuration.orientation}, window=${configuration.screenWidthDp}x${configuration.screenHeightDp}dp, " +
            "fontScale=${configuration.fontScale}, density=${activity.resources.displayMetrics.density}, " +
            "page=${bounds(activity.findViewById(R.id.pager))}, panel=${bounds(panel)}, list=${bounds(list)}, controls=[$children], " +
            "player=${bounds(activity.findViewById(R.id.fragment_player_holder))}, state=${playerBehavior(activity).state}, " +
            "padding=${activity.findViewById<View>(R.id.fragment_holder).paddingBottom}, rows=${list.adapter?.itemCount}, attached=${list.childCount}"
    }

    private fun savePreview(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val file = File(context.getExternalFilesDir(null), "$name.png")
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
        val destination = "/sdcard/Download/wizestream-player-layout-previews"
        listOf("mkdir -p $destination", "cp ${file.absolutePath} $destination/").forEach { command ->
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        }
    }

    private fun binding(activity: MainActivity): FragmentSubscriptionBinding {
        val main = activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) as MainFragment
        return FragmentSubscriptionBinding.bind(main.childFragmentManager.fragments.filterIsInstance<SubscriptionFragment>().single().requireView())
    }

    private fun playerBehavior(activity: MainActivity): CustomBottomSheetBehavior = BottomSheetBehavior.from(activity.findViewById<View>(R.id.fragment_player_holder)) as CustomBottomSheetBehavior

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

    private fun bounds(view: View): Rect = IntArray(2).also(view::getLocationInWindow).let { Rect(it[0], it[1], it[0] + view.width, it[1] + view.height) }

    private fun miniPlayerHeight(): Int = context.resources.getDimensionPixelSize(R.dimen.mini_player_height)

    private fun key(resource: Int): String = context.getString(resource)

    companion object {
        private const val CHANNEL_COUNT = 40
        private const val LAST_CHANNEL = "Channel 39 — A very long channel name that wraps onto a second line in the subscription editor"
    }
}
