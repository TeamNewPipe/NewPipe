/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings.tabs

import android.app.Application
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
