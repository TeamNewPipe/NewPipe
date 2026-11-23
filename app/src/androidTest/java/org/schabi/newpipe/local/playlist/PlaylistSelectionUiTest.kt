/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.playlist

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.ActionBarContextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDisplayingAtLeast
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.appbar.AppBarLayout
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.io.File
import org.hamcrest.Description
import org.hamcrest.Matchers.allOf
import org.hamcrest.TypeSafeMatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.learning.LearningContentManager
import org.schabi.newpipe.learning.LearningMode
import org.schabi.newpipe.local.LocalItemListAdapter
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.settings.SettingsActivity
import org.schabi.newpipe.testUtil.TestDatabase

class PlaylistSelectionUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply { isAccessible = true }
    private val learningField = LearningContentManager::class.java.getDeclaredField("instance").apply { isAccessible = true }
    private var oldDatabase: Any? = null
    private var oldLearning: Any? = null
    private lateinit var database: AppDatabase
    private lateinit var learning: LearningContentManager
    private lateinit var profile: String
    private lateinit var savedPreferences: Map<String, Any?>
    private lateinit var originalIds: List<Long>
    private lateinit var oldSort: LocalPlaylistSortMode
    private var playlistId = 0L

    @Before
    fun setup() {
        oldDatabase = databaseField.get(null)
        oldLearning = learningField.get(null)
        database = TestDatabase.createReplacingNewPipeDatabase()
        learningField.set(null, null)
        learning = LearningContentManager.getInstance(context)
        profile = ProfileManager.getActiveProfileId(context)
        val duplicate = remote("duplicate", "Match duplicate")
        val local = remote("local", "Local clip").apply {
            sourceType = StreamEntity.SOURCE_TYPE_LOCAL
            url = "content://media/external/video/media/42"
            mimeType = "video/mp4"
            localMediaId = 42
        }
        LocalPlaylistManager(database, profile).createPlaylist(
            "Selection fixture",
            listOf(duplicate, remote("hidden", "Hidden entry"), duplicate, remote("other", "Match different"), local)
        ).blockingGet()
        playlistId = database.playlistDAO().getAllDirectForProfile(profile).single().uid
        originalIds = persistedIds()
        oldSort = LocalPlaylistSortPreferences.get(context, profile, playlistId)
        val keys = listOf(
            context.getString(R.string.learning_mode_key),
            LearningMode.profilePreferenceKey(profile),
            "list_separators_${profile}_playlist:$playlistId",
            "list_separators_${profile}_playlist:${playlistId}_positions"
        )
        savedPreferences = keys.associateWith { preferences.all[it] }
        preferences.edit().putBoolean(keys[0], false).putBoolean(keys[1], false).remove(keys[2]).remove(keys[3]).commit()
    }

    @After
    fun cleanup() {
        (LearningContentManager::class.java.getDeclaredField("disposables").apply { isAccessible = true }.get(learning) as CompositeDisposable).dispose()
        learningField.set(null, oldLearning)
        databaseField.set(null, oldDatabase)
        database.close()
        LocalPlaylistSortPreferences.set(context, profile, playlistId, oldSort)
        preferences.edit().apply {
            savedPreferences.forEach { (key, value) ->
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is String -> putString(key, value)
                    else -> remove(key)
                }
            }
        }.commit()
    }

    @Test
    fun selectItemsIsAvailableOutsideEditAndRemovesOnlyTheConfirmedDuplicateOccurrence() {
        withPlaylist { scenario, fragment ->
            scenario.onActivity { assertFalse(fragment.editMode) }
            onView(withId(R.id.collection_select_move)).check(matches(withText(R.string.playlist_select_items))).check(matches(isDisplayed())).perform(click())
            waitFor { selecting(scenario, fragment) }
            tapRow(scenario, fragment, 2)
            assertSelectionCount(1)
            savePreview("playlist-selection-duplicate")
            chooseAction(R.id.playlist_selection_remove, R.string.playlist_remove_selected)
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
            assertEquals("Cancel must leave every occurrence intact", originalIds, persistedIds())
            assertTrue(selecting(scenario, fragment))
            chooseAction(R.id.playlist_selection_remove, R.string.playlist_remove_selected)
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            val expected = listOf(originalIds[0], originalIds[1], originalIds[3], originalIds[4])
            waitFor { persistedIds() == expected && !selecting(scenario, fragment) }
            assertEquals(1, persistedIds().count { it == originalIds[0] })
            scenario.onActivity { assertEquals(expected, adapter(fragment).itemsList.map { (it as org.schabi.newpipe.database.playlist.PlaylistStreamEntry).streamId }) }
        }
    }

    @Test
    fun filteredSortedSelectionMovesAndRemovesOnlyShownRowsWithoutLosingHiddenEntries() {
        withPlaylist(LocalPlaylistSortMode.REVERSE_PLAYLIST_ORDER) { scenario, fragment ->
            scenario.onActivity { fragment.setContextualSearchQuery("Match") }
            waitFor { rowCount(scenario, fragment) == 3 }
            onView(withId(R.id.collection_select_move)).perform(click())
            waitFor { selecting(scenario, fragment) }
            chooseAction(R.id.selection_all, R.string.collection_select_all)
            assertSelectionCount(3)
            savePreview("playlist-selection-filtered")
            chooseAction(R.id.playlist_selection_move, R.string.playlist_reorder_selected)
            onView(withText(R.string.collection_move_end)).inRoot(isDialog()).perform(click())
            val moved = listOf(originalIds[1], originalIds[4], originalIds[0], originalIds[2], originalIds[3])
            waitFor { persistedIds() == moved && !selecting(scenario, fragment) }
            assertEquals(3, rowCount(scenario, fragment))
            onView(withId(R.id.collection_select_move)).perform(click())
            waitFor { selecting(scenario, fragment) }
            chooseAction(R.id.selection_all, R.string.collection_select_all)
            chooseAction(R.id.playlist_selection_remove, R.string.playlist_remove_selected)
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            val remaining = listOf(originalIds[1], originalIds[4])
            waitFor { persistedIds() == remaining && !selecting(scenario, fragment) }
            scenario.onActivity { fragment.setContextualSearchQuery("") }
            waitFor { rowCount(scenario, fragment) == 2 }
            assertEquals("A temporary sort must not become the saved order", remaining, persistedIds())
        }
    }

    @Test
    fun movingFilteredRowsToAnotherPlaylistKeepsHiddenRowsAndCancellingDoesNotWrite() {
        LocalPlaylistManager(database, profile).createPlaylist("Move destination", listOf(remote("target", "Existing destination entry"))).blockingGet()
        val destinationId = database.playlistDAO().getAllDirectForProfile(profile).single { it.name == "Move destination" }.uid
        fun destinationIds() = database.playlistStreamDAO().getQuickAddEntries(destinationId).map { it.streamUid }
        val before = destinationIds()
        withPlaylist(LocalPlaylistSortMode.REVERSE_PLAYLIST_ORDER) { scenario, fragment ->
            scenario.onActivity { fragment.setContextualSearchQuery("Match") }
            waitFor { rowCount(scenario, fragment) == 3 }
            onView(withId(R.id.collection_select_move)).perform(click())
            waitFor { selecting(scenario, fragment) }
            tapRow(scenario, fragment, 0)
            tapRow(scenario, fragment, 1)
            chooseAction(R.id.playlist_selection_transfer, R.string.playlist_move_to_playlist)
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
            assertEquals(originalIds, persistedIds())
            assertEquals(before, destinationIds())
            assertTrue(selecting(scenario, fragment))

            chooseAction(R.id.playlist_selection_transfer, R.string.playlist_move_to_playlist)
            onView(withText("Move destination")).inRoot(isDialog()).perform(click())
            val remaining = listOf(originalIds[0], originalIds[1], originalIds[4])
            val transferred = before + listOf(originalIds[3], originalIds[2])
            waitFor { persistedIds() == remaining && destinationIds() == transferred && !selecting(scenario, fragment) }
            assertEquals("Only the selected duplicate occurrence should move", 1, persistedIds().count { it == originalIds[0] })
            scenario.onActivity { fragment.setContextualSearchQuery("") }
            waitFor { rowCount(scenario, fragment) == remaining.size }
            assertEquals("Hidden rows must retain their saved order", remaining, persistedIds())
        }
    }

    @Test
    fun changingSearchEndsSelectionAndLocalMediaKeepsQueueAndPlaylistActions() {
        withPlaylist { scenario, fragment ->
            onView(withId(R.id.collection_select_move)).perform(click())
            waitFor { selecting(scenario, fragment) }
            tapRow(scenario, fragment, 0)
            scenario.onActivity { fragment.setContextualSearchQuery("Local clip") }
            waitFor { rowCount(scenario, fragment) == 1 && !selecting(scenario, fragment) }
            assertEquals(originalIds, persistedIds())
            onView(withId(R.id.collection_select_move)).perform(click())
            waitFor { selecting(scenario, fragment) }
            tapRow(scenario, fragment, 0)
            openSelectionOverflow()
            assertMenuEnabled(R.string.enqueue_stream, true)
            assertMenuEnabled(R.string.playlist_copy_to_playlist, true)
            assertMenuEnabled(R.string.playlist_download_selected, false)
            savePreview("playlist-selection-local-media")
            pressBack()
            onView(withId(R.id.playlist_edit_toggle)).perform(click())
            onView(withId(R.id.playlist_edit_toggle)).perform(click())
            scenario.onActivity { fragment.setContextualSearchQuery("") }
            waitFor { rowCount(scenario, fragment) == originalIds.size }
            assertEquals("Leaving Edit while filtered must retain hidden playlist entries", originalIds, persistedIds())
        }
    }

    private fun withPlaylist(mode: LocalPlaylistSortMode = LocalPlaylistSortMode.PLAYLIST_ORDER, block: (ActivityScenario<SettingsActivity>, LocalPlaylistFragment) -> Unit) {
        LocalPlaylistSortPreferences.set(context, profile, playlistId, mode)
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            lateinit var fragment: LocalPlaylistFragment
            scenario.onActivity { activity ->
                fragment = LocalPlaylistFragment.getInstance(playlistId, "Selection fixture")
                activity.supportFragmentManager.beginTransaction().replace(R.id.settings_fragment_holder, fragment).commitNow()
                // Keep the settings-only expanded header from clipping the playlist fixture.
                activity.findViewById<AppBarLayout>(R.id.settings_app_bar).setExpanded(false, false)
            }
            waitFor { rowCount(scenario, fragment) == originalIds.size }
            block(scenario, fragment)
        }
    }

    private fun tapRow(scenario: ActivityScenario<SettingsActivity>, fragment: LocalPlaylistFragment, itemIndex: Int) {
        var position = RecyclerView.NO_POSITION
        scenario.onActivity {
            val recycler = fragment.requireView().findViewById<RecyclerView>(R.id.items_list)
            position = adapter(fragment).getAdapterPositionForItemIndex(itemIndex)
            (recycler.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, 0)
        }
        waitFor {
            var visible = false
            scenario.onActivity {
                val recycler = fragment.requireView().findViewById<RecyclerView>(R.id.items_list)
                val row = recycler.findViewHolderForAdapterPosition(position)?.itemView
                visible = row != null && !row.isLayoutRequested && isDisplayingAtLeast(90).matches(row)
            }
            visible
        }
        onView(object : TypeSafeMatcher<View>() {
            override fun describeTo(description: Description) {
                description.appendText("playlist row at adapter position $position")
            }
            override fun matchesSafely(view: View): Boolean {
                val parent = view.parent as? RecyclerView ?: return false
                return parent.id == R.id.items_list && parent.getChildAdapterPosition(view) == position
            }
        }).perform(click())
    }

    private fun chooseAction(id: Int, title: Int) {
        try {
            onView(allOf(withId(id), isDescendantOfA(isAssignableFrom(ActionBarContextView::class.java)), isDisplayed())).perform(click())
        } catch (_: NoMatchingViewException) {
            openSelectionOverflow()
            onView(withText(title)).perform(click())
        }
    }

    private fun assertSelectionCount(count: Int) {
        onView(withText(context.getString(R.string.collection_selected, count)))
            .check(matches(isDisplayed()))
            .check { view, error ->
                if (error != null) throw error
                val layout = (view as TextView).layout
                assertTrue("The selected count must be laid out", layout.lineCount > 0)
                assertEquals("The selected count must remain fully visible", 0, layout.getEllipsisCount(0))
            }
    }

    private fun openSelectionOverflow() {
        onView(
            allOf(
                withContentDescription(androidx.appcompat.R.string.abc_action_menu_overflow_description),
                isDescendantOfA(isAssignableFrom(ActionBarContextView::class.java)),
                isDisplayed()
            )
        ).perform(click())
    }

    private fun assertMenuEnabled(title: Int, expected: Boolean) {
        onView(withText(title)).check { view, error ->
            if (error != null) throw error
            var current: View? = view
            var enabled = true
            while (current != null) {
                enabled = enabled && current.isEnabled
                current = current.parent as? View
            }
            assertEquals("Action eligibility must match the selected media", expected, enabled)
        }
    }

    private fun adapter(fragment: LocalPlaylistFragment) = fragment.requireView().findViewById<RecyclerView>(R.id.items_list).adapter as LocalItemListAdapter

    private fun selecting(scenario: ActivityScenario<SettingsActivity>, fragment: LocalPlaylistFragment): Boolean {
        var selecting = false
        scenario.onActivity { selecting = adapter(fragment).isSelecting }
        return selecting
    }

    private fun rowCount(scenario: ActivityScenario<SettingsActivity>, fragment: LocalPlaylistFragment): Int {
        var count = 0
        scenario.onActivity { count = adapter(fragment).itemsList.size }
        return count
    }

    private fun persistedIds() = database.playlistStreamDAO().getQuickAddEntries(playlistId).map { it.streamUid }

    private fun waitFor(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        do {
            if (condition()) return
            SystemClock.sleep(25)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Playlist UI did not reach its expected state")
    }

    private fun savePreview(name: String) {
        instrumentation.waitForIdleSync()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
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

    private fun remote(id: String, title: String) = StreamEntity(
        serviceId = 0,
        url = "https://example.test/video/$id",
        title = title,
        streamType = StreamType.VIDEO_STREAM,
        duration = 60,
        uploader = "Example channel"
    )
}
