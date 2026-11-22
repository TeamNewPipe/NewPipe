/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.schabi.newpipe.extractor.exceptions.ParsingException

class VimeoVideoTargetTest {
    private val factory = VimeoStreamLinkHandlerFactory.INSTANCE

    @Test
    fun acceptedPublicPathsHaveOneStableVideoIdentity() {
        for (url in listOf(
            "https://vimeo.com/35572051",
            "http://www.vimeo.com/35572051/",
            "https://vimeo.com:443/35572051?share=copy#t=30s",
            "https://player.vimeo.com/video/35572051",
            "https://vimeo.com/channels/staffpicks/35572051",
            "https://vimeo.com/groups/shortfilms/videos/35572051",
            "https://vimeo.com/album/123/video/35572051",
            "https://vimeo.com/showcase/123/video/35572051"
        )) {
            val handler = factory.fromUrl(url)
            assertEquals(url, "35572051", handler.id)
            assertEquals("https://vimeo.com/35572051", handler.url)
            assertEquals(url, handler.originalUrl)
            assertEquals("https://player.vimeo.com/video/35572051", VimeoVideoTarget.fromId(handler.id)?.playerUrl)
        }
    }

    @Test
    fun aSharedUnlistedHashSurvivesPlayerCanonicalAndStoredIdRoundTrips() {
        for (url in listOf(
            "https://vimeo.com/35572051/8272103f6e",
            "https://vimeo.com/35572051?h=8272103f6e",
            "https://vimeo.com/35572051/8272103f6e?h=8272103f6e",
            "https://player.vimeo.com/video/35572051?h=8272103f6e#t=1m",
            "https://vimeo.com/channels/staffpicks/35572051?h=8272103f6e"
        )) {
            val handler = factory.fromUrl(url)
            assertEquals(url, "35572051/8272103f6e", handler.id)
            assertEquals("https://vimeo.com/35572051/8272103f6e", handler.url)
            assertEquals(handler.url, factory.fromId(handler.id).url)
            assertEquals(handler.id, factory.fromUrl(handler.url).id)
            assertEquals("https://player.vimeo.com/video/35572051?h=8272103f6e", VimeoVideoTarget.fromId(handler.id)?.playerUrl)
        }
    }

    @Test
    fun foreignAmbiguousPrivateManagementAndMalformedTargetsAreNotRoutedAsVideos() {
        for (url in listOf(
            "https://vimeo.com.example.org/35572051",
            "https://player.vimeo.com.example.org/video/35572051",
            "https://vimeo.com@evil.example/35572051",
            "https://someone@vimeo.com/35572051",
            "https://vimeo.com:8443/35572051",
            "ftp://vimeo.com/35572051",
            "https://vimeo.com/0",
            "https://vimeo.com/035572051",
            "https://vimeo.com/9223372036854775808",
            "https://vimeo.com/35572051/short",
            "https://vimeo.com/35572051/8272103f6e/extra",
            "https://vimeo.com/35572051?h=invalid",
            "https://vimeo.com/35572051?h=8272103f6e&%68=8272103f6e",
            "https://vimeo.com/35572051/8272103f6e?h=0123456789",
            "https://vimeo.com/35572051?h=%zz",
            "https://vimeo.com/35572051%2F8272103f6e",
            "https://player.vimeo.com/video/35572051/config",
            "https://player.vimeo.com/35572051",
            "https://vimeo.com/video/35572051",
            "https://vimeo.com/manage/videos/35572051",
            "https://vimeo.com/ondemand/35572051",
            "https://vimeo.com/user123/review/35572051/8272103f6e",
            "https://vimeo.com/showcase/123",
            "https://vimeo.com/channels/staffpicks"
        )) {
            assertNull(url, VimeoVideoTarget.fromUrl(url))
            assertFalse(url, factory.onAcceptUrl(url))
            assertThrows(url, ParsingException::class.java) { factory.getId(url) }
        }
        assertNotNull(VimeoVideoTarget.fromId("9223372036854775807"))
        assertNull(VimeoVideoTarget.fromId("35572051/8272103f6e/extra"))
        assertNull(VimeoVideoTarget.fromUrl("https://vimeo.com/event/35572051"))
    }
}
