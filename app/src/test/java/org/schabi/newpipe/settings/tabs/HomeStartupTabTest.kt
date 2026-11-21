/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings.tabs

import android.app.Application
import androidx.preference.PreferenceManager
import com.grack.nanojson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class HomeStartupTabTest {
    @Test
    fun firstLaunchSnapshotMatchesTheCanonicalHomeSections() {
        val defaultsPath = listOf(
            Path.of("src/main/res/raw/wizestream_default_preferences.json"),
            Path.of("app/src/main/res/raw/wizestream_default_preferences.json")
        ).first { it.exists() }
        val snapshot = Files.newInputStream(defaultsPath).use { input ->
            JsonParser.`object`().from(input)
        }
        val fromFirstLaunch = TabsJsonHelper.getTabsFromJson(
            snapshot.getString("saved_tabs_key")
        )
        val expected = listOf(
            Tab.FeedTab.ID,
            Tab.SubscriptionsTab.ID,
            Tab.DefaultKioskTab.ID,
            Tab.BookmarksTab.ID,
            Tab.HistoryTab.ID
        )
        assertEquals(expected, fromFirstLaunch.map { it.tabId })
        assertEquals(expected, TabsJsonHelper.getDefaultTabs().map { it.tabId })
        assertEquals(expected, TabsJsonHelper.getTabsFromJson(null).map { it.tabId })
        assertEquals(
            expected,
            TabsJsonHelper.getTabsFromJson("""{"tabs":[]}""").map { it.tabId }
        )
    }

    @Test
    fun restoringContentDefaultsResetsTheSectionsAndStartupSelection() {
        val context = RuntimeEnvironment.getApplication()
        val manager = TabsManager.getManager(context)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val savedTabsKey = context.getString(org.schabi.newpipe.R.string.saved_tabs_key)
        try {
            manager.saveTabs(listOf(Tab.BookmarksTab(), Tab.HistoryTab()))
            HomeStartupTab.save(context, Tab.HistoryTab())
            assertEquals(1, HomeStartupTab.startupIndex(context, manager.tabs))

            manager.resetTabs()

            val expected = listOf(
                Tab.FeedTab.ID,
                Tab.SubscriptionsTab.ID,
                Tab.DefaultKioskTab.ID,
                Tab.BookmarksTab.ID,
                Tab.HistoryTab.ID
            )
            assertEquals(expected, manager.tabs.map { it.tabId })
            assertEquals(-1, HomeStartupTab.preferredIndex(context, manager.tabs))
            assertEquals(0, HomeStartupTab.startupIndex(context, manager.tabs))
        } finally {
            prefs.edit().remove(savedTabsKey).commit()
            HomeStartupTab.save(context, null)
        }
    }

    @Test
    fun followsSelectedTabAcrossReorderingAndFallsBackIfRemoved() {
        val context = RuntimeEnvironment.getApplication()
        val tabs = listOf(Tab.BookmarksTab(), Tab.FeedTab(), Tab.SubscriptionsTab())
        HomeStartupTab.save(context, null)
        assertEquals(-1, HomeStartupTab.preferredIndex(context, tabs))
        assertEquals(0, HomeStartupTab.startupIndex(context, tabs))

        HomeStartupTab.save(context, tabs[1])
        assertEquals(1, HomeStartupTab.startupIndex(context, tabs))
        assertEquals(0, HomeStartupTab.startupIndex(context, listOf(tabs[1], tabs[0])))
        assertEquals(0, HomeStartupTab.startupIndex(context, listOf(tabs[0], tabs[2])))

        HomeStartupTab.save(context, null)
        assertEquals(-1, HomeStartupTab.preferredIndex(context, tabs))
    }
}
