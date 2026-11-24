package org.schabi.newpipe.local.subscription

import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.Item
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.databinding.FragmentSubscriptionBinding
import org.schabi.newpipe.local.subscription.dialog.FeedGroupDialog
import org.schabi.newpipe.local.subscription.item.FeedGroupAddNewGridItem
import org.schabi.newpipe.local.subscription.item.FeedGroupAddNewItem
import org.schabi.newpipe.local.subscription.item.FeedGroupCardGridItem
import org.schabi.newpipe.local.subscription.item.FeedGroupCardItem
import org.schabi.newpipe.local.subscription.item.FeedGroupImportGridItem
import org.schabi.newpipe.local.subscription.item.FeedGroupImportItem
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.testUtil.TestDatabase

@RunWith(AndroidJUnit4::class)
class SubscriptionFloatingActionsIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val styleKey = context.getString(R.string.subscription_actions_style_key)
    private val importKey = context.getString(R.string.show_subscription_import_shortcut_key)
    private lateinit var collapseKey: String
    private lateinit var previous: Map<String, *>
    private lateinit var database: AppDatabase
    private val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply { isAccessible = true }
    private var previousDatabase: Any? = null

    @Before
    fun setUp() {
        val profile = ProfileManager.getActiveProfileId(context)
        collapseKey = SubscriptionGroupsCollapse.key(profile)
        previous = preferences.all.filterKeys { it in listOf(styleKey, importKey, collapseKey) }
        preferences.edit().remove(styleKey).remove(importKey).putBoolean(collapseKey, false).commit()
        previousDatabase = databaseField.get(null)
        database = TestDatabase.createReplacingNewPipeDatabase()
        database.feedGroupDAO().insert(FeedGroupEntity(uid = 0, name = "Keep this group", icon = FeedGroupIcon.EDUCATION, profileId = profile))
    }

    @After
    fun tearDown() {
        val editor = preferences.edit().remove(styleKey).remove(importKey).remove(collapseKey)
        previous.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
            }
        }
        try {
            assertTrue(editor.commit())
        } finally {
            databaseField.set(null, previousDatabase)
            database.close()
        }
    }

    @Test
    fun livePresentationChangesKeepGroupsAndTheConfiguredSearchRouteAcrossRecreation() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.supportFragmentManager.beginTransaction().add(android.R.id.content, ActionsHostFragment(), HOST_TAG).commitNow()
            }
            for (listMode in listOf(true, false)) {
                scenario.onActivity { activity ->
                    preferences.edit().putString(styleKey, "classic").putBoolean(importKey, true).commit()
                    ViewModelProvider(host(activity).subscriptions())[SubscriptionViewModel::class.java].setListViewMode(listMode)
                }
                val original = awaitRows(scenario, listMode, showCreate = true, showImport = true)
                val groups = original.filter(::isGroup)
                scenario.onActivity { preferences.edit().putString(styleKey, "floating").commit() }
                val floating = awaitRows(scenario, listMode, showCreate = false, showImport = true)
                assertTrue(isImport(floating.first()))
                groups.zip(floating.filter(::isGroup)).forEach { (before, after) -> assertSame(before, after) }

                scenario.onActivity { preferences.edit().putBoolean(importKey, false).commit() }
                val noShortcuts = awaitRows(scenario, listMode, showCreate = false, showImport = false)
                groups.zip(noShortcuts).forEach { (before, after) -> assertSame(before, after) }
                scenario.onActivity { preferences.edit().putBoolean(importKey, true).putString(styleKey, "classic").commit() }
                val restored = awaitRows(scenario, listMode, showCreate = true, showImport = true)
                groups.zip(restored.filter(::isGroup)).forEach { (before, after) -> assertSame(before, after) }
            }

            scenario.onActivity { preferences.edit().putString(styleKey, "floating").commit() }
            awaitRows(scenario, listMode = false, showCreate = false, showImport = true)
            scenario.recreate()
            awaitRows(scenario, listMode = false, showCreate = false, showImport = true)
            scenario.onActivity { activity ->
                val host = host(activity)
                val fragment = host.subscriptions()
                val binding = FragmentSubscriptionBinding.bind(fragment.requireView())
                assertTrue(fragment.usesFloatingSearchAction())
                host.openLink = true
                host.notifySubscriptionActionsChanged()
                assertEquals(activity.getString(R.string.open_link), binding.subscriptionFloatingSearch.contentDescription)
                binding.subscriptionFloatingSearch.performClick()
                assertEquals(1, host.searches)
                assertTrue("The group-specific filter remains available", binding.itemsList.findViewById<View>(R.id.header_search).isShown)
                assertTrue("The channel-specific filter remains available", binding.itemsList.findViewById<View>(R.id.channels_header_search).isShown)

                host.searchAllowed = false
                host.notifySubscriptionActionsChanged()
                assertFalse(fragment.usesFloatingSearchAction())
                assertEquals(View.GONE, binding.subscriptionFloatingSearch.visibility)
                assertTrue(binding.subscriptionFloatingCreate.isShown)
                host.searchAllowed = true
                host.contextualEntry = true
                host.notifySubscriptionActionsChanged()
                assertEquals(View.GONE, binding.subscriptionFloatingActions.visibility)
                assertEquals(0, binding.itemsList.paddingBottom)
                host.contextualEntry = false
                host.notifySubscriptionActionsChanged()
                assertTrue(fragment.usesFloatingSearchAction())

                binding.itemsList.findViewById<View>(R.id.channels_header_edit).performClick()
                assertEquals(View.GONE, binding.subscriptionFloatingActions.visibility)
                assertFalse(fragment.usesFloatingSearchAction())
                binding.subscriptionSortHeader.channelsHeaderEdit.performClick()
                assertTrue(fragment.usesFloatingSearchAction())

                host.childFragmentManager.beginTransaction().setMaxLifecycle(fragment, Lifecycle.State.STARTED).commitNow()
                assertEquals("A neighboring pager page must not keep floating actions", View.GONE, binding.subscriptionFloatingActions.visibility)
                assertFalse(fragment.usesFloatingSearchAction())
                host.childFragmentManager.beginTransaction().setMaxLifecycle(fragment, Lifecycle.State.RESUMED).commitNow()
                assertTrue(fragment.usesFloatingSearchAction())
            }

            database.feedGroupDAO().deleteAll()
            awaitRows(scenario, listMode = false, showCreate = false, showImport = true, groupCount = 1)
            scenario.onActivity { activity ->
                val fragment = host(activity).subscriptions()
                val binding = FragmentSubscriptionBinding.bind(fragment.requireView())
                binding.itemsList.findViewById<View>(R.id.header_toggle_collapse).performClick()
                assertTrue("Even an empty, collapsed group section keeps its creation entry point", binding.subscriptionFloatingCreate.isShown)
                binding.subscriptionFloatingCreate.performClick()
                activity.supportFragmentManager.executePendingTransactions()
                val dialog = activity.supportFragmentManager.fragments.filterIsInstance<FeedGroupDialog>().single()
                assertTrue(dialog.requireDialog().isShowing)
                dialog.dismissNow()
            }
        }
    }

    private fun awaitRows(
        scenario: ActivityScenario<AboutActivity>,
        listMode: Boolean,
        showCreate: Boolean,
        showImport: Boolean,
        groupCount: Int = 2
    ): List<Item<*>> {
        val deadline = SystemClock.uptimeMillis() + 10_000
        var rows = emptyList<Item<*>>()
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { activity ->
                val carousel = host(activity).subscriptions().view?.findViewById<RecyclerView>(R.id.recycler_view)
                val adapter = carousel?.adapter as? GroupAdapter<*>
                rows = adapter?.let { (0 until it.itemCount).map(it::getItem) }.orEmpty()
            }
            val correctMode = rows.filter(::isGroup).all { if (listMode) it is FeedGroupCardItem else it is FeedGroupCardGridItem }
            if (correctMode && rows.count(::isGroup) == groupCount && rows.any(::isCreate) == showCreate && rows.any(::isImport) == showImport) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                return rows
            }
            SystemClock.sleep(25)
        }
        throw AssertionError("Expected list=$listMode, create=$showCreate, import=$showImport; received ${rows.map { it.javaClass.simpleName }}")
    }

    private fun host(activity: AboutActivity) = activity.supportFragmentManager.findFragmentByTag(HOST_TAG) as ActionsHostFragment

    private fun isGroup(item: Item<*>) = item is FeedGroupCardItem || item is FeedGroupCardGridItem
    private fun isCreate(item: Item<*>) = item is FeedGroupAddNewItem || item is FeedGroupAddNewGridItem
    private fun isImport(item: Item<*>) = item is FeedGroupImportItem || item is FeedGroupImportGridItem

    class ActionsHostFragment : Fragment(), SubscriptionActionsHost {
        var searchAllowed = true
        var contextualEntry = false
        var openLink = false
        var searches = 0

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = FrameLayout(requireContext()).apply { id = R.id.fragment_holder }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            if (childFragmentManager.findFragmentByTag(CHILD_TAG) == null) {
                childFragmentManager.beginTransaction()
                    .add(R.id.fragment_holder, SubscriptionFragment().apply { useAsFrontPage(true) }, CHILD_TAG).commitNow()
            }
        }

        fun subscriptions() = childFragmentManager.findFragmentByTag(CHILD_TAG) as SubscriptionFragment

        override fun performConfiguredSearchAction() {
            searches++
        }
        override fun getConfiguredSearchLabel(): String = getString(if (openLink) R.string.open_link else R.string.search)
        override fun getConfiguredSearchIcon(): Int = if (openLink) R.drawable.ic_link else R.drawable.ic_search
        override fun isConfiguredSearchAvailable(): Boolean = searchAllowed && !contextualEntry
        override fun isContextualSearchOpen(): Boolean = contextualEntry
        override fun notifySubscriptionActionsChanged() {
            (childFragmentManager.findFragmentByTag(CHILD_TAG) as? SubscriptionFragment)?.onMainNavigationActionsChanged()
        }
    }

    companion object {
        private const val HOST_TAG = "subscription-actions-host"
        private const val CHILD_TAG = "subscription-actions-child"
    }
}
