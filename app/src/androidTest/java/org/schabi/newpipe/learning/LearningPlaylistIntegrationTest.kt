/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.learning

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.databinding.FragmentDescriptionBinding
import org.schabi.newpipe.databinding.RelatedItemsHeaderBinding
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.fragments.MainFragment
import org.schabi.newpipe.fragments.list.playlist.PlaylistFragment
import org.schabi.newpipe.local.LocalItemListAdapter
import org.schabi.newpipe.local.bookmark.BookmarkFragment
import org.schabi.newpipe.local.playlist.LocalPlaylistFragment
import org.schabi.newpipe.local.playlist.LocalPlaylistManager
import org.schabi.newpipe.player.playqueue.LocalMediaPlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.settings.tabs.Tab
import org.schabi.newpipe.settings.tabs.TabsManager
import org.schabi.newpipe.testUtil.TestDatabase
import org.schabi.newpipe.util.InfoCache

class LearningPlaylistIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply { isAccessible = true }
    private val managerField = LearningContentManager::class.java.getDeclaredField("instance").apply { isAccessible = true }
    private var oldDatabase: Any? = null
    private var oldManager: Any? = null
    private lateinit var database: AppDatabase
    private lateinit var manager: LearningContentManager
    private lateinit var profile: String
    private lateinit var savedPreferences: Map<String, Any?>
    private var playlistId = 0L
    private val lessons = listOf(lesson("first", "Introduction"), lesson("second", "Your next lesson"))

    @Before
    fun setup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        oldDatabase = databaseField.get(null)
        oldManager = managerField.get(null)
        database = TestDatabase.createReplacingNewPipeDatabase()
        managerField.set(null, null)
        manager = LearningContentManager.getInstance(context)
        profile = ProfileManager.getActiveProfileId(context)
        val keys = listOf(context.getString(R.string.learning_mode_key), LearningMode.profilePreferenceKey(profile), context.getString(R.string.learning_playlist_navigation_key), context.getString(R.string.saved_tabs_key))
        savedPreferences = keys.associateWith { prefs.all[it] }
        prefs.edit().putBoolean(keys[0], true).putBoolean(keys[1], true).putBoolean(keys[2], true).commit()
        LocalPlaylistManager(database, profile).createPlaylist("Local course", lessons.map(::StreamEntity)).blockingGet()
        playlistId = database.playlistDAO().getAllDirectForProfile(profile).single().uid
    }

    @After
    fun cleanup() {
        InfoCache.getInstance().removeInfo(0, REMOTE_URL, InfoCache.Type.PLAYLIST)
        (LearningContentManager::class.java.getDeclaredField("disposables").apply { isAccessible = true }.get(manager) as CompositeDisposable).dispose()
        managerField.set(null, oldManager)
        databaseField.set(null, oldDatabase)
        database.close()
        prefs.edit().apply {
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
    fun learningHomeOpensFirstSurvivesRecreationAndRespectsProfileInclusion() {
        val tabs = TabsManager.getManager(context)
        tabs.saveTabs(listOf(Tab.Type.LEARNING.tab, Tab.Type.BOOKMARKS.tab))
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            waitFor {
                var ready = false
                onActivity(scenario) { activity ->
                    val main = activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) as? MainFragment
                    ready = main?.childFragmentManager?.fragments?.any { it is LearningDashboardFragment && it.view != null } == true
                }
                ready
            }
            savePreview("learning-home")
            scenario.recreate()
            waitFor {
                var ready = false
                onActivity(scenario) { activity ->
                    val main = activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) as? MainFragment
                    ready = main?.childFragmentManager?.fragments?.any { it is LearningDashboardFragment && it.view != null } == true
                }
                ready
            }
            onActivity(scenario) { prefs.edit().putBoolean(LearningMode.profilePreferenceKey(profile), false).commit() }
            assertEquals(listOf(Tab.Type.BOOKMARKS.tab), tabs.visibleTabs)
            assertEquals(Tab.Type.LEARNING.tab, tabs.tabs.first())
            waitFor {
                var hidden = false
                onActivity(scenario) { activity ->
                    val main = activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) as? MainFragment
                    hidden = main != null && main.childFragmentManager.fragments.none { it is LearningDashboardFragment && it.view != null }
                }
                hidden
            }
            onActivity(scenario) { prefs.edit().putBoolean(LearningMode.profilePreferenceKey(profile), true).commit() }
            assertEquals(Tab.Type.LEARNING.tab, tabs.visibleTabs.first())
            waitFor {
                var restored = false
                onActivity(scenario) { activity ->
                    val main = activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) as? MainFragment
                    restored = main?.childFragmentManager?.fragments?.any { it is LearningDashboardFragment && it.view != null } == true
                }
                restored
            }
        } finally {
            onActivity(scenario) { it.finish() }
            scenario.close()
        }
    }

    @Test
    fun bothBookmarkMenusMarkLearningAndLocalRenameUsesTheSameDialogStyle() {
        cacheRemotePlaylist()
        database.playlistRemoteDAO().insert(PlaylistRemoteEntity(serviceId = 0, orderingName = "Remote course", url = REMOTE_URL, thumbnailUrl = null, uploader = "Teacher", streamCount = 2, profileId = profile))
        val scenario = ActivityScenario.launch(AboutActivity::class.java)
        try {
            onActivity(scenario) { activity ->
                activity.setContentView(FrameLayout(activity).apply { id = R.id.fragment_holder })
                activity.supportFragmentManager.beginTransaction().replace(R.id.fragment_holder, BookmarkFragment()).commitNow()
            }
            longPress(scenario, "Local course")
            savePreview("learning-local-playlist-menu")
            clickDialogText(context.getString(R.string.learning_mark_content))
            waitFor { manager.isLocalPlaylistMarked(playlistId) }
            waitForBookmarkSource(scenario, LearningContentManager.localPlaylistSourceId(playlistId))
            longPress(scenario, "Local course")
            waitFor { findDialogText(context.getString(R.string.learning_remove_content)) != null }
            clickDialogText(context.getString(R.string.rename))
            waitFor { findDialogText(context.getString(R.string.cancel)) != null }
            savePreview("learning-playlist-rename")
            clickDialogText(context.getString(R.string.cancel))
            longPress(scenario, "Remote course")
            savePreview("learning-remote-playlist-menu")
            clickDialogText(context.getString(R.string.learning_mark_content))
            waitFor { manager.isRemotePlaylistMarked(0, REMOTE_URL) && manager.isStreamLearning(0, lessons[1].url) }
            waitForBookmarkSource(scenario, LearningContentManager.remotePlaylistSourceId(0, REMOTE_URL))
            // Removal remains available offline, without re-extracting the playlist.
            InfoCache.getInstance().removeInfo(0, REMOTE_URL, InfoCache.Type.PLAYLIST)
            longPress(scenario, "Remote course")
            clickDialogText(context.getString(R.string.learning_remove_content))
            waitFor { !manager.isRemotePlaylistMarked(0, REMOTE_URL) }
        } finally {
            onActivity(scenario) { it.finish() }
            scenario.close()
        }
    }

    @Test
    fun filteredPlaylistsKeepTheSelectedLessonsOriginalPosition() {
        manager.setLocalPlaylistMarked(playlistId, "Local course", true).blockingAwait()
        cacheRemotePlaylist()
        LearningPlaylistMarker.markRemote(manager, 0, REMOTE_URL).blockingAwait()
        val scenario = ActivityScenario.launch(AboutActivity::class.java)
        try {
            lateinit var local: LocalPlaylistFragment
            onActivity(scenario) { activity ->
                activity.setContentView(FrameLayout(activity).apply { id = R.id.fragment_holder })
                local = LocalPlaylistFragment.getInstance(playlistId, "Local course")
                activity.supportFragmentManager.beginTransaction().replace(R.id.fragment_holder, local).commitNow()
            }
            waitFor {
                var loaded = false
                onActivity(scenario) { loaded = local.playQueue.size() == 2 }
                loaded
            }
            onActivity(scenario) {
                local.setContextualSearchQuery("next lesson")
                assertEquals(2, local.playQueue.size())
                assertEquals(1, local.playQueue.index)
                assertNotNull(local.playQueue.learningPlaylistContext)
                local.setContextualSearchQuery("")
                val adapter = local.requireView().findViewById<RecyclerView>(R.id.items_list).adapter as LocalItemListAdapter
                assertTrue(adapter.swapItems(1, 2))
                assertEquals("Your next lesson", local.playQueue.item!!.title)
                assertEquals(0, local.playQueue.index)
                assertEquals("Introduction", local.playQueue.getItem(1)!!.title)
            }
            lateinit var remote: PlaylistFragment
            onActivity(scenario) { activity ->
                remote = PlaylistFragment.getInstance(0, REMOTE_URL, "Remote course")
                activity.supportFragmentManager.beginTransaction().replace(R.id.fragment_holder, remote).commitNow()
            }
            waitFor {
                var loaded = false
                onActivity(scenario) { loaded = descendants(remote.requireView()).filterIsInstance<TextView>().any { it.text.toString() == "Your next lesson" } }
                loaded
            }
            onActivity(scenario) {
                remote.setContextualSearchQuery("next lesson")
                assertEquals(2, remote.playQueue.size())
                assertEquals(1, remote.playQueue.index)
                assertNotNull(remote.playQueue.learningPlaylistContext)
            }
        } finally {
            onActivity(scenario) { it.finish() }
            scenario.close()
        }
    }

    @Test
    fun coursePanelsShowTheNextLessonAndEndAndHideWhenDisabled() {
        manager.setLocalPlaylistMarked(playlistId, "Local course", true).blockingAwait()
        val queue = LocalMediaPlayQueue(lessons.map(::PlayQueueItem), 0).apply {
            learningPlaylistContext = LearningPlaylistContext(LearningContentManager.localPlaylistSourceId(playlistId), "Local course", profile)
        }
        val scenario = ActivityScenario.launch(AboutActivity::class.java)
        try {
            lateinit var description: FragmentDescriptionBinding
            lateinit var related: RelatedItemsHeaderBinding
            var opened = false
            onActivity(scenario) { activity ->
                val container = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                description = FragmentDescriptionBinding.inflate(activity.layoutInflater, container, false)
                related = RelatedItemsHeaderBinding.inflate(activity.layoutInflater, container, false)
                container.addView(description.root)
                container.addView(related.root)
                activity.setContentView(container)
                org.schabi.newpipe.util.EdgeToEdgeHelper.applySystemBarPadding(container)
                LearningPlaylistPanel.render(description.learningPlaylistPanel, queue, 0, lessons[0].url, false) { opened = true }
                LearningPlaylistPanel.render(related.learningPlaylistPanel, queue, 0, lessons[0].url, true) { opened = true }
                assertTrue(description.learningPlaylistPanel.root.isVisible)
                assertEquals(context.getString(R.string.learning_lesson_position, 1, 2), description.learningPlaylistPanel.learningCoursePosition.text.toString())
                assertFalse(description.learningPlaylistPanel.learningNextVideo.isVisible)
                assertEquals("Your next lesson", related.learningPlaylistPanel.learningNextTitle.text.toString())
                related.learningPlaylistPanel.learningNextVideo.performClick()
                assertTrue(opened)
            }
            savePreview("learning-course-next")
            onActivity(scenario) {
                queue.setIndex(1)
                LearningPlaylistPanel.render(related.learningPlaylistPanel, queue, 0, lessons[1].url, true) {}
                assertFalse(related.learningPlaylistPanel.learningNextVideo.isVisible)
                assertTrue(related.learningPlaylistPanel.learningCourseStatus.isVisible)
                assertEquals(context.getString(R.string.learning_playlist_end), related.learningPlaylistPanel.learningCourseStatus.text.toString())
                prefs.edit().putBoolean(context.getString(R.string.learning_playlist_navigation_key), false).commit()
                LearningPlaylistPanel.render(related.learningPlaylistPanel, queue, 0, lessons[1].url, true) {}
                assertFalse(related.learningPlaylistPanel.root.isVisible)
            }
        } finally {
            onActivity(scenario) { it.finish() }
            scenario.close()
        }
    }

    private fun cacheRemotePlaylist() {
        val constructor = PlaylistInfo::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType, ListLinkHandler::class.java, String::class.java).apply { isAccessible = true }
        val info = constructor.newInstance(0, ServiceList.YouTube.playlistLHFactory.fromUrl(REMOTE_URL), "Remote course").apply {
            relatedItems = lessons
            streamCount = 2
            uploaderName = "Teacher"
        }
        InfoCache.getInstance().putInfo(0, REMOTE_URL, info, InfoCache.Type.PLAYLIST)
    }

    private fun longPress(scenario: ActivityScenario<AboutActivity>, title: String) {
        // Accessibility clicks and dialog dismissal are asynchronous. Do not accept the old
        // menu's Delete entry as evidence that the next menu has appeared.
        waitFor { findDialogText(context.getString(R.string.delete)) == null }
        waitFor {
            var pressed = false
            onActivity(scenario) { activity ->
                var view: View? = descendants(activity.window.decorView).filterIsInstance<TextView>().firstOrNull { it.text.toString() == title }
                while (view != null && !pressed) {
                    pressed = view.performLongClick()
                    view = view.parent as? View
                }
            }
            pressed
        }
        waitFor { findDialogText(context.getString(R.string.delete)) != null }
    }

    private fun waitForBookmarkSource(scenario: ActivityScenario<AboutActivity>, sourceId: String) {
        // The manager and fragment observe Room separately. Reopen the menu only after the
        // fragment has received the mark; an already opened menu keeps its original labels.
        val sourcesField = BookmarkFragment::class.java.getDeclaredField("learningSources").apply { isAccessible = true }
        waitFor {
            var observed = false
            onActivity(scenario) { activity ->
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_holder) as BookmarkFragment
                observed = (sourcesField.get(fragment) as Set<*>).contains(sourceId)
            }
            observed
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }

    private fun findDialogText(text: String): AccessibilityNodeInfo? = instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.firstOrNull { it.text?.toString() == text }

    private fun clickDialogText(text: String) {
        waitFor {
            var node = findDialogText(text)
            while (node != null) {
                if (node.isClickable) return@waitFor node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                node = node.parent
            }
            false
        }
    }

    private fun savePreview(name: String) {
        instrumentation.waitForIdleSync()
        val image = instrumentation.uiAutomation.takeScreenshot() ?: return
        val file = File(context.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        val target = "/sdcard/Download/wizestream-player-layout-previews"
        listOf("mkdir -p $target", "cp ${file.absolutePath} $target/").forEach { command ->
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        }
    }

    private fun waitFor(condition: () -> Boolean) {
        repeat(100) {
            if (condition()) return
            Thread.sleep(50)
        }
        throw AssertionError("Learning UI did not reach its expected state")
    }

    private fun <T : AppCompatActivity> onActivity(scenario: ActivityScenario<T>, action: (T) -> Unit) {
        val done = CountDownLatch(1)
        val error = AtomicReference<Throwable>()
        Handler(Looper.getMainLooper()).post {
            try {
                scenario.onActivity(action)
            } catch (failure: Throwable) {
                error.set(failure)
            } finally {
                done.countDown()
            }
        }
        assertTrue("Main-thread action timed out", done.await(10, TimeUnit.SECONDS))
        error.get()?.let { throw it }
    }

    companion object {
        private const val REMOTE_URL = "https://www.youtube.com/playlist?list=PLlearning000000000000000000000000"
        private fun lesson(id: String, title: String) = StreamInfoItem(0, "https://www.youtube.com/watch?v=$id", title, StreamType.VIDEO_STREAM).apply {
            duration = 120
            uploaderName = "Teacher"
        }
    }
}
