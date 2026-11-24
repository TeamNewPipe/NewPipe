package org.schabi.newpipe.recommendations

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.children
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.TestScheduler
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.info_list.InfoListAdapter
import org.schabi.newpipe.local.history.IncognitoMode
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.views.NewPipeRecyclerView

@RunWith(AndroidJUnit4::class)
class RecommendationsRefreshTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val context = instrumentation.targetContext
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val modelPreferences = context.getSharedPreferences(LocalRecommendations.PREFERENCES, Context.MODE_PRIVATE)
    private val computation = TestScheduler()
    private val previousComputationHandler = RxJavaPlugins.getComputationSchedulerHandler()
    private val originalProfile = ProfileManager.getActiveProfileId(context)
    private val originalIncognito = IncognitoMode.isEnabled(context)
    private val serviceKey = context.getString(R.string.current_service_key)
    private val searchHistoryKey = context.getString(R.string.enable_search_history_key)
    private val originalService = preferences.getString(serviceKey, null)
    private val originalSearchHistory = preferences.all[searchHistoryKey] as? Boolean
    private val profiles = mutableListOf<String>()
    private lateinit var profile: String
    private val astronomyVideo = item("AAAAAAAAAAA", "Astronomy lesson")
    private val astronomyShort = item("BBBBBBBBBBB", "Astronomy short", short = true)
    private val cookingShort = item("CCCCCCCCCCC", "Cooking short", short = true)

    @Before
    fun setup() {
        IncognitoMode.setEnabled(context, false)
        ServiceHelper.setSelectedServiceId(context, 0)
        preferences.edit().putBoolean(searchHistoryKey, true).commit()
        profile = createProfile()
        LocalRecommendations.configure(context, mapOf("enabled" to true), "")
        LocalRecommendations.searched(context, 0, "astronomy", profile)
        LocalRecommendations.observe(context, listOf(astronomyVideo, astronomyShort, cookingShort))
        LocalRecommendations.configure(context, mapOf("paused" to true), "")
        RxJavaPlugins.setComputationSchedulerHandler { computation }
    }

    @After
    fun tearDown() {
        RxJavaPlugins.setComputationSchedulerHandler(previousComputationHandler)
        ProfileManager.setActiveProfile(context, originalProfile)
        profiles.forEach { ProfileManager.deleteProfile(context, it) }
        IncognitoMode.setEnabled(context, originalIncognito)
        preferences.edit().apply {
            putString(serviceKey, originalService)
            originalSearchHistory?.let { putBoolean(searchHistoryKey, it) } ?: remove(searchHistoryKey)
        }.commit()
    }

    @Test
    fun menuForcesUnchangedSnapshotAndPreservesFilterInterestAndRestoredState() {
        val before = modelPreferences.all.toMap()
        withFeed { scenario ->
            finishLoad(scenario)
            selectShortsAndAstronomy(scenario)
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertLoading(scenario, false)

            // The revision is unchanged. Only a forced menu refresh should start another load.
            repeat(2) { refresh(scenario) }
            assertLoading(scenario, true)
            finishLoad(scenario)
            assertSelection(scenario, astronomy = true, expectedUrls = listOf(astronomyShort.url))
            assertEquals("Refreshing paused learning must leave the stored model/settings intact", before, modelPreferences.all)

            // recreate() waits for an idle loop after relaunch. Let the restored local load
            // finish on its real scheduler so Android 6's indicator cannot hold that wait open.
            RxJavaPlugins.setComputationSchedulerHandler(previousComputationHandler)
            try {
                scenario.recreate()
            } finally {
                RxJavaPlugins.setComputationSchedulerHandler { computation }
            }
            finishLoad(scenario)
            assertSelection(scenario, astronomy = true, expectedUrls = listOf(astronomyShort.url))
            assertEquals(before, modelPreferences.all)
        }
    }

    @Test
    fun refreshUpdatesInterestChipsAndClearsOnlyAnUnavailableInterest() {
        withFeed { scenario ->
            finishLoad(scenario)
            selectShortsAndAstronomy(scenario)
            onActivity(scenario) {
                LocalRecommendations.removeInterests(context, profile, setOf("astronomy"))
            }
            val beforeRefresh = modelPreferences.all.toMap()
            refresh(scenario)
            assertLoading(scenario, true)
            finishLoad(scenario)
            assertSelection(scenario, astronomy = false, expectedUrls = listOf(astronomyShort.url, cookingShort.url))
            assertEquals(beforeRefresh, modelPreferences.all)
        }
    }

    @Test
    fun returningToLoadedProfileCancelsAnObsoleteLoadAndFinishesTheIndicator() {
        val other = createProfile()
        LocalRecommendations.configure(context, mapOf("enabled" to true), "")
        LocalRecommendations.observe(context, listOf(item("DDDDDDDDDDD", "Other profile video")))
        assertTrue(ProfileManager.setActiveProfile(context, profile))
        val before = modelPreferences.all.toMap()
        withFeed { scenario ->
            finishLoad(scenario)
            onActivity(scenario) {
                assertTrue(ProfileManager.setActiveProfile(context, other))
                assertTrue(ProfileManager.setActiveProfile(context, profile))
            }
            assertLoading(scenario, true)
            finishLoad(scenario)
            onActivity(scenario) { activity ->
                assertEquals(setOf(astronomyVideo.url, astronomyShort.url, cookingShort.url), rows(fragment(activity)).toSet())
            }
            assertEquals(before, modelPreferences.all)
        }
    }

    @Test
    fun refreshCompletesForEmptyDisabledAndIncognitoStatesWithoutChangingLearningData() {
        createProfile()
        LocalRecommendations.configure(context, mapOf("enabled" to true), "")
        withFeed { scenario ->
            finishLoad(scenario)
            val emptyModel = modelPreferences.all.toMap()
            refresh(scenario)
            assertLoading(scenario, true)
            finishLoad(scenario)
            assertEmpty(scenario)
            assertEquals(emptyModel, modelPreferences.all)

            onActivity(scenario) { LocalRecommendations.configure(context, mapOf("enabled" to false), "") }
            val disabledModel = modelPreferences.all.toMap()
            refresh(scenario)
            assertLoading(scenario, false)
            assertEmpty(scenario)
            assertFalse(LocalRecommendations.setting(context, "enabled"))
            assertEquals(disabledModel, modelPreferences.all)

            onActivity(scenario) { assertTrue(ProfileManager.setActiveProfile(context, profile)) }
            finishLoad(scenario)
            onActivity(scenario) { IncognitoMode.setEnabled(context, true) }
            val privateModel = modelPreferences.all.toMap()
            refresh(scenario)
            assertLoading(scenario, false)
            assertEmpty(scenario)
            assertEquals(privateModel, modelPreferences.all)
            assertTrue(LocalRecommendations.setting(context, "paused"))
        }
    }

    private fun withFeed(action: (ActivityScenario<AboutActivity>) -> Unit) {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            try {
                onActivity(scenario) { activity ->
                    activity.supportFragmentManager.beginTransaction()
                        .replace(android.R.id.content, RecommendationsFragment(), TAG)
                        .commitNow()
                }
                action(scenario)
            } finally {
                // Stop a pending indicator before close() waits for the main loop to become idle.
                onActivity(scenario) { activity ->
                    activity.supportFragmentManager.findFragmentByTag(TAG)?.let { fragment ->
                        activity.supportFragmentManager.beginTransaction().remove(fragment).commitNowAllowingStateLoss()
                    }
                }
            }
        }
    }

    private fun createProfile(): String = requireNotNull(
        ProfileManager.createProfile(context, "Refresh test " + System.nanoTime())
    ).id.also(profiles::add)

    private fun fragment(activity: AboutActivity): RecommendationsFragment = activity.supportFragmentManager.findFragmentByTag(TAG) as RecommendationsFragment

    private fun refresh(scenario: ActivityScenario<AboutActivity>) {
        onActivity(scenario) { activity ->
            val fragment = fragment(activity)
            val menu = PopupMenu(activity, fragment.requireView()).menu
            fragment.onCreateOptionsMenu(menu, activity.menuInflater)
            val refresh = menu.findItem(R.id.action_refresh_recommendations)
            assertNotNull("The feed must expose an accessible refresh action", refresh)
            assertEquals(activity.getString(R.string.local_recommendations_refresh), refresh.title)
            assertNotNull(refresh.icon)
            assertTrue(menu.performIdentifierAction(R.id.action_refresh_recommendations, 0))
        }
    }

    private fun finishLoad(scenario: ActivityScenario<AboutActivity>) {
        // Drain queued snapshots/results without waiting for the animated loading view to idle.
        onMain {}
        computation.triggerActions()
        onMain {}
        assertLoading(scenario, false)
    }

    private fun onActivity(scenario: ActivityScenario<AboutActivity>, action: (AboutActivity) -> Unit) {
        // Android 6 can keep its loop busy while the controlled source leaves a spinner running.
        // Calling from the main thread avoids onActivity's off-main waitForIdleSync barrier.
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
        assertTrue("Recommendation main-thread action did not finish", completed.await(5, SECONDS))
        failure?.let { throw it }
    }

    private fun assertLoading(scenario: ActivityScenario<AboutActivity>, expected: Boolean) = awaitCondition {
        var matches = false
        onActivity(scenario) { activity ->
            val progress = fragment(activity).requireView().findViewById<View>(R.id.loading_progress_bar)
            matches = (progress.visibility == View.VISIBLE) == expected
        }
        matches
    }

    private fun selectShortsAndAstronomy(scenario: ActivityScenario<AboutActivity>) {
        awaitCondition {
            var available = false
            onActivity(scenario) { activity ->
                available = interestChips(fragment(activity)).any { it.text.toString() == "astronomy" }
            }
            available
        }
        onActivity(scenario) { activity ->
            val fragment = fragment(activity)
            fragment.requireView().findViewById<Chip>(R.id.recommendation_filter_shorts).performClick()
            interestChips(fragment).first { it.text.toString() == "astronomy" }.performClick()
        }
        assertSelection(scenario, astronomy = true, expectedUrls = listOf(astronomyShort.url))
    }

    private fun assertSelection(scenario: ActivityScenario<AboutActivity>, astronomy: Boolean, expectedUrls: List<String>) = awaitCondition {
        var matches = false
        onActivity(scenario) { activity ->
            val fragment = fragment(activity)
            val shorts = fragment.requireView().findViewById<Chip>(R.id.recommendation_filter_shorts)
            val selected = interestChips(fragment).filter { it.isChecked }.map { it.text.toString() }
            val expectedInterests = if (astronomy) listOf("astronomy") else emptyList()
            matches = shorts?.isChecked == true && selected == expectedInterests &&
                rows(fragment).toSet() == expectedUrls.toSet()
        }
        matches
    }

    private fun interestChips(fragment: RecommendationsFragment): List<Chip> = fragment.requireView().findViewById<ChipGroup>(R.id.recommendation_interest_chips)
        ?.children?.filterIsInstance<Chip>()?.toList().orEmpty()

    private fun rows(fragment: RecommendationsFragment): List<String> = (fragment.requireView().findViewById<NewPipeRecyclerView>(R.id.items_list).adapter as InfoListAdapter)
        .itemsList.filterIsInstance<StreamInfoItem>().map { it.url }

    private fun assertEmpty(scenario: ActivityScenario<AboutActivity>) = awaitCondition {
        var empty = false
        onActivity(scenario) { activity ->
            val fragment = fragment(activity)
            empty = rows(fragment).isEmpty() &&
                fragment.requireView().findViewById<View>(R.id.empty_state_view).visibility == View.VISIBLE
        }
        empty
    }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(20)
        }
        assertTrue("The local refresh UI did not reach the expected state", condition())
    }

    private fun item(id: String, title: String, short: Boolean = false) = StreamInfoItem(0, "https://www.youtube.com/watch?v=" + id, title, StreamType.VIDEO_STREAM).apply {
        isShortFormContent = short
        duration = if (short) 30 else 600
        uploaderName = "Test channel"
    }

    private companion object {
        const val TAG = "recommendations-refresh-test"
    }
}
