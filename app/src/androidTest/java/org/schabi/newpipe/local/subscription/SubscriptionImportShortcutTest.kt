package org.schabi.newpipe.local.subscription

import android.app.Application
import android.os.SystemClock
import android.view.View
import android.widget.PopupMenu
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.Item
import org.junit.After
import org.junit.Assert.assertEquals
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
import org.schabi.newpipe.local.subscription.item.FeedGroupAddNewGridItem
import org.schabi.newpipe.local.subscription.item.FeedGroupAddNewItem
import org.schabi.newpipe.local.subscription.item.FeedGroupImportGridItem
import org.schabi.newpipe.local.subscription.item.FeedGroupImportItem
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.testUtil.TestDatabase

@RunWith(AndroidJUnit4::class)
class SubscriptionImportShortcutTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val shortcutKey = context.getString(R.string.show_subscription_import_shortcut_key)
    private lateinit var collapseKey: String
    private lateinit var previous: Map<String, *>
    private lateinit var database: AppDatabase
    private val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply { isAccessible = true }
    private var previousDatabase: Any? = null

    @Before
    fun setUp() {
        val profileId = ProfileManager.getActiveProfileId(context)
        collapseKey = SubscriptionGroupsCollapse.key(profileId)
        previous = preferences.all.filterKeys { it == shortcutKey || it == collapseKey }
        preferences.edit().remove(shortcutKey).putBoolean(collapseKey, false).commit()
        previousDatabase = databaseField.get(null)
        database = TestDatabase.createReplacingNewPipeDatabase()
        database.feedGroupDAO().insert(
            FeedGroupEntity(uid = 0, name = "Keep this group", icon = FeedGroupIcon.EDUCATION, profileId = profileId)
        )
    }

    @After
    fun tearDown() {
        val editor = preferences.edit().remove(shortcutKey).remove(collapseKey)
        previous.forEach { (key, value) -> if (value is Boolean) editor.putBoolean(key, value) }
        try {
            assertTrue(editor.commit())
        } finally {
            databaseField.set(null, previousDatabase)
            database.close()
        }
    }

    @Test
    fun liveTogglePreservesGroupsInListAndGridAndImportRemainsInTheMenu() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.supportFragmentManager.beginTransaction()
                    .add(android.R.id.content, SubscriptionFragment().apply { useAsFrontPage(true) }, FRAGMENT_TAG)
                    .commitNow()
            }
            for (listMode in listOf(true, false)) {
                scenario.onActivity { activity ->
                    ViewModelProvider(fragment(activity))[SubscriptionViewModel::class.java].setListViewMode(listMode)
                }
                val initial = awaitRows(scenario, listMode, showImport = true)
                val remaining = initial.filterNot(::isImport)
                assertEquals(3, remaining.size) // Create, All, and the existing channel group.

                scenario.onActivity { preferences.edit().putBoolean(shortcutKey, false).commit() }
                val hidden = awaitRows(scenario, listMode, showImport = false)
                remaining.zip(hidden).forEach { (before, after) -> assertSame(before, after) }
                scenario.onActivity { activity ->
                    val menu = PopupMenu(activity, View(activity)).menu
                    fragment(activity).onCreateOptionsMenu(menu, activity.menuInflater)
                    val import = (0 until menu.size()).map(menu::getItem)
                        .single { it.title == activity.getString(R.string.import_from) }
                    assertTrue(import.hasSubMenu())
                    assertEquals(activity.getString(R.string.previous_export), import.subMenu!!.getItem(0).title)
                }

                scenario.onActivity { preferences.edit().putBoolean(shortcutKey, true).commit() }
                val restored = awaitRows(scenario, listMode, showImport = true)
                assertTrue(isImport(restored[1]))
                remaining.zip(restored.filterNot(::isImport)).forEach { (before, after) -> assertSame(before, after) }
            }

            scenario.onActivity { preferences.edit().putBoolean(shortcutKey, false).commit() }
            awaitRows(scenario, listMode = false, showImport = false)
            scenario.recreate()
            awaitRows(scenario, listMode = false, showImport = false)
            scenario.onActivity { preferences.edit().remove(shortcutKey).commit() }
            awaitRows(scenario, listMode = false, showImport = true)
        }
    }

    private fun awaitRows(scenario: ActivityScenario<AboutActivity>, listMode: Boolean, showImport: Boolean): List<Item<*>> {
        val deadline = SystemClock.uptimeMillis() + 10_000
        var rows = emptyList<Item<*>>()
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { activity ->
                val carousel = fragment(activity).view?.findViewById<RecyclerView>(R.id.recycler_view)
                val adapter = carousel?.adapter as? GroupAdapter<*>
                rows = adapter?.let { (0 until it.itemCount).map(it::getItem) }.orEmpty()
            }
            val correctMode = if (listMode) rows.firstOrNull() is FeedGroupAddNewItem else rows.firstOrNull() is FeedGroupAddNewGridItem
            if (correctMode && rows.size == (if (showImport) 4 else 3) && rows.any(::isImport) == showImport) return rows
            SystemClock.sleep(25)
        }
        throw AssertionError("Expected list=$listMode, import=$showImport; received ${rows.map { it.javaClass.simpleName }}")
    }

    private fun fragment(activity: AboutActivity) = activity.supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) as SubscriptionFragment

    private fun isImport(item: Item<*>) = item is FeedGroupImportItem || item is FeedGroupImportGridItem

    companion object {
        private const val FRAGMENT_TAG = "subscription-import-shortcut"
    }
}
