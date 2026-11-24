/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.ObjectStreamClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.comments.CommentsFilterConfig
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.utils.ExtractorHelper
import org.schabi.newpipe.extractor.utils.KeywordMatcher
import org.schabi.newpipe.extractor.utils.ServiceScopedId

class PluginFilterContractsTest {
    @Test
    fun serviceScopedIdsFilterAllUploaderItemKindsWithoutCrossingServices() {
        val video = stream(0).apply { uploaderId = "123" }
        val channel = ChannelInfoItem(0, "https://example.org/channel", "Channel").apply { uploaderId = "123" }
        val playlist = PlaylistInfoItem(0, "https://example.org/playlist", "Playlist").apply { uploaderId = "123" }
        val otherService = stream(1).apply { uploaderId = "123" }
        val collector = MultiInfoItemsCollector(0)
        collector.addAll(listOf(video, channel, playlist, otherService))
        val config = InfoItemsCollector.FilterConfig(
            arrayListOf(),
            arrayListOf(),
            arrayListOf("YouTube:123"),
            false,
            false
        )
        collector.applyBlocking(config)
        assertEquals(listOf(otherService), collector.items)
        assertEquals(3, collector.filteredCount)
        collector.applyBlocking(config)
        assertEquals(3, collector.filteredCount)
        val next = Page("https://example.org/next")
        assertEquals(3, InfoItemsPage(collector, next).filteredCount)
        collector.reset()
        assertEquals(0, collector.filteredCount)
        assertEquals("YouTube:123", ServiceScopedId.normalize("  youtube : 123  "))
        assertNull(ServiceScopedId.normalize("unknown-service:123"))
        assertNull(ServiceScopedId.of(-42, "123"))
    }

    @Test
    fun newFilterOptionsDoNotChangeLegacyLiteralAndVideoNameRules() {
        val video = stream(0).apply { name = "Alpha" }
        val channel = ChannelInfoItem(0, "https://example.org/channel", "Channel")
        val collector = MultiInfoItemsCollector(0)
        collector.addAll(listOf(video, channel))
        collector.applyBlocking(
            InfoItemsCollector.FilterConfig(arrayListOf("/alpha/"), arrayListOf("Channel"), false, false)
        )
        assertEquals(listOf(video, channel), collector.items)
        collector.applyBlocking(
            InfoItemsCollector.FilterConfig(arrayListOf("/alpha/"), arrayListOf("Channel"), null, false, false)
        )
        assertTrue(collector.items.isEmpty())
        assertEquals(2, collector.filteredCount)
        val continuation = Page("https://example.org/next")
        assertSame(continuation, InfoItemsPage(collector, continuation).nextPage)
        // Existing WizeStream pages may be empty without having applied a filter.
        assertSame(continuation, InfoItemsPage<InfoItem>(emptyList(), continuation, emptyList()).nextPage)
    }

    @Test
    fun commentRulesHandleHtmlNamesIdsAndUnknownOrPinnedLikeCounts() {
        val hidden = comment().apply { setCommentText(Description("<b>Hidden topic</b>", Description.HTML)) }
        val keywordRules = CommentsFilterConfig(listOf("/hidden\\s+topic/", "/[/"), null, 0)
        assertTrue(keywordRules.shouldFilter(hidden))
        assertFalse(KeywordMatcher(listOf("/[ /")).matches("irrelevant"))
        assertTrue(
            CommentsFilterConfig(null, listOf("  @CREATOR  "), 0).shouldFilter(
                comment().apply { uploaderName = "creator" }
            )
        )
        assertTrue(
            CommentsFilterConfig(null, null, listOf("YouTube:123"), 0).shouldFilter(
                comment().apply { uploaderId = "123" }
            )
        )

        val lowLikes = comment().apply { likeCount = 1 }
        val unknown = comment().apply { likeCount = CommentsInfoItem.NO_LIKE_COUNT }
        val pinned = comment().apply {
            likeCount = 1
            isPinned = true
        }
        val likes = CommentsFilterConfig(null, null, 10)
        assertEquals(listOf(unknown, pinned), likes.filter(listOf(lowLikes, unknown, pinned)))
    }

