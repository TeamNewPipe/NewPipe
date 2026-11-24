/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments.list.channel

import android.app.Application
import android.os.Bundle
import com.evernote.android.state.StateSaver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs
import org.schabi.newpipe.extractor.search.filter.FilterItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeChannelTabLinkHandlerFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ChannelTabSortStateTest {
    @Test
    fun rebuildingTabsKeepsVideoAndLiveChoicesIndependent() {
        val channel = channel()
        channel.setSelectedChannelSort(ChannelTabs.VIDEOS, "popular")
        assertNull(selectedSort(channel, ChannelTabs.LIVESTREAMS))
        channel.setSelectedChannelSort(ChannelTabs.LIVESTREAMS, "oldest")

        repeat(2) {
            assertEquals("popular", selectedSort(channel, ChannelTabs.VIDEOS))
            assertEquals("oldest", selectedSort(channel, ChannelTabs.LIVESTREAMS))
        }

        channel.setSelectedChannelSort(ChannelTabs.LIVESTREAMS, "latest")
        assertEquals("popular", selectedSort(channel, ChannelTabs.VIDEOS))
        assertEquals("latest", selectedSort(channel, ChannelTabs.LIVESTREAMS))
    }

    @Test
    fun stateSaverRestoresEachTabChoiceAfterFragmentRecreation() {
        val original = channel()
        original.setSelectedChannelSort(ChannelTabs.VIDEOS, "oldest")
        original.setSelectedChannelSort(ChannelTabs.LIVESTREAMS, "popular")
        val saved = Bundle()
        StateSaver.saveInstanceState(original, saved)

        val restored = ChannelFragment()
        StateSaver.restoreInstanceState(restored, saved)
        assertEquals("oldest", selectedSort(restored, ChannelTabs.VIDEOS))
        assertEquals("popular", selectedSort(restored, ChannelTabs.LIVESTREAMS))

        restored.setSelectedChannelSort(ChannelTabs.VIDEOS, "latest")
        assertEquals("latest", selectedSort(restored, ChannelTabs.VIDEOS))
        assertEquals("popular", selectedSort(restored, ChannelTabs.LIVESTREAMS))
    }

    @Test
    fun unrelatedTabsCannotChangeOrInheritRememberedSorts() {
        val channel = channel()
        channel.setSelectedChannelSort(ChannelTabs.VIDEOS, "popular")
        channel.setSelectedChannelSort(ChannelTabs.LIVESTREAMS, "oldest")
        channel.setSelectedChannelSort(ChannelTabs.SHORTS, "latest")
        channel.setSelectedChannelSort(null, "latest")

        assertNull(selectedSort(channel, ChannelTabs.SHORTS))
        assertEquals("popular", selectedSort(channel, ChannelTabs.VIDEOS))
        assertEquals("oldest", selectedSort(channel, ChannelTabs.LIVESTREAMS))
    }

    private fun channel(): ChannelFragment = ChannelFragment.getInstance(
        ServiceList.YouTube.serviceId,
        "https://www.youtube.com/channel/UC123",
        "Fixture channel"
    )

    private fun selectedSort(channel: ChannelFragment, tab: String): String? {
        val handler = YoutubeChannelTabLinkHandlerFactory.getInstance()
            .fromQuery("channel/UC123", listOf(FilterItem(-1, tab)), null)
        return channel.createChannelTabFragment(handler).selectedChannelVideoSort
    }
}
