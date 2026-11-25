package org.schabi.newpipe.local.feed

import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.appbar.AppBarLayout
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.databinding.FragmentFeedBinding
import org.schabi.newpipe.local.subscription.FeedGroupIcon
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.settings.SettingsActivity
import org.schabi.newpipe.testUtil.TestDatabase
import org.schabi.newpipe.util.NavigationHelper

@RunWith(AndroidJUnit4::class)
class FeedRefreshNavigationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val floatingKey = context.getString(R.string.feed_floating_refresh_key)
    private val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply { isAccessible = true }
    private var previousDatabase: Any? = null
    private var previousFloating: Boolean? = null
    private lateinit var database: AppDatabase
    private var groupId = 0L

    @Before
    fun setUp() {
        previousFloating = preferences.all[floatingKey] as? Boolean
        preferences.edit().putBoolean(floatingKey, false).commit()
        previousDatabase = databaseField.get(null)
        database = TestDatabase.createReplacingNewPipeDatabase()
        groupId = database.feedGroupDAO().insert(
            FeedGroupEntity(0, "Refresh test group", FeedGroupIcon.EDUCATION, profileId = ProfileManager.getActiveProfileId(context))
        )
    }

    @After
    fun tearDown() {
        preferences.edit().apply {
            previousFloating?.let { putBoolean(floatingKey, it) } ?: remove(floatingKey)
        }.commit()
        databaseField.set(null, previousDatabase)
        database.close()
    }

    @Test
    fun homeAndSubscriptionRoutesAllowRefreshWithExpandedAndRestoredCollapsedHeaders() {
        for (nested in listOf(true, false)) {
            for (id in listOf(FeedGroupEntity.GROUP_ALL_ID, groupId)) {
                withFeed(nested, id) { scenario ->
                    await(scenario) { it.view != null && binding(it).refreshRootView.isShown }
                    scenario.onActivity { activity ->
                        val fragment = feed(activity, nested)
                        assertEquals(nested, fragment.parentFragment != null)
                        assertFalse(binding(fragment).swipeRefreshLayout.canChildScrollUp())
                        binding(fragment).feedHeader.setExpanded(false, false)
                    }
                    await(scenario) { !it.feedHeaderExpanded }
                    scenario.onActivity { activity ->
                        val fragment = feed(activity, nested)
                        assertFalse("A collapsed header must not block refresh at the top", binding(fragment).swipeRefreshLayout.canChildScrollUp())
                        val manager = manager(activity, nested)
                        val restored = FeedFragment.newInstance(id, "Refresh test group")
                        restored.setInitialSavedState(manager.saveFragmentInstanceState(fragment))
                        manager.beginTransaction().replace(R.id.fragment_holder, restored).commitNow()
                    }
                    await(scenario) {
                        !it.feedHeaderExpanded && binding(it).swipeRefreshLayout.height > 0 &&
                            binding(it).refreshRootView.visibility == View.VISIBLE
                    }
                    var refreshes = 0
                    scenario.onActivity { activity ->
                        val swipe = binding(feed(activity, nested)).swipeRefreshLayout
                        assertFalse("Restored Home and standalone feeds must use the same gesture", swipe.canChildScrollUp())
                        // Count the real gesture without starting a network refresh.
                        swipe.setOnRefreshListener { refreshes++ }
                    }
                    onView(withId(R.id.swipeRefreshLayout)).perform(pullDown())
                    scenario.onActivity { assertEquals("The restored feed must accept the pull gesture", 1, refreshes) }
                }
            }
        }
    }

    @Test
    fun contentAboveTheViewportBlocksRefreshInBothNavigationRoutes() {
        for (nested in listOf(true, false)) {
            withFeed(nested, FeedGroupEntity.GROUP_ALL_ID) { scenario ->
                await(scenario) { binding(it).refreshRootView.isShown }
                var refreshes = 0
                scenario.onActivity { activity ->
                    val binding = binding(feed(activity, nested))
                    binding.itemsList.apply {
                        layoutManager = LinearLayoutManager(context)
                        adapter = Rows()
                        visibility = View.VISIBLE
                        scrollToPosition(20)
                    }
                    binding.swipeRefreshLayout.setOnRefreshListener { refreshes++ }
                }
                await(scenario) { binding(it).itemsList.canScrollVertically(-1) }
                scenario.onActivity { activity -> assertTrue(binding(feed(activity, nested)).swipeRefreshLayout.canChildScrollUp()) }
                onView(withId(R.id.swipeRefreshLayout)).perform(pullDown())
                scenario.onActivity { activity ->
                    assertEquals("Scrolling through feed content must not refresh it", 0, refreshes)
                    binding(feed(activity, nested)).itemsList.scrollToPosition(0)
                    binding(feed(activity, nested)).feedHeader.setExpanded(false, false)
                }
                await(scenario) { !binding(it).itemsList.canScrollVertically(-1) && !it.feedHeaderExpanded }
                onView(withId(R.id.swipeRefreshLayout)).perform(pullDown())
                scenario.onActivity { assertEquals(1, refreshes) }
            }
        }
    }

    @Test
    fun floatingPreferenceKeepsItsRefreshAndCancelControlsInBothNavigationRoutes() {
        for (nested in listOf(true, false)) {
            withFeed(nested, groupId) { scenario ->
                await(scenario) { binding(it).refreshRootView.isShown }
                scenario.onActivity { activity ->
                    val fragment = feed(activity, nested)
                    val binding = binding(fragment)
                    assertTrue(binding.swipeRefreshLayout.isEnabled)
                    assertEquals(View.GONE, binding.feedFloatingRefresh.visibility)
                    preferences.edit().putBoolean(floatingKey, true).commit()
                    assertTrue(binding.feedFloatingRefresh.isShown)
                    assertFalse(binding.swipeRefreshLayout.isEnabled)
                    assertEquals(View.GONE, binding.refreshIcon.visibility)
                    fragment.handleResult(FeedState.ProgressState(1, 4))
                    assertEquals(activity.getString(R.string.cancel_refresh), binding.feedFloatingRefresh.contentDescription)
                    assertEquals(View.GONE, binding.cancelRefreshButton.visibility)
                    assertTrue(binding.feedFloatingRefresh.performClick())
                    assertFalse("The floating Cancel action must disable repeated cancellation", binding.feedFloatingRefresh.isEnabled)
                    fragment.handleResult(FeedState.LoadedState(emptyList(), null, 0, emptyList()))
                    assertTrue(binding.feedFloatingRefresh.isEnabled)
                    assertEquals(activity.getString(R.string.local_media_refresh), binding.feedFloatingRefresh.contentDescription)
                    preferences.edit().putBoolean(floatingKey, false).commit()
                    assertTrue(binding.swipeRefreshLayout.isEnabled)
                    assertEquals(View.GONE, binding.feedFloatingRefresh.visibility)
                    fragment.handleResult(FeedState.ProgressState(1, 4))
                    assertTrue(binding.cancelRefreshButton.isShown)
                    assertTrue(binding.cancelRefreshButton.performClick())
                    assertFalse(binding.cancelRefreshButton.isEnabled)
                }
            }
        }
    }

    private fun withFeed(nested: Boolean, id: Long, test: (ActivityScenario<SettingsActivity>) -> Unit) {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<AppBarLayout>(R.id.settings_app_bar).setExpanded(false, false)
                val host = RouteHost().apply { arguments = bundleOf(NESTED to nested, GROUP to id) }
                activity.supportFragmentManager.beginTransaction().replace(R.id.settings_fragment_holder, host, HOST).commitNow()
                if (!nested) {
                    // Subscriptions uses this navigation helper and the activity's manager.
                    NavigationHelper.openFeedFragment(activity.supportFragmentManager, id, "Refresh test group")
                    activity.supportFragmentManager.executePendingTransactions()
                }
            }
            test(scenario)
        }
    }

    private fun manager(activity: SettingsActivity, nested: Boolean): FragmentManager = if (nested) {
        activity.supportFragmentManager.findFragmentByTag(HOST)!!.childFragmentManager
    } else {
        activity.supportFragmentManager
    }

    private fun feed(activity: SettingsActivity, nested: Boolean): FeedFragment = manager(activity, nested)
        .findFragmentById(R.id.fragment_holder) as FeedFragment

    private fun binding(fragment: FeedFragment) = FragmentFeedBinding.bind(fragment.requireView())

    private fun pullDown(): ViewAction = object : ViewAction {
        override fun getConstraints(): Matcher<View> = allOf(isDisplayed(), isAssignableFrom(SwipeRefreshLayout::class.java))

        override fun getDescription() = "Pull down within the feed's visible viewport"

        override fun perform(uiController: UiController, view: View) {
            // The app bar's scrolling behavior can lay out content below the window edge.
            // Use screen-visible coordinates instead of swiping through a clipped bottom edge.
            GeneralSwipeAction(
                Swipe.SLOW,
                { visiblePoint(it, 0.1f) },
                { visiblePoint(it, 0.9f) },
                Press.FINGER
            ).perform(uiController, view)
        }
    }

    private fun visiblePoint(view: View, fraction: Float): FloatArray {
        val visible = Rect()
        assertTrue("The feed must have a visible gesture surface", view.getGlobalVisibleRect(visible))
        return floatArrayOf(visible.exactCenterX(), visible.top + visible.height() * fraction)
    }

    private fun await(scenario: ActivityScenario<SettingsActivity>, ready: (FeedFragment) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            var complete = false
            scenario.onActivity { activity ->
                val nested = activity.supportFragmentManager.findFragmentByTag(HOST)!!.requireArguments().getBoolean(NESTED)
                complete = ready(feed(activity, nested))
            }
            if (complete) {
                instrumentation.waitForIdleSync()
                return
            }
            SystemClock.sleep(25)
        }
        throw AssertionError("Feed did not reach the expected layout state")
    }

    class RouteHost : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = FrameLayout(requireContext()).apply { id = R.id.fragment_holder }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            if (requireArguments().getBoolean(NESTED) && childFragmentManager.findFragmentById(R.id.fragment_holder) == null) {
                // Home's pager owns the feed through a child FragmentManager.
                childFragmentManager.beginTransaction().add(
                    R.id.fragment_holder,
                    FeedFragment.newInstance(requireArguments().getLong(GROUP), "Refresh test group")
                ).commitNow()
            }
        }
    }

    private class Rows : RecyclerView.Adapter<Rows.Row>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Row(
            TextView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (64 * resources.displayMetrics.density).toInt())
            }
        )

        override fun onBindViewHolder(holder: Row, position: Int) {
            (holder.itemView as TextView).text = "Feed row $position"
        }

        override fun getItemCount() = 60

        class Row(view: View) : RecyclerView.ViewHolder(view)
    }

    companion object {
        private const val HOST = "feed-refresh-route"
        private const val NESTED = "nested"
        private const val GROUP = "group"
    }
}
