/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeChannelTabLinkHandlerFactory

class ChannelSearchResultsTest {
    private val channelId = "UC_x5XG1OV2P6uZZ5FSM9Ttw"

    @Test
    fun channelSearchUsesExistingYouTubeRemoteSearchEndpoint() {
        val handler = ChannelSearchResults.handlerFor(
            ServiceList.YouTube.serviceId,
            channelId,
            "  quantum gravity  "
        )
        assertNotNull(handler)
        assertEquals(
            ChannelTabs.SEARCH,
            requireNotNull(handler).contentFilters.single().name
        )
        assertEquals(
            "quantum gravity",
            YoutubeChannelTabLinkHandlerFactory.getSearchQueryFromUrl(handler.originalUrl)
        )
        assertEquals(
            "https://www.youtube.com/channel/$channelId/search?query=quantum%20gravity",
            handler.url
        )
    }

    @Test
    fun channelSearchRejectsUnsupportedServicesAndInvalidQueries() {
        assertNull(ChannelSearchResults.handlerFor(ServiceList.PeerTube.serviceId, channelId, "physics"))
        assertNull(ChannelSearchResults.handlerFor(ServiceList.YouTube.serviceId, channelId, " "))
        assertNull(ChannelSearchResults.handlerFor(ServiceList.YouTube.serviceId, "not-a-channel", "physics"))
        assertNull(ChannelSearchResults.handlerFor(ServiceList.YouTube.serviceId, channelId, "a".repeat(257)))
    }
}