    @Test
    fun filteredPageCompletionIsBoundedAndKeepsTheContinuationForLater() {
        val extractor = extractor<StreamInfoItem>()
        val firstNext = Page("https://example.org/next/0")
        `when`(extractor.initialPage).thenReturn(InfoItemsPage(emptyList(), firstNext, emptyList(), 2))
        var calls = 0
        `when`(extractor.getPage(any(Page::class.java))).thenAnswer {
            calls++
            InfoItemsPage<StreamInfoItem>(emptyList(), Page("https://example.org/next/$calls"), emptyList(), 2)
        }
        val info = FixtureInfo()
        val result = ExtractorHelper.getItemsPageOrLogError(info, extractor)
        assertEquals(3, calls)
        assertEquals(8, result.filteredCount)
        assertEquals(8, info.filteredCount)
        assertTrue(result.hasNextPage())
        assertEquals("https://example.org/next/3", result.nextPage.url)
    }

    @Test
    fun failedFilteredContinuationKeepsItsErrorAndRetryPage() {
        val extractor = extractor<StreamInfoItem>()
        val next = Page("https://example.org/next")
        `when`(extractor.initialPage).thenReturn(InfoItemsPage(emptyList(), next, emptyList(), 2))
        val error = IOException("fixture failure")
        `when`(extractor.getPage(next)).thenThrow(error)
        val info = FixtureInfo()
        val result = ExtractorHelper.getItemsPageOrLogError(info, extractor)
        assertEquals(2, result.filteredCount)
        assertSame(next, result.nextPage)
        assertEquals(listOf(error), result.errors)
        assertEquals(listOf(error), info.errors)
    }

    @Test
    fun subsequentCommentPagesApplyOptionalServiceRulesAndRetainExistingCounts() {
        val extractor = extractor<CommentsInfoItem>()
        `when`(extractor.service.commentsFilterConfig).thenReturn(CommentsFilterConfig(listOf("hidden"), null, 0))
        val page = Page("https://example.org/comments")
        val hidden = comment().apply { setCommentText("hidden") }
        val kept = comment().apply { setCommentText("visible") }
        `when`(extractor.getPage(page)).thenReturn(InfoItemsPage(listOf(hidden, kept), null, emptyList(), 2))
        val result = ExtractorHelper.getMoreItems(extractor, page)
        assertEquals(listOf(kept), result.items)
        assertEquals(3, result.filteredCount)
    }

    @Test
    fun fullPagesAggregateCountsAndListInfoRetainsItsSerializedIdentity() {
        val extractor = extractor<StreamInfoItem>()
        val next = Page("https://example.org/next")
        val first = stream(0)
        val second = stream(1)
        `when`(extractor.initialPage).thenReturn(InfoItemsPage(listOf(first), next, emptyList(), 2))
        `when`(extractor.getPage(next)).thenReturn(InfoItemsPage(listOf(second), null, emptyList(), 3))
        `when`(extractor.fullPage).thenCallRealMethod()
        val result = extractor.fullPage
        assertEquals(listOf(first, second), result.items)
        assertEquals(5, result.filteredCount)

        assertEquals(5235255403652571931L, ObjectStreamClass.lookup(ListInfo::class.java).serialVersionUID)
        val info = FixtureInfo().apply { filteredCount = result.filteredCount }
        val bytes = ByteArrayOutputStream().also { output ->
            ObjectOutputStream(output).use { it.writeObject(info) }
        }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as FixtureInfo }
        assertEquals(5, restored.filteredCount)
    }

    @Test
    fun subtitleAndCommentPreferencesAreOptionalAndIsolatedPerService() {
        val first = YoutubeService(0)
        val second = YoutubeService(0)
        assertFalse(first.showAutoTranslatedSubtitles)
        assertEquals("en", first.autoTranslatedSubtitlesLanguage)
        assertNull(first.commentsFilterConfig)
        first.showAutoTranslatedSubtitles = true
        first.autoTranslatedSubtitlesLanguage = "fr"
        first.commentsFilterConfig = CommentsFilterConfig(listOf("hidden"), null, 0)
        assertTrue(first.showAutoTranslatedSubtitles)
        assertEquals("fr", first.autoTranslatedSubtitlesLanguage)
        assertFalse(second.showAutoTranslatedSubtitles)
        assertNull(second.commentsFilterConfig)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : InfoItem> extractor(): ListExtractor<T> {
        val extractor = mock(ListExtractor::class.java) as ListExtractor<T>
        `when`(extractor.service).thenReturn(mock(StreamingService::class.java))
        return extractor
    }

    private fun stream(serviceId: Int) = StreamInfoItem(serviceId, "https://example.org/video", "Video", StreamType.VIDEO_STREAM)
    private fun comment() = CommentsInfoItem(0, "https://example.org/comment", "Comment")

    private class FixtureInfo : ListInfo<StreamInfoItem>(0, "fixture", "https://example.org", "https://example.org", "Fixture", emptyList(), emptyList())
}
