/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs
import org.schabi.newpipe.extractor.search.filter.FilterItem

class VimeoChannelTargetTest {
    @Test
    fun creatorsAndCuratedChannelsHaveDistinctCanonicalIdentities() {
        val factory = VimeoChannelLinkHandlerFactory.INSTANCE
        for (url in listOf("https://vimeo.com/nkistudio", "https://www.vimeo.com/nkistudio/videos/?share=1")) {
            val handler = factory.fromUrl(url)
            assertEquals("user:nkistudio", handler.id)
            assertEquals("https://vimeo.com/nkistudio", handler.url)
            assertEquals(handler.url, factory.fromId(handler.id).url)
        }
        val curated = factory.fromUrl("https://vimeo.com/channels/staffpicks/videos")
        assertEquals("channel:staffpicks", curated.id)
        assertEquals("https://vimeo.com/channels/staffpicks", curated.url)
        assertEquals("user:user1939265", factory.getId("https://vimeo.com/user1939265"))
    }

    @Test
    fun unrelatedRoutesVideosAndForeignOriginsAreNeverChannels() {
        for (url in listOf(
            "https://vimeo.com/123456789",
            "https://vimeo.com/123456789/abcdef1234",
            "https://vimeo.com/channels/staffpicks/123456789",
            "https://vimeo.com/search?q=nature",
            "https://vimeo.com/showcase/2632481",
            "https://vimeo.com/groups/animation",
            "https://vimeo.com/nkistudio/extra",
            "https://vimeo.com/nkistudio%2Fextra",
            "https://vimeo.com/../nkistudio",
            "https://vimeo.com:444/nkistudio",
            "https://user@vimeo.com/nkistudio",
            "https://vimeo.com.example.org/nkistudio",
            "https://player.vimeo.com/nkistudio"
        )) {
            assertNull(url, VimeoChannelTarget.fromUrl(url))
            assertFalse(url, VimeoChannelLinkHandlerFactory.INSTANCE.onAcceptUrl(url))
        }
        assertNull(VimeoChannelTarget.fromId("user:search"))
        assertNull(VimeoChannelTarget.fromId("user:123456789"))
        assertNull(VimeoChannelTarget.fromId("channel:../staffpicks"))
    }

    @Test
    fun onlyVideosAndTheNamedStaffPicksKioskAreAdvertised() {
        val tabs = VimeoChannelTabLinkHandlerFactory.INSTANCE
        val handler = tabs.fromQuery("user:nkistudio", listOf(VimeoChannelTabLinkHandlerFactory.VIDEOS), emptyList())
        assertEquals(ChannelTabs.VIDEOS, handler.contentFilters.single().name)
        assertThrows(ContentNotSupportedException::class.java) {
            tabs.fromQuery("user:nkistudio", listOf(FilterItem(1, ChannelTabs.PLAYLISTS)), emptyList())
        }
        assertThrows(ContentNotSupportedException::class.java) {
            tabs.fromQuery("user:nkistudio", listOf(VimeoChannelTabLinkHandlerFactory.VIDEOS), listOf(FilterItem(2, "popular")))
        }
        val kiosk = VimeoKioskLinkHandlerFactory.INSTANCE
        assertEquals("Staff Picks", kiosk.getId("https://vimeo.com/channels/staffpicks"))
        assertEquals("https://vimeo.com/channels/staffpicks", kiosk.fromId("Staff Picks").url)
        assertTrue(kiosk.onAcceptUrl("https://vimeo.com/channels/staffpicks/videos"))
        assertFalse(kiosk.onAcceptUrl("https://vimeo.com/channels/another"))
    }
}
