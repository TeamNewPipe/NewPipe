/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.history

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.processors.PublishProcessor
import io.reactivex.rxjava3.schedulers.TestScheduler
import java.time.OffsetDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.LocalItemListAdapter
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.settings.tabs.Tab
import org.schabi.newpipe.settings.tabs.TabsManager
import org.schabi.newpipe.testUtil.TestDatabase

@RunWith(AndroidJUnit4::class)
class HistoryPreviewUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val context = instrumentation.targetContext
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val computation = TestScheduler()
    private val previousComputationHandler = RxJavaPlugins.getComputationSchedulerHandler()
    private lateinit var database: AppDatabase
    private val full = (1L..150L).map { entry(it) }
    private var originalTabs: String? = null
    private var originalStarPromptCompleted: Boolean? = null

    @Before
    fun setup() {
        originalTabs = preferences.getString(context.getString(R.string.saved_tabs_key), null)
        originalStarPromptCompleted = preferences.all["github_star_prompt_completed"] as? Boolean
        // Keep MainActivity's initial home independent of network content and launch counters.
        TabsManager.getManager(context).saveTabs(listOf(Tab.Type.BLANK.tab))
        preferences.edit().putBoolean("github_star_prompt_completed", true).commit()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        database = TestDatabase.createReplacingNewPipeDatabase()
        RxJavaPlugins.setComputationSchedulerHandler { computation }
    }

    @After
    fun tearDown() {
        RxJavaPlugins.setComputationSchedulerHandler(previousComputationHandler)
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
    fun previewCannotCreatePartialQueuesAndSearchWaitsForAllHistory() {
        withHistory { scenario, fragment ->
            emit(scenario, fragment, full.take(100), false)
            awaitRows(scenario, fragment, 100)
            onActivity(scenario) {
                assertControlsEnabled(fragment, false)
                assertEquals(0, fragment.playQueue.size())
                assertEquals(View.GONE, fragment.requireView().findViewById<View>(R.id.history_date_jump_button).visibility)
                fragment.source.onNext(HistoryLoadSnapshot(full, true))
            }
            onMain {}
            // The database is complete, but the visible adapter still holds only the preview.
            onActivity(scenario) {
                assertControlsEnabled(fragment, false)
                assertEquals(0, fragment.playQueue.size())
                fragment.setContextualSearchQuery("Video 150")
            }
            flushProcessing()
            awaitRows(scenario, fragment, 1)
            onActivity(scenario) {
                assertControlsEnabled(fragment, true)
                assertEquals(full.last().streamEntity.url, fragment.playQueue.streams.single().url)
                fragment.setContextualSearchQuery("")
            }
            flushProcessing()
            awaitRows(scenario, fragment, 150)
            onActivity(scenario) {
                assertEquals(150, fragment.playQueue.size())
                assertEquals(View.VISIBLE, fragment.requireView().findViewById<View>(R.id.history_date_jump_button).visibility)
                fragment.source.onNext(HistoryLoadSnapshot(full.take(100), false))
            }
            onMain {}
            assertEquals(150, rows(scenario, fragment))
        }
    }

    @Test
    fun savedSortDateAndWatchFiltersUseTheCompleteList() {
        for (mode in listOf("EARLIEST_PLAYED", "MOST_PLAYED", "LEAST_PLAYED", "TITLE")) {
            withHistory(configure = { it.historySortMode = mode }) { scenario, fragment ->
                assertFalse(fragment.requestedPreview)
                emit(scenario, fragment, full.take(100), false)
                assertEquals(0, rows(scenario, fragment))
                emit(scenario, fragment, full, true)
                awaitRows(scenario, fragment, 150)
                onActivity(scenario) {
                    assertEquals(
                        HistoryViewOptions.sort(full, mode).map { it.streamEntity.url },
                        fragment.playQueue.streams.map { it.url }
                    )
                }
            }
        }
        val day = full.last().latestAccessDate.toLocalDate().toEpochDay()
        withHistory(configure = {
            it.historyDateFrom = day
            it.historyDateTo = day
            it.selectedStreamFilters = "UNWATCHED"
        }) { scenario, fragment ->
            assertFalse(fragment.requestedPreview)
            emit(scenario, fragment, full.take(100), false)
            assertEquals(0, rows(scenario, fragment))
            emit(scenario, fragment, full, true)
            awaitRows(scenario, fragment, 1)
            onActivity(scenario) {
                assertEquals(full.last().streamEntity.url, fragment.playQueue.streams.single().url)
            }
        }
    }

    @Test
    fun previewScrollAnchorSurvivesNewRowsAndMetadataUpdates() {
        withHistory { scenario, fragment ->
            emit(scenario, fragment, full.take(100), false)
            awaitRows(scenario, fragment, 100)
            onActivity(scenario) {
                val list = list(fragment)
                (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                    adapter(fragment).getAdapterPositionForItemIndex(40),
                    -12
                )
            }
            awaitCondition {
                var reached = false
                onActivity(scenario) { reached = visibleAnchor(fragment)?.first == full[40].streamId }
                reached
            }
            var before: Pair<Long, Int>? = null
            onActivity(scenario) { before = visibleAnchor(fragment) }
            val changed = listOf(entry(0)) + full.map { it.copy(progressMillis = 13_000) }
            emit(scenario, fragment, changed, true)
            awaitRows(scenario, fragment, 151)
            awaitCondition {
                var after: Pair<Long, Int>? = null
                onActivity(scenario) { after = visibleAnchor(fragment) }
                after == before
            }
        }
    }

    @Test
    fun deferredPlaybackResolvesTheSelectedIdInTheCompleteUpdatedQueue() {
        withHistory { scenario, fragment ->
            emit(scenario, fragment, full.take(100), false)
            awaitRows(scenario, fragment, 100)
            onActivity(scenario) {
                fragment.playHistoryItem(42, true, true)
                assertNull(fragment.startedQueue)
            }
            val updated = listOf(entry(0)) + full.map { it.copy(watchCount = 99, progressMillis = 12_000) }
            emit(scenario, fragment, updated, true)
            awaitRows(scenario, fragment, 151)
            onActivity(scenario) {
                val queue = fragment.startedQueue
                assertNotNull(queue)
                assertEquals(151, queue!!.size())
                assertEquals(42, queue.index)
                assertEquals(full[41].streamEntity.url, queue.streams[queue.index].url)
                assertTrue(fragment.startedBackground)
                assertTrue(fragment.startedResume)
            }
        }
    }

    @Test
    fun changingTheViewOrLeavingCancelsDeferredPlayback() {
        for (leave in listOf(false, true)) {
            withHistory { scenario, fragment ->
                emit(scenario, fragment, full.take(100), false)
                awaitRows(scenario, fragment, 100)
                onActivity(scenario) { fragment.playHistoryItem(42, false, false) }
                if (leave) {
                    scenario.moveToState(Lifecycle.State.CREATED)
                } else {
                    onActivity(scenario) { fragment.setContextualSearchQuery("Video 150") }
                }
                emit(scenario, fragment, full, true)
                onActivity(scenario) { assertNull(fragment.startedQueue) }
            }
        }
    }

    @Test
    fun reloadDisposesThePreviousLoadAndProcessingBeforeItCanReplaceTheNewView() {
        withHistory { scenario, fragment ->
            emit(scenario, fragment, full.take(100), false)
            awaitRows(scenario, fragment, 100)
            val oldSource = fragment.source
            onActivity(scenario) {
                oldSource.onNext(HistoryLoadSnapshot(full, true))
            }
            onMain {}
            onActivity(scenario) {
                fragment.source = PublishProcessor.create()
                fragment.startLoading(true)
                assertFalse(oldSource.hasSubscribers())
                oldSource.onNext(HistoryLoadSnapshot(full, true))
            }
            flushProcessing()
            assertEquals(0, rows(scenario, fragment))
            emit(scenario, fragment, listOf(entry(999)), true)
            awaitRows(scenario, fragment, 1)
            onActivity(scenario) {
                assertEquals(entry(999).streamEntity.url, fragment.playQueue.streams.single().url)
            }
        }
    }

    private fun withHistory(
        configure: (ControlledHistoryFragment) -> Unit = {},
        block: (ActivityScenario<MainActivity>, ControlledHistoryFragment) -> Unit
    ) {
        // Use the bounded production history host; ACTION_VIEW skips unrelated launch notices.
        val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            lateinit var fragment: ControlledHistoryFragment
            try {
                onActivity(scenario) { activity ->
                    fragment = ControlledHistoryFragment().also(configure)
                    activity.supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_holder, fragment).addToBackStack(null).commit()
                    activity.supportFragmentManager.executePendingTransactions()
                }
                block(scenario, fragment)
            } finally {
                // Remove the loading view before ActivityScenario.close waits for an idle loop,
                // including when an assertion fails while the controlled source is still pending.
                onActivity(scenario) { activity ->
                    val current = activity.supportFragmentManager.findFragmentById(R.id.fragment_holder)
                    if (current is ControlledHistoryFragment) {
                        activity.supportFragmentManager.beginTransaction()
                            .remove(current).commitNowAllowingStateLoss()
                    }
                }
            }
        }
    }

    private fun emit(
        scenario: ActivityScenario<MainActivity>,
        fragment: ControlledHistoryFragment,
        entries: List<StreamStatisticsEntry>,
        complete: Boolean
    ) {
        onActivity(scenario) { fragment.source.onNext(HistoryLoadSnapshot(entries, complete)) }
        flushProcessing()
    }

    private fun flushProcessing() {
        // FIFO barriers finish posted snapshots and results while the loading indicator animates.
        onMain {}
        computation.triggerActions()
        onMain {}
    }

    private fun onActivity(scenario: ActivityScenario<MainActivity>, action: (MainActivity) -> Unit) {
        // Off-main onActivity waits for idle before running the action. On Android 6, a pending
        // controlled source keeps loading animated, so emit and inspect through the main loop.
        onMain { scenario.onActivity { action(it) } }
    }

    private fun onMain(action: () -> Unit) {
        val completed = CountDownLatch(1)
        var failure: Throwable? = null
        mainHandler.post {
            try {
                action()
            } catch (error: Throwable) {
                failure = error
            } finally {
                completed.countDown()
            }
        }
        assertTrue("History main-thread action did not finish", completed.await(5, SECONDS))
        failure?.let { throw it }
    }

    private fun list(fragment: StatisticsPlaylistFragment) = fragment.requireView().findViewById<RecyclerView>(R.id.items_list)

    private fun adapter(fragment: StatisticsPlaylistFragment) = list(fragment).adapter as LocalItemListAdapter

    private fun rows(scenario: ActivityScenario<MainActivity>, fragment: StatisticsPlaylistFragment): Int {
        var count = 0
        onActivity(scenario) { count = adapter(fragment).itemsList.size }
        return count
    }

    private fun awaitRows(scenario: ActivityScenario<MainActivity>, fragment: StatisticsPlaylistFragment, count: Int) {
        awaitCondition {
            var ready = false
            onActivity(scenario) {
                ready = adapter(fragment).itemsList.size == count && list(fragment).childCount > 0
            }
            ready
        }
    }

    private fun visibleAnchor(fragment: StatisticsPlaylistFragment): Pair<Long, Int>? {
        val list = list(fragment)
        val layout = list.layoutManager as LinearLayoutManager
        val position = layout.findFirstVisibleItemPosition()
        val index = adapter(fragment).getItemIndex(position)
        val view = layout.findViewByPosition(position) ?: return null
        val item = adapter(fragment).itemsList.getOrNull(index) as? StreamStatisticsEntry ?: return null
        return item.streamId to layout.getDecoratedTop(view) - list.paddingTop
    }

    private fun assertControlsEnabled(fragment: StatisticsPlaylistFragment, enabled: Boolean) {
        for (id in listOf(R.id.playlist_ctrl_play_all_button, R.id.playlist_ctrl_play_bg_button, R.id.playlist_ctrl_play_popup_button)) {
            assertEquals(enabled, fragment.requireView().findViewById<View>(id).isEnabled)
        }
        assertEquals(
            if (enabled) View.GONE else View.VISIBLE,
            fragment.requireView().findViewById<View>(R.id.history_loading_older).visibility
        )
    }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        do {
            if (condition()) return
            SystemClock.sleep(25)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("History UI did not reach the expected state")
    }

    class ControlledHistoryFragment : StatisticsPlaylistFragment() {
        var source = PublishProcessor.create<HistoryLoadSnapshot>()
        var requestedPreview = false
        var startedQueue: PlayQueue? = null
        var startedBackground = false
        var startedResume = false

        override fun getHistoryForDisplay(showPreview: Boolean): Flowable<HistoryLoadSnapshot> {
            requestedPreview = showPreview
            return source
        }

        override fun startHistoryPlayback(queue: PlayQueue, background: Boolean, resume: Boolean) {
            startedQueue = queue
            startedBackground = background
            startedResume = resume
        }
    }

    companion object {
        private fun entry(id: Long) = StreamStatisticsEntry(
            streamEntity = StreamEntity(
                uid = id,
                serviceId = 0,
                url = "https://example.com/history/$id",
                title = "Video ${id.toString().padStart(3, '0')}",
                streamType = StreamType.VIDEO_STREAM,
                duration = 600,
                uploader = "Uploader $id",
                sourceType = if (id == 42L) StreamEntity.SOURCE_TYPE_LOCAL else StreamEntity.SOURCE_TYPE_REMOTE
            ),
            progressMillis = if (id > 100) 0 else 600_000,
            streamId = id,
            latestAccessDate = OffsetDateTime.parse("2026-11-22T12:00:00Z").minusDays(id / 10),
            watchCount = id % 5 + 1
        )
    }
}
