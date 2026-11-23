/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.history

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.local.LocalItemListAdapter
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.settings.tabs.Tab
import org.schabi.newpipe.settings.tabs.TabsManager
import org.schabi.newpipe.testUtil.TestDatabase

@RunWith(AndroidJUnit4::class)
class HistoryInitialLoadingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private lateinit var database: AppDatabase
    private lateinit var manager: HistoryRecordManager
    private lateinit var profile: String
    private var originalTabs: String? = null
    private var originalStarPromptCompleted: Boolean? = null

    @Before
    fun setup() {
        originalTabs = preferences.getString(context.getString(R.string.saved_tabs_key), null)
        originalStarPromptCompleted = preferences.all["github_star_prompt_completed"] as? Boolean
        // Keep MainActivity's initial home independent of network content and launch counters.
        TabsManager.getManager(context).saveTabs(listOf(Tab.Type.BLANK.tab))
        preferences.edit().putBoolean("github_star_prompt_completed", true).commit()
        database = TestDatabase.createReplacingNewPipeDatabase()
        profile = ProfileManager.getActiveProfileId(context)
        manager = HistoryRecordManager(context, profile)
    }

    @After
    fun tearDown() {
        try {
            if (::database.isInitialized) database.close()
        } finally {
            preferences.edit().apply {
                putString(context.getString(R.string.saved_tabs_key), originalTabs)
                originalStarPromptCompleted?.let { putBoolean("github_star_prompt_completed", it) }
                    ?: remove("github_star_prompt_completed")
            }.commit()
        }
    }

    @Test
    fun previewIsTheStableFullPrefixWithProfileCountsAndProgress() {
        seedHistory(210)
        val history = database.streamHistoryDAO()
        val state = database.streamStateDAO()
        history.insert(StreamHistoryEntity(150, date(BASE_TIME - 86_400_000), 7, profile))
        history.insert(StreamHistoryEntity(150, date(BASE_TIME + 86_400_000), 999, "other-profile"))
        state.insert(StreamStateEntity(150, 90_000, "other-profile"))
        assertEquals(1, state.deleteStateForProfile(profile, 10))
        state.insert(StreamStateEntity(10, 90_000, "other-profile"))

        val subscriber = manager.getStreamStatisticsForDisplay(true).test()
        try {
            subscriber.awaitCount(2).assertNoErrors()
            assertTrue(subscriber.values().size >= 2)
            val preview = subscriber.values()[0]
            val complete = subscriber.values()[1]
            assertFalse(preview.isComplete)
            assertTrue(complete.isComplete)
            assertEquals(100, preview.entries.size)
            assertEquals(210, complete.entries.size)
            assertEquals(complete.entries.take(100), preview.entries)
            assertEquals(listOf(1L, 2L, 3L, 4L, 5L), preview.entries.take(5).map { it.streamId })
            assertEquals(8L, complete.entries.single { it.streamId == 150L }.watchCount)
            assertEquals(150_000L, complete.entries.single { it.streamId == 150L }.progressMillis)
            assertEquals(0L, complete.entries.single { it.streamId == 10L }.progressMillis)

            assertEquals(1, state.update(StreamStateEntity(1, 42_000, profile)))
            awaitCondition {
                subscriber.values().last().entries.first().progressMillis == 42_000L
            }
            subscriber.assertNoErrors()
            assertTrue(subscriber.values().drop(1).all { it.isComplete })
            assertEquals(210, subscriber.values().last().entries.size)
            assertEquals(42_000L, subscriber.values().last().entries.first().progressMillis)
        } finally {
            subscriber.cancel()
        }
        val other = history.getRecentStatisticsForProfile("other-profile", 100).blockingGet()
        assertEquals(1, other.size)
        assertEquals(999L, other.single().watchCount)
        assertEquals(90_000L, other.single().progressMillis)
    }

    @Test
    fun completeOnlyLoadingAndEmptyHistoryNeverReportAPartialResultAsComplete() {
        val empty = manager.getStreamStatisticsForDisplay(true).take(2).toList().blockingGet()
        assertEquals(listOf(false, true), empty.map { it.isComplete })
        assertTrue(empty.all { it.entries.isEmpty() })

        seedHistory(12)
        val snapshot = manager.getStreamStatisticsForDisplay(false).blockingFirst()
        assertTrue(snapshot.isComplete)
        assertEquals(12, snapshot.entries.size)
    }

    @Test
    fun recreationRestoresAScrollPositionBeyondThePreview() {
        seedHistory(250)
        launchMainActivity().use { scenario ->
            scenario.onActivity { activity ->
                activity.supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_holder, StatisticsPlaylistFragment())
                    .addToBackStack(null).commit()
                activity.supportFragmentManager.executePendingTransactions()
            }
            fun withList(action: (RecyclerView, LocalItemListAdapter) -> Unit) {
                scenario.onActivity { activity ->
                    val fragment = activity.supportFragmentManager
                        .findFragmentById(R.id.fragment_holder)!!
                    val list = fragment.requireView().findViewById<RecyclerView>(R.id.items_list)
                    action(list, list.adapter as LocalItemListAdapter)
                }
            }
            awaitCondition {
                var ready = false
                withList { _, adapter -> ready = adapter.itemsList.size == 250 }
                ready
            }
            withList { list, adapter ->
                (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                    adapter.getAdapterPositionForItemIndex(180),
                    0
                )
            }
            fun atSavedPosition(): Boolean {
                var ready = false
                withList { list, adapter ->
                    val layout = list.layoutManager as LinearLayoutManager
                    ready = adapter.itemsList.size == 250 &&
                        adapter.getItemIndex(layout.findFirstVisibleItemPosition()) == 180
                }
                return ready
            }
            awaitCondition { atSavedPosition() }
            scenario.recreate()
            awaitCondition { atSavedPosition() }
        }
    }

    /**
     * One large fixture: reports actual Room hydration and first nonempty layout on the device.
     * Timings are observations, not pass/fail thresholds that depend on emulator speed.
     */
    @LargeTest
    @Test(timeout = 120_000)
    fun largeHistoryReportsRoomAndFirstLayoutCosts() {
        val count = 67_000
        val seedStart = SystemClock.elapsedRealtimeNanos()
        seedHistory(count)
        val seedMs = elapsedMillis(seedStart)
        Log.i("HistoryLoadingBenchmark", "streams=$count seed_ms=$seedMs")
        val fullStart = SystemClock.elapsedRealtimeNanos()
        val fullPrefix = manager.streamStatistics.blockingFirst().also {
            assertEquals(count, it.size)
        }.take(100)
        val fullRoomMs = elapsedMillis(fullStart)
        Log.i("HistoryLoadingBenchmark", "streams=$count full_room_ms=$fullRoomMs")
        val previewStart = SystemClock.elapsedRealtimeNanos()
        val preview = database.streamHistoryDAO()
            .getRecentStatisticsForProfile(profile, HistoryRecordManager.INITIAL_HISTORY_SIZE)
            .blockingGet()
        val previewRoomMs = elapsedMillis(previewStart)
        assertEquals(fullPrefix, preview)

        val firstLayout = CountDownLatch(1)
        val completeLayout = CountDownLatch(1)
        var firstLayoutMs = 0L
        var completeLayoutMs = 0L
        var firstLayoutRows = 0
        var maxAttachedChildren = 0
        var completeViewportHeight = 0
        var completeHostHeight = 0
        launchMainActivity().use { scenario ->
            scenario.onActivity { activity ->
                val fragment = StatisticsPlaylistFragment()
                val start = SystemClock.elapsedRealtimeNanos()
                activity.supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_holder, fragment).addToBackStack(null).commit()
                activity.supportFragmentManager.executePendingTransactions()
                val list = fragment.requireView().findViewById<RecyclerView>(R.id.items_list)
                var attachedChildrenPeak = list.childCount
                list.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
                    override fun onChildViewAttachedToWindow(view: View) {
                        attachedChildrenPeak = maxOf(attachedChildrenPeak, list.childCount)
                    }

                    override fun onChildViewDetachedFromWindow(view: View) = Unit
                })
                list.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        val rows = (list.adapter as LocalItemListAdapter).itemsList.size
                        if (rows > 0 && list.childCount > 0 && firstLayout.count > 0) {
                            firstLayoutRows = rows
                            firstLayoutMs = elapsedMillis(start)
                            firstLayout.countDown()
                        }
                        if (rows == count && list.childCount > 0) {
                            completeLayoutMs = elapsedMillis(start)
                            maxAttachedChildren = attachedChildrenPeak
                            completeViewportHeight = list.height
                            completeHostHeight = activity.findViewById<View>(R.id.fragment_holder).height
                            list.viewTreeObserver.removeOnPreDrawListener(this)
                            completeLayout.countDown()
                        }
                        return true
                    }
                })
            }
            assertTrue("History did not lay out", firstLayout.await(30, TimeUnit.SECONDS))
            assertTrue("Complete history did not lay out", completeLayout.await(30, TimeUnit.SECONDS))
            assertTrue(
                "History must recycle offscreen rows; attached up to $maxAttachedChildren of $count",
                maxAttachedChildren in 1 until HistoryRecordManager.INITIAL_HISTORY_SIZE
            )
            assertTrue(
                "History viewport $completeViewportHeight must fit the main container $completeHostHeight",
                completeViewportHeight in 1..completeHostHeight
            )
        }
        Log.i(
            "HistoryLoadingBenchmark",
            "streams=$count seed_ms=$seedMs full_room_ms=$fullRoomMs preview_room_ms=$previewRoomMs " +
                "first_pre_draw_ms=$firstLayoutMs first_pre_draw_rows=$firstLayoutRows " +
                "complete_pre_draw_ms=$completeLayoutMs max_attached_children=$maxAttachedChildren " +
                "viewport_height=$completeViewportHeight host_height=$completeHostHeight"
        )
    }

    private fun launchMainActivity(): ActivityScenario<MainActivity> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        // Use the bounded production history host; ACTION_VIEW skips unrelated launch notices.
        return ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW))
    }

    private fun seedHistory(count: Int) {
        val sqlite = database.openHelper.writableDatabase
        val stream = sqlite.compileStatement(
            "INSERT INTO streams (uid, service_id, url, title, stream_type, duration, uploader) " +
                "VALUES (?, 0, ?, ?, 'VIDEO_STREAM', 600, 'History fixture')"
        )
        val history = sqlite.compileStatement(
            "INSERT INTO stream_history (stream_id, access_date, repeat_count, profile_id) " +
                "VALUES (?, ?, ?, ?)"
        )
        val state = sqlite.compileStatement(
            "INSERT INTO stream_state (stream_id, progress_time, profile_id) VALUES (?, ?, ?)"
        )
        try {
            database.runInTransaction {
                for (id in 1..count) {
                    stream.bindLong(1, id.toLong())
                    stream.bindString(2, "https://example.com/history/$id")
                    stream.bindString(3, "History video $id")
                    stream.executeInsert()
                    history.bindLong(1, id.toLong())
                    history.bindLong(2, BASE_TIME - id / 3 * 1000L)
                    history.bindLong(3, 1L + id % 3)
                    history.bindString(4, profile)
                    history.executeInsert()
                    state.bindLong(1, id.toLong())
                    state.bindLong(2, id * 1000L)
                    state.bindString(3, profile)
                    state.executeInsert()
                }
            }
        } finally {
            stream.close()
            history.close()
            state.close()
        }
    }

    private fun date(millis: Long): OffsetDateTime = OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC)

    private fun elapsedMillis(startNanos: Long) = TimeUnit.NANOSECONDS.toMillis(SystemClock.elapsedRealtimeNanos() - startNanos)

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        do {
            if (condition()) return
            SystemClock.sleep(25)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("History did not reach the expected state")
    }

    companion object {
        private const val BASE_TIME = 1_750_000_000_000L
    }
}
