/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments.list.channel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory
import org.schabi.newpipe.extractor.search.filter.FilterItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeChannelTabLinkHandlerFactory

class ChannelLiveSortTest {
    private val youtube = ServiceList.YouTube.serviceId
    private val factory = YoutubeChannelTabLinkHandlerFactory.getInstance()

    @Test
    fun exposesYouTubeLiveOrdersAndDefaultsToLatest() {
        val handler = handler(ChannelTabs.LIVESTREAMS)
        val filters = ChannelTabSortHelper.getAvailableSortFilters(youtube, factory, handler)

        assertEquals(listOf("latest", "popular", "oldest"), filters.map { it.name })
        assertEquals(0, ChannelTabSortHelper.getSelectedSortFilterIndex(handler, filters))
    }

    @Test
    fun keepsEachLiveOrderInTheHandlerAndItsCacheUrl() {
        val original = handler(ChannelTabs.LIVESTREAMS)
        val filters = ChannelTabSortHelper.getAvailableSortFilters(youtube, factory, original)

        filters.forEachIndexed { index, filter ->
            val sorted = ChannelTabSortHelper.withSortFilter(factory, original, filter)
            assertEquals(original.originalUrl, sorted.originalUrl)
            assertEquals(original.id, sorted.id)
            assertEquals(ChannelTabs.LIVESTREAMS, sorted.contentFilters.single().name)
            assertEquals(filter.name, sorted.sortFilter.single().name)
            assertEquals("https://www.youtube.com/channel/UC123/streams?sort=${filter.name}", sorted.url)
            assertEquals(index, ChannelTabSortHelper.getSelectedSortFilterIndex(sorted, filters))
        }
    }

    @Test
    fun doesNotInferLiveSortingFromOtherServicesOrGlobalPluginFilters() {
        val live = handler(ChannelTabs.LIVESTREAMS)
        val pluginFactory = mock(ListLinkHandlerFactory::class.java)
        `when`(pluginFactory.availableSortFilter).thenReturn(factory.availableSortFilter)

        assertTrue(ChannelTabSortHelper.getAvailableSortFilters(100_000, factory, live).isEmpty())
        assertTrue(ChannelTabSortHelper.getAvailableSortFilters(youtube, pluginFactory, live).isEmpty())
        assertTrue(ChannelTabSortHelper.getAvailableSortFilters(100_000, pluginFactory, live).isEmpty())
        assertTrue(ChannelTabSortHelper.getAvailableSortFilters(youtube, null, live).isEmpty())

        // Existing video sorting remains available to factories that advertise it.
        assertEquals(
            listOf("latest", "popular", "oldest"),
            ChannelTabSortHelper.getAvailableSortFilters(100_000, pluginFactory, handler(ChannelTabs.VIDEOS))
                .map { it.name }
        )
    }

    @Test
    fun doesNotExposeSortOnUnrelatedYouTubeTabs() {
        for (tab in listOf(ChannelTabs.SHORTS, ChannelTabs.PLAYLISTS, ChannelTabs.POSTS, ChannelTabs.SEARCH)) {
            assertTrue(ChannelTabSortHelper.getAvailableSortFilters(youtube, factory, handler(tab)).isEmpty())
        }
    }

    private fun handler(tab: String): ListLinkHandler = factory.fromQuery("channel/UC123", listOf(FilterItem(-1, tab)), null)
}
