/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.bookmark

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.learning.model.LearningContentSourceEntity
import org.schabi.newpipe.database.playlist.PlaylistLocalItem
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.learning.LearningContentManager
import org.schabi.newpipe.learning.LearningMode
import org.schabi.newpipe.local.LocalItemListAdapter
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.profiles.ProfileTransferPreferences
import org.schabi.newpipe.settings.tabs.Tab
import org.schabi.newpipe.settings.tabs.TabsManager
import org.schabi.newpipe.testUtil.TestDatabase
import org.schabi.newpipe.util.NavigationHelper

@RunWith(AndroidJUnit4::class)
class PlaylistOpeningUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val queryExecutor = Executors.newSingleThreadExecutor()
    private val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply { isAccessible = true }
    private var oldDatabase: AppDatabase? = null
    private lateinit var database: AppDatabase
    private lateinit var savedPreferences: Map<String, Any?>
    private lateinit var first: String
    private lateinit var second: String
    private lateinit var firstRows: List<Long>
    private lateinit var secondRows: List<Long>

    @Before
    fun setup() {
        savedPreferences = preferences.all.toMap()
        oldDatabase = databaseField.get(null) as? AppDatabase
        database = TestDatabase.createReplacingNewPipeDatabase(queryExecutor)
        val suffix = UUID.randomUUID().toString().take(8)
        first = requireNotNull(ProfileManager.createProfile(context, "Opening A $suffix")).id
        second = requireNotNull(ProfileManager.createProfile(context, "Opening B $suffix")).id
        assertTrue(ProfileManager.setActiveProfile(context, first))
        firstRows = seedPlaylists(first, "A")
        secondRows = seedPlaylists(second, "B")
        TabsManager.getManager(context).saveTabs(listOf(Tab.Type.BLANK.tab))
        preferences.edit().putBoolean("github_star_prompt_completed", true)
            .putBoolean(context.getString(R.string.learning_mode_key), false).commit()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @After
    fun tearDown() {
        try {
            if (::first.isInitialized) ProfileManager.deleteProfile(context, first)
            if (::second.isInitialized) ProfileManager.deleteProfile(context, second)
            if (::savedPreferences.isInitialized) restorePreferences()
        } finally {
            try {
                queryExecutor.submit {}.get(5, SECONDS)
                if (::database.isInitialized) NewPipeDatabase.close()
                queryExecutor.submit {}.get(5, SECONDS)
            } finally {
                databaseField.set(null, oldDatabase?.takeIf { it.isOpen })
                queryExecutor.shutdown()
                assertTrue("Playlist queries did not stop", queryExecutor.awaitTermination(5, SECONDS))
            }
        }
    }

    @Test
    fun freshOpeningsUseLastUsedAllOrAFixedCategoryWithoutChangingPlaylistOrder() {
        val originalOrder = persistedOrder(first)
        withBookmarks { scenario ->
            assertState(scenario, first, PlaylistCategories.ALL, R.string.playlist_categories_all, firstRows)
            openOpeningDialog(scenario)
            onView(withText(R.string.playlist_opening_last_used)).inRoot(isDialog()).check(matches(isChecked()))
            chooseDialogText(context.getString(R.string.cancel))
            chooseFilter("Courses")
            assertState(scenario, first, "courses", "Courses", listOf(firstRows[0]))
            reopen(scenario)
            assertState(scenario, first, "courses", "Courses", listOf(firstRows[0]))

            chooseOpening(scenario, "Music", fromManage = true)
            assertState(scenario, first, "courses", "Courses", listOf(firstRows[0]))
            chooseFilter(context.getString(R.string.playlist_uncategorized))
            reopen(scenario)
            assertState(scenario, first, "music", "Music", listOf(firstRows[1]))

            chooseOpening(scenario, context.getString(R.string.playlist_categories_all))
            chooseFilter("Courses")
            reopen(scenario)
            assertState(scenario, first, PlaylistCategories.ALL, R.string.playlist_categories_all, firstRows)
            chooseOpening(scenario, context.getString(R.string.playlist_opening_last_used))
            chooseFilter(context.getString(R.string.playlist_uncategorized))
        }
        // A different activity and fragment cannot inherit the previous fragment's @State fields.
        withBookmarks { scenario ->
            assertState(scenario, first, PlaylistCategories.UNCATEGORIZED, R.string.playlist_uncategorized, listOf(firstRows[2]))
        }
        assertEquals(originalOrder, persistedOrder(first))
    }

    @Test
    fun recreationAndBackStackKeepTheCurrentFilterButFreshOpeningUsesTheFixedChoice() {
        withBookmarks { scenario ->
            assertState(scenario, first, PlaylistCategories.ALL, R.string.playlist_categories_all, firstRows)
            chooseOpening(scenario, context.getString(R.string.playlist_categories_all))
            chooseFilter("Courses")
            scenario.recreate()
            assertState(scenario, first, "courses", "Courses", listOf(firstRows[0]))
            onActivity(scenario) { activity ->
                NavigationHelper.openBookmarksFragment(activity.supportFragmentManager)
                activity.supportFragmentManager.executePendingTransactions()
            }
            assertState(scenario, first, PlaylistCategories.ALL, R.string.playlist_categories_all, firstRows)
            chooseFilter("Music")
            assertState(scenario, first, "music", "Music", listOf(firstRows[1]))
            onActivity(scenario) { it.supportFragmentManager.popBackStackImmediate() }
            assertState(scenario, first, "courses", "Courses", listOf(firstRows[0]))
            assertEquals("courses", PlaylistOpeningPreferences.getLastCategory(context, first))
            reopen(scenario)
            assertState(scenario, first, PlaylistCategories.ALL, R.string.playlist_categories_all, firstRows)
        }
    }

    @Test
    fun aPendingChoiceUsesItsCapturedOwnerAndProfileSwitchRejectsForeignSavedState() {
        PlaylistOpeningPreferences.setOpeningCategory(context, second, PlaylistCategories.UNCATEGORIZED)
        withBookmarks { scenario ->
            assertState(scenario, first, PlaylistCategories.ALL, R.string.playlist_categories_all, firstRows)
            onActivity(scenario) { activity ->
                current(activity).requireView().findViewById<View>(R.id.playlist_category_filter).performClick()
                assertTrue(ProfileManager.setActiveProfile(context, second))
            }
            chooseDialogText("Music")
            assertEquals("music", PlaylistOpeningPreferences.getLastCategory(context, first))
            assertEquals(PlaylistCategories.ALL, PlaylistOpeningPreferences.getLastCategory(context, second))
            scenario.recreate()
            assertState(scenario, second, PlaylistCategories.UNCATEGORIZED, R.string.playlist_uncategorized, listOf(secondRows[2]))
            onActivity(scenario) { assertTrue(ProfileManager.setActiveProfile(context, first)) }
            scenario.recreate()
            assertState(scenario, first, "music", "Music", listOf(firstRows[1]))
        }
    }

    @Test
    fun renamingDeletingAndReplacingCategoriesUpdateTheLiveViewAndLaterOpenings() {
        val originalOrder = persistedOrder(first)
        withBookmarks { scenario ->
            assertState(scenario, first, PlaylistCategories.ALL, R.string.playlist_categories_all, firstRows)
            chooseOpening(scenario, "Courses")
            chooseFilter("Courses")
            onView(withId(R.id.playlist_category_manage)).perform(click())
            chooseDialogText("Courses")
            chooseDialogText(context.getString(R.string.rename))
            onView(withId(R.id.dialogEditText)).inRoot(isDialog()).perform(replaceText("Renamed courses"), closeSoftKeyboard())
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            assertState(scenario, first, "courses", "Renamed courses", listOf(firstRows[0]))
            reopen(scenario)
            assertState(scenario, first, "courses", "Renamed courses", listOf(firstRows[0]))

            onView(withId(R.id.playlist_category_manage)).perform(click())
            chooseDialogText("Renamed courses")
            chooseDialogText(context.getString(R.string.delete))
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            assertState(scenario, first, PlaylistCategories.ALL, R.string.playlist_categories_all, firstRows)
            assertEquals(PlaylistCategories.ALL, PlaylistOpeningPreferences.getOpeningCategory(context, first))
            assertEquals(PlaylistCategories.ALL, PlaylistOpeningPreferences.getLastCategory(context, first))
            assertEquals(originalOrder, persistedOrder(first))

            chooseOpening(scenario, "Music")
            chooseFilter("Music")
            val imported = PlaylistCategories().apply { rename("imported", "Imported") }
            preferences.edit().putString(PlaylistCategories.preferenceKey(second), imported.toJson()).commit()
            PlaylistOpeningPreferences.setOpeningCategory(context, second, "imported")
            ProfileTransferPreferences.copy(context, second, first, true, emptyMap())
            assertState(scenario, first, PlaylistCategories.ALL, R.string.playlist_categories_all, firstRows)
            assertEquals(PlaylistCategories.ALL, PlaylistOpeningPreferences.getOpeningCategory(context, first))
            assertEquals("imported", PlaylistOpeningPreferences.getOpeningCategory(context, second))
            reopen(scenario)
            assertState(scenario, first, PlaylistCategories.ALL, R.string.playlist_categories_all, firstRows)
        }
        assertEquals(originalOrder, persistedOrder(first))
    }

    @Test
    fun learningSurvivesRecreationAndFreshOpeningUntilTheProfileIsExcluded() {
        preferences.edit().putBoolean(context.getString(R.string.learning_mode_key), true)
            .putBoolean(LearningMode.profilePreferenceKey(first), true).commit()
        database.learningContentDAO().upsertSource(
            LearningContentSourceEntity(
                sourceId = LearningContentManager.localPlaylistSourceId(firstRows[0]),
                sourceType = LearningContentSourceEntity.TYPE_LOCAL_PLAYLIST,
                localPlaylistId = firstRows[0],
                title = "A Course"
            )
        )
        withBookmarks { scenario ->
            assertState(scenario, first, PlaylistCategories.ALL, R.string.playlist_categories_all, firstRows)
            chooseOpening(scenario, context.getString(R.string.learning_for_learning))
            chooseFilter(context.getString(R.string.learning_for_learning))
            assertState(scenario, first, PlaylistOpeningPreferences.LEARNING_CATEGORY, R.string.learning_for_learning, listOf(firstRows[0]))
            scenario.recreate()
            assertState(scenario, first, PlaylistOpeningPreferences.LEARNING_CATEGORY, R.string.learning_for_learning, listOf(firstRows[0]))
            reopen(scenario)
            assertState(scenario, first, PlaylistOpeningPreferences.LEARNING_CATEGORY, R.string.learning_for_learning, listOf(firstRows[0]))

            preferences.edit().putBoolean(LearningMode.profilePreferenceKey(first), false).commit()
            assertState(scenario, first, PlaylistCategories.ALL, R.string.playlist_categories_all, firstRows)
            reopen(scenario)
            assertState(scenario, first, PlaylistCategories.ALL, R.string.playlist_categories_all, firstRows)
            openOpeningDialog(scenario)
            onView(withText(R.string.learning_for_learning)).inRoot(isDialog()).check(doesNotExist())
            chooseDialogText(context.getString(R.string.cancel))
        }
    }

    @Test
    fun assigningAPlaylistChangesOnlyItsMembershipAndNeverTheRememberedFilterOrOrder() {
        val originalOrder = persistedOrder(first)
        withBookmarks { scenario ->
            assertState(scenario, first, PlaylistCategories.ALL, R.string.playlist_categories_all, firstRows)
            chooseFilter("Courses")
            assertState(scenario, first, "courses", "Courses", listOf(firstRows[0]))
            awaitCondition {
                var opened = false
                onActivity(scenario) { activity ->
                    val list = current(activity).requireView().findViewById<RecyclerView>(R.id.items_list)
                    val adapter = list.adapter as LocalItemListAdapter
                    opened = list.findViewHolderForAdapterPosition(adapter.getAdapterPositionForItemIndex(0))?.itemView?.performLongClick() == true
                }
                opened
            }
            chooseDialogText(context.getString(R.string.playlist_move_to_category))
            onView(withText(R.string.playlist_opening_title)).inRoot(isDialog()).check(doesNotExist())
            chooseDialogText("Music")
            assertState(scenario, first, "courses", "Courses", emptyList())
            assertEquals("courses", PlaylistOpeningPreferences.getLastCategory(context, first))
            assertNull(PlaylistOpeningPreferences.getOpeningCategory(context, first))
            val categories = PlaylistCategories.fromJson(preferences.getString(PlaylistCategories.preferenceKey(first), ""))
            assertTrue(categories.matches("local:${firstRows[0]}", "music"))
            assertFalse(categories.matches("local:${firstRows[0]}", "courses"))
            reopen(scenario)
            assertState(scenario, first, "courses", "Courses", emptyList())
            chooseFilter("Music")
            assertState(scenario, first, "music", "Music", firstRows.take(2))
            chooseFilter(context.getString(R.string.playlist_categories_all))
            assertState(scenario, first, PlaylistCategories.ALL, R.string.playlist_categories_all, firstRows)
        }
        assertEquals(originalOrder, persistedOrder(first))
    }

    @Test
    fun deletingAProfileClearsItsOpeningPreferencesWithoutTouchingTheSurvivor() {
        for (profile in listOf(first, second)) {
            PlaylistOpeningPreferences.setOpeningCategory(context, profile, "courses")
            PlaylistOpeningPreferences.rememberCategory(context, profile, "music")
        }
        // This common metadata cleanup is also invoked by ProfileDeletionManager.
        assertTrue(ProfileManager.deleteProfile(context, first))
        assertNull(ProfileManager.getProfile(context, first))
        assertNull(PlaylistOpeningPreferences.getOpeningCategory(context, first))
        assertEquals(PlaylistCategories.ALL, PlaylistOpeningPreferences.getLastCategory(context, first))
        assertEquals("courses", PlaylistOpeningPreferences.getOpeningCategory(context, second))
        assertEquals("music", PlaylistOpeningPreferences.getLastCategory(context, second))
    }

    private fun seedPlaylists(profile: String, prefix: String): List<Long> {
        val rows = listOf("Course", "Music", "Other").mapIndexed { index, name ->
            database.playlistDAO().insert(
                PlaylistEntity(
                    name = "$prefix $name",
                    isThumbnailPermanent = false,
                    thumbnailStreamId = PlaylistEntity.DEFAULT_THUMBNAIL_ID,
                    displayIndex = (index + 1) * 10L,
                    profileId = profile
                )
            )
        }
        val categories = PlaylistCategories().apply {
            rename("courses", "Courses")
            rename("music", "Music")
            assign("local:${rows[0]}", "courses")
            assign("local:${rows[1]}", "music")
        }
        preferences.edit().putString(PlaylistCategories.preferenceKey(profile), categories.toJson()).commit()
        return rows
    }

    private fun withBookmarks(block: (ActivityScenario<MainActivity>) -> Unit) {
        val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            try {
                reopen(scenario)
                block(scenario)
            } finally {
                // Remove loading and dialog-owning views before ActivityScenario waits for idle.
                onActivity(scenario) { activity ->
                    activity.supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                    val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_holder)
                    if (fragment is BookmarkFragment) {
                        activity.supportFragmentManager.beginTransaction().remove(fragment).commitNowAllowingStateLoss()
                    }
                }
            }
        }
    }

    private fun reopen(scenario: ActivityScenario<MainActivity>) {
        onActivity(scenario) { activity ->
            val manager = activity.supportFragmentManager
            manager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            NavigationHelper.openBookmarksFragment(manager)
            manager.executePendingTransactions()
        }
    }

    private fun chooseFilter(label: String) {
        onView(withId(R.id.playlist_category_filter)).perform(click())
        chooseDialogText(label)
    }

    private fun openOpeningDialog(scenario: ActivityScenario<MainActivity>, fromManage: Boolean = false) {
        onActivity(scenario) { activity ->
            current(activity).requireView().findViewById<View>(
                if (fromManage) R.id.playlist_category_manage else R.id.playlist_category_filter
            ).performClick()
        }
        chooseDialogText(context.getString(R.string.playlist_opening_title))
    }

    private fun chooseOpening(scenario: ActivityScenario<MainActivity>, label: String, fromManage: Boolean = false) {
        openOpeningDialog(scenario, fromManage)
        chooseDialogText(label)
    }

    private fun chooseDialogText(label: String) {
        onView(withText(label)).inRoot(isDialog()).perform(click())
    }

    private fun assertState(scenario: ActivityScenario<MainActivity>, profile: String, category: String, label: Int, rows: List<Long>) {
        assertState(scenario, profile, category, context.getString(label), rows)
    }

    private fun assertState(scenario: ActivityScenario<MainActivity>, profile: String, category: String, label: String, rows: List<Long>) {
        var observed = "no fragment view"
        awaitCondition({ "Expected profile=$profile category=$category label=$label rows=$rows; observed $observed" }) {
            var matches = false
            onActivity(scenario) { activity ->
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) as? BookmarkFragment
                val view = fragment?.view
                if (view != null) {
                    val adapter = view.findViewById<RecyclerView>(R.id.items_list).adapter as LocalItemListAdapter
                    observed = "profile=${fragment.categoryProfileId} category=${fragment.selectedCategory} " +
                        "label=${view.findViewById<TextView>(R.id.playlist_category_filter).text} " +
                        "rows=${adapter.itemsList.filterIsInstance<PlaylistLocalItem>().map { it.uid }}"
                    matches = fragment.categoryProfileId == profile && fragment.selectedCategory == category &&
                        view.findViewById<TextView>(R.id.playlist_category_filter).text.toString() == label &&
                        adapter.itemsList.filterIsInstance<PlaylistLocalItem>().map { it.uid } == rows
                }
            }
            matches
        }
    }

    private fun current(activity: MainActivity) = activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) as BookmarkFragment

    private fun persistedOrder(profile: String) = database.playlistDAO().getAllDirectForProfile(profile).map { it.uid to it.displayIndex }

    private fun onActivity(scenario: ActivityScenario<MainActivity>, action: (MainActivity) -> Unit) {
        val completed = CountDownLatch(1)
        var failure: Throwable? = null
        mainHandler.post {
            try {
                scenario.onActivity { action(it) }
            } catch (error: Throwable) {
                failure = error
            } finally {
                completed.countDown()
            }
        }
        assertTrue("Playlist main-thread action did not finish", completed.await(5, SECONDS))
        failure?.let { throw it }
    }

    private fun awaitCondition(message: () -> String = { "Playlists did not reach the expected state" }, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        do {
            if (condition()) return
            SystemClock.sleep(25)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError(message())
    }

    private fun restorePreferences() {
        preferences.edit().clear().apply {
            savedPreferences.forEach { (key, value) -> restoreValue(key, value) }
        }.commit()
    }

    @Suppress("UNCHECKED_CAST")
    private fun SharedPreferences.Editor.restoreValue(key: String, value: Any?) {
        when (value) {
            is Boolean -> putBoolean(key, value)
            is String -> putString(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
            is Set<*> -> putStringSet(key, value as Set<String>)
        }
    }
}
