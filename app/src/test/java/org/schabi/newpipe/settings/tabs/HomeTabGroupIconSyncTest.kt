/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings.tabs

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.local.subscription.FeedGroupIcon

class HomeTabGroupIconSyncTest {
    private val context = mock(Context::class.java)

    @Test
    fun groupIconChangesFollowSourceUntilUserSelectsHomeOverride() {
        val tab = Tab.FeedGroupTab(42L, "News", FeedGroupIcon.NEWS.drawableResource)
        val tabs = listOf<Tab>(tab, Tab.FeedTab())

        assertEquals(FeedGroupIcon.NEWS.drawableResource, tab.getTabIconRes(context))
        assertTrue(
            HomeTabGroupIconSync.apply(
                tabs,
                listOf(FeedGroupEntity(42L, "News", FeedGroupIcon.MUSIC))
            )
        )
        assertEquals(FeedGroupIcon.MUSIC.drawableResource, tab.getTabIconRes(context))
        assertFalse(
            HomeTabGroupIconSync.apply(
                tabs,
                listOf(FeedGroupEntity(42L, "News", FeedGroupIcon.MUSIC))
            )
        )

        tab.setCustomIconId(FeedGroupIcon.MOVIE.id)
        assertFalse(
            HomeTabGroupIconSync.apply(
                tabs,
                listOf(FeedGroupEntity(42L, "News", FeedGroupIcon.GAMING))
            )
        )
        assertEquals(FeedGroupIcon.MOVIE.drawableResource, tab.getTabIconRes(context))

        // Resetting the manual icon immediately reveals the latest source icon.
        tab.setCustomIconId(Tab.DEFAULT_ICON_ID)
        assertEquals(FeedGroupIcon.GAMING.drawableResource, tab.getTabIconRes(context))
    }

    @Test
    fun ignoresUnrelatedGroupsAndFallsBackWhenSourceGroupDisappears() {
        val tab = Tab.FeedGroupTab(42L, "News", FeedGroupIcon.NEWS.drawableResource)

        assertFalse(
            HomeTabGroupIconSync.apply(
                listOf(tab),
                listOf(FeedGroupEntity(43L, "Other", FeedGroupIcon.FITNESS))
            )
        )
        assertEquals(FeedGroupIcon.NEWS.drawableResource, tab.getTabIconRes(context))

        assertTrue(
            HomeTabGroupIconSync.apply(
                listOf(tab),
                listOf(FeedGroupEntity(42L, "News", FeedGroupIcon.MUSIC))
            )
        )
        assertTrue(HomeTabGroupIconSync.apply(listOf(tab), emptyList()))
        assertEquals(FeedGroupIcon.NEWS.drawableResource, tab.getTabIconRes(context))
    }

    @Test
    fun explicitOverrideSurvivesTabSerialization() {
        val tab = Tab.FeedGroupTab(42L, "News", FeedGroupIcon.NEWS.drawableResource)
        tab.setCustomIconId(FeedGroupIcon.MOVIE.id)
        val serialized = TabsJsonHelper.getJsonToSave(listOf(tab))
        val restored = TabsJsonHelper.getTabsFromJson(serialized).single() as Tab.FeedGroupTab

        assertFalse(
            HomeTabGroupIconSync.apply(
                listOf(restored),
                listOf(FeedGroupEntity(42L, "News", FeedGroupIcon.GAMING))
            )
        )
        assertEquals(FeedGroupIcon.MOVIE.drawableResource, restored.getTabIconRes(context))
    }
}
