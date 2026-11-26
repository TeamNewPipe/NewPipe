/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.feed

import android.content.Context
import android.widget.ListView
import androidx.appcompat.widget.Toolbar
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.local.history.IncognitoMode
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.recommendations.LocalRecommendations
import org.schabi.newpipe.settings.SettingsActivity
import org.schabi.newpipe.testUtil.TestDatabase

@RunWith(AndroidJUnit4::class)
class FeedSortDialogTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test(timeout = 60_000)
    fun mostViewedIsIndependentOfRecommendationsAndSurvivesCancelAndRecreation() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val modeKey = context.getString(R.string.feed_sort_mode_key)
        val discoveryKey = context.getString(R.string.feed_sort_by_discovery_key)
        val savedPreferences = listOf(modeKey, discoveryKey, IncognitoMode.KEY).associateWith { preferences.all[it] }
        val recommendations = context.getSharedPreferences(LocalRecommendations.PREFERENCES, Context.MODE_PRIVATE)
        val enabledKey = "${ProfileManager.getActiveProfileId(context)}:enabled"
        val previousEnabled = recommendations.all[enabledKey] as? Boolean
        val databaseField = NewPipeDatabase::class.java.getDeclaredField("databaseInstance").apply { isAccessible = true }
        val previousDatabase = databaseField.get(null)
        val database = TestDatabase.createReplacingNewPipeDatabase()
        try {
            for (recommendationsEnabled in listOf(false, true)) {
                preferences.edit().putString(modeKey, FeedSortMode.PUBLICATION.name)
                    .putBoolean(discoveryKey, false).putBoolean(IncognitoMode.KEY, false).commit()
                recommendations.edit().putBoolean(enabledKey, recommendationsEnabled).commit()
                ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
                    scenario.onActivity { activity ->
                        activity.supportFragmentManager.beginTransaction()
                            .replace(R.id.settings_fragment_holder, FeedFragment(), FEED_TAG).commitNow()
                        val toolbar = toolbar(activity)
                        feed(activity).onPrepareOptionsMenu(toolbar.menu)
                        assertEquals(context.getString(R.string.feed_older_cached_page), toolbar.menu.findItem(R.id.menu_item_feed_older_cached).title)
                        assertEquals(context.getString(R.string.feed_newer_cached_page), toolbar.menu.findItem(R.id.menu_item_feed_newer_cached).title)
                        toolbar.menu.performIdentifierAction(R.id.menu_item_feed_sort, 0)
                    }
                    assertChoices(recommendationsEnabled, R.string.feed_sort_publication)
                    onView(withText(R.string.cancel)).inRoot(isDialog()).perform(click())
                    assertEquals(FeedSortMode.PUBLICATION.name, preferences.getString(modeKey, null))

                    openSort(scenario)
                    onView(withText(R.string.feed_sort_views)).inRoot(isDialog()).perform(click())
                    assertEquals(FeedSortMode.VIEWS_DESC.name, preferences.getString(modeKey, null))
                    assertFalse(preferences.getBoolean(discoveryKey, true))
                    scenario.recreate()
                    openSort(scenario)
                    assertChoices(recommendationsEnabled, R.string.feed_sort_views)
                    onView(withText(R.string.cancel)).inRoot(isDialog()).perform(click())
                    assertEquals(FeedSortMode.VIEWS_DESC.name, preferences.getString(modeKey, null))
                    scenario.onActivity { activity ->
                        val toolbar = toolbar(activity)
                        feed(activity).onPrepareOptionsMenu(toolbar.menu)
                        assertEquals(context.getString(R.string.feed_next_cached_page), toolbar.menu.findItem(R.id.menu_item_feed_older_cached).title)
                        assertEquals(context.getString(R.string.feed_previous_cached_page), toolbar.menu.findItem(R.id.menu_item_feed_newer_cached).title)
                    }
                }
            }
        } finally {
            databaseField.set(null, previousDatabase)
            database.close()
            preferences.edit().apply {
                savedPreferences.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Boolean -> putBoolean(key, value)
                        else -> remove(key)
                    }
                }
            }.commit()
            recommendations.edit().apply {
                previousEnabled?.let { putBoolean(enabledKey, it) } ?: remove(enabledKey)
            }.commit()
        }
    }

    private fun feed(activity: SettingsActivity) = activity.supportFragmentManager.findFragmentByTag(FEED_TAG) as FeedFragment

    private fun toolbar(activity: SettingsActivity) = Toolbar(activity).apply {
        inflateMenu(R.menu.menu_feed_fragment)
        setOnMenuItemClickListener(feed(activity)::onOptionsItemSelected)
    }

    private fun openSort(scenario: ActivityScenario<SettingsActivity>) {
        scenario.onActivity { activity -> toolbar(activity).menu.performIdentifierAction(R.id.menu_item_feed_sort, 0) }
    }

    private fun assertChoices(recommendationsEnabled: Boolean, checkedLabel: Int) {
        onView(isAssignableFrom(ListView::class.java)).inRoot(isDialog()).check { view, error ->
            if (error != null) throw error
            val choices = view as ListView
            val labels = (0 until choices.adapter.count).map { choices.adapter.getItem(it).toString() }
            val expected = mutableSetOf(
                context.getString(R.string.feed_sort_publication),
                context.getString(R.string.feed_sort_discovery),
                context.getString(R.string.feed_sort_views)
            )
            if (recommendationsEnabled) expected += context.getString(R.string.feed_sort_relevance)
            assertEquals(expected, labels.toSet())
            assertTrue(choices.isItemChecked(labels.indexOf(context.getString(checkedLabel))))
        }
    }

    private companion object {
        const val FEED_TAG = "feed-sort-test"
    }
}
