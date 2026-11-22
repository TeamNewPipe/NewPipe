/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VimeoEventTargetTest {
    @Test
    fun eventPagesAndEmbedsKeepAnEventIdentitySeparateFromVideoIds() {
        for (url in listOf(
            "https://vimeo.com/event/4768062",
            "https://www.vimeo.com/event/4768062/",
            "http://vimeo.com/event/4768062/embed?autoplay=1#t=30s"
        )) {
            val target = requireNotNull(VimeoEventTarget.fromUrl(url))
            assertEquals("event/4768062", target.id)
            assertEquals("https://vimeo.com/event/4768062", target.canonicalUrl)
            assertEquals("https://vimeo.com/event/4768062/embed", target.embedUrl)
            assertEquals(target, VimeoEventTarget.fromId(target.id))
            assertNull(VimeoVideoTarget.fromUrl(url))
        }
    }

    @Test
    fun sharedEventHashesAndExplicitRecordingSelectionSurviveRoundTrips() {
        for (url in listOf("https://vimeo.com/event/4768062/abcdef0123", "https://vimeo.com/event/4768062/embed/abcdef0123")) {
            val target = requireNotNull(VimeoEventTarget.fromUrl(url))
            assertEquals("event/4768062/abcdef0123", target.id)
            assertEquals("https://vimeo.com/event/4768062/embed/abcdef0123", target.embedUrl)
            assertEquals(target, VimeoEventTarget.fromUrl(target.canonicalUrl))
        }
        val recording = requireNotNull(VimeoEventTarget.fromUrl("https://vimeo.com/event/5116195/videos/1082194134/"))
        assertEquals("event/5116195/videos/1082194134", recording.id)
        assertEquals("1082194134", recording.videoId)
        assertEquals("https://vimeo.com/event/5116195/embed", recording.embedUrl)
        assertEquals(recording, VimeoEventTarget.fromId(recording.id))
    }

    @Test
    fun unrelatedOriginsAmbiguousHashesAndUnverifiedPathsAreRejected() {
        for (url in listOf(
            "https://vimeo.com.example.org/event/4768062",
            "https://player.vimeo.com/event/4768062",
            "https://someone@vimeo.com/event/4768062",
            "https://vimeo.com:8443/event/4768062",
            "https://vimeo.com/event/0",
            "https://vimeo.com/event/04768062",
            "https://vimeo.com/event/9223372036854775808",
            "https://vimeo.com/event/4768062/short",
            "https://vimeo.com/event/4768062/embed/short",
            "https://vimeo.com/event/4768062?h=abcdef0123",
            "https://vimeo.com/event/4768062/abcdef0123?h=0123456789",
            "https://vimeo.com/event/4768062/videos/0",
            "https://vimeo.com/event/4768062/abcdef0123/videos/123",
            "https://vimeo.com/event/4768062/embed/videos/123",
            "https://vimeo.com/event/4768062%2Fembed",
            "https://vimeo.com/manage/events/4768062",
            "https://vimeo.com/4768062"
        )) {
            assertNull(url, VimeoEventTarget.fromUrl(url))
        }
    }
}
