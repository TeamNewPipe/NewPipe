/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonWriter
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ParsingException

class VimeoApiCommentsTest : VimeoApiExtractorTestSupport() {
    private val firstUrl = "https://api.vimeo.com/videos/100101/comments?per_page=25&direction=desc"
    private val nextUrl = "https://api.vimeo.com/videos/100101/comments?per_page=25&page=2&direction=desc"
    private val repliesUrl = "https://api.vimeo.com/videos/100101/comments/300101/replies?per_page=25"
    private val nextRepliesUrl = "https://api.vimeo.com/videos/100101/comments/300101/replies?per_page=25&page=2"

    private fun extractor(id: String = "100101"): VimeoCommentsExtractor = VimeoCommentsExtractor(
        ServiceList.Vimeo,
        VimeoCommentsLinkHandlerFactory.INSTANCE.fromId(id)
    )

    @Test
    fun commentsAndReplyPagesRetainVideoIdentityOnFreshExtractors() {
        responses[firstUrl] = fixture("comments-page1.json")
        responses[nextUrl] = fixture("comments-page2.json")
        responses[repliesUrl] = fixture("replies-page1.json")
        responses[nextRepliesUrl] = fixture("replies-page2.json")
        val first = extractor().apply { fetchPage() }.initialPage
        assertTrue(first.errors.toString(), first.errors.isEmpty())
        assertEquals(2, first.items.size)
        val comment = first.items.first()
        assertEquals("300101", comment.commentId)
        assertEquals("https://vimeo.com/100101", comment.url)
        assertEquals("A thoughtful film. The scene at 1:20 is memorable.", comment.commentText)
        assertEquals("Example filmmaker", comment.uploaderName)
        assertEquals("https://vimeo.com/examplefilmmaker", comment.uploaderUrl)
        assertEquals(OffsetDateTime.parse("2026-01-03T13:45:00Z"), requireNotNull(comment.uploadDate).offsetDateTime())
        assertEquals(2, comment.replyCount)
        assertEquals(repliesUrl, requireNotNull(comment.replies).url)
        assertEquals("", first.items[1].uploaderName)
        assertNull(first.items[1].replies)

        val second = extractor().getPage(first.nextPage)
        assertEquals("300103", second.items.single().commentId)
        assertFalse(second.hasNextPage())
        val replies = extractor().getPage(requireNotNull(comment.replies))
        assertEquals("300104", replies.items.single().commentId)
        assertEquals(nextRepliesUrl, replies.nextPage.url)
        val last = extractor().getPage(replies.nextPage)
        assertEquals("300105", last.items.single().commentId)
        assertFalse(last.hasNextPage())
        assertEquals(listOf(firstUrl, nextUrl, repliesUrl, nextRepliesUrl), requests)
    }

    @Test
    fun explicitShareHashStaysInTheSourceAndContinuationIdentity() {
        responses[firstUrl] = fixture("comments-page1.json")
        responses[repliesUrl] = fixture("replies-page1.json")
        val handler = VimeoCommentsLinkHandlerFactory.INSTANCE.fromUrl("https://player.vimeo.com/video/100101?h=abcdef1234")
        assertEquals("100101/abcdef1234", handler.id)
        val first = extractor(handler.id).apply { fetchPage() }.initialPage
        val comment = first.items.first()
        assertEquals("https://vimeo.com/100101/abcdef1234", comment.url)
        assertEquals(handler.id, first.nextPage.id)
        val replies = extractor(handler.id).getPage(requireNotNull(comment.replies))
        assertEquals(comment.url, replies.items.single().url)
        requests.clear()
        assertThrows(ParsingException::class.java) { extractor().getPage(requireNotNull(comment.replies)) }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun paginationAndReplyLinksCannotEscapeTheirVideoParentOrApiOrigin() {
        for (url in listOf(
            "https://example.org/videos/100101/comments?page=2",
            "https://api.vimeo.com/videos/100102/comments?page=2",
            "https://api.vimeo.com/videos/100101/comments/300101/replies?page=2&query=other",
            "https://api.vimeo.com/videos/100101/comments?page=2&direction=asc",
            "https://api.vimeo.com/videos/100101/comments?page=2&access_token=unexpected"
        )) {
            assertThrows(url, ParsingException::class.java) { extractor().getPage(Page(url, "100101")) }
        }
        assertTrue(requests.isEmpty())
        responses[repliesUrl] = FixtureResponse(fixture("replies-page1.json").body.replace("comments/300101/replies?", "comments/300102/replies?"))
        assertThrows(ParsingException::class.java) { extractor().getPage(Page(repliesUrl, "100101")) }
        assertEquals(listOf(repliesUrl), requests)

        requests.clear()
        responses[firstUrl] = FixtureResponse(
            fixture("comments-page1.json").body.replace(
                "\"uri\": \"/videos/100101/comments/300101/replies\"",
                "\"uri\": \"https://example.org/videos/100101/comments/300101/replies\""
            )
        )
        val result = extractor().apply { fetchPage() }.initialPage
        assertNull(result.items.first().replies)
        assertTrue(result.errors.any { it is ParsingException })
        assertEquals(listOf(firstUrl), requests)
    }

    @Test
    fun commentAndReplyPagesCannotRepeatGoBackOrChangeTheirRequestedPageSize() {
        for ((url, name) in listOf(
            firstUrl to "comments-page1.json",
            nextUrl to "comments-page2.json",
            repliesUrl to "replies-page1.json",
            nextRepliesUrl to "replies-page2.json"
        )) {
            val currentPage = if (url == nextUrl || url == nextRepliesUrl) 2 else 1
            val base = url.substringBefore('?')
            val direction = if (url.contains("direction=desc")) "&direction=desc" else ""
            for (next in listOf(
                url,
                "$base?page=1&per_page=25$direction",
                "$base?page=${currentPage + 1}&per_page=10$direction",
                "$base?page=${currentPage + 1}$direction"
            )) {
                responses[url] = withNext(name, next)
                requests.clear()
                assertThrows(next, ParsingException::class.java) {
                    if (url == firstUrl) extractor().fetchPage() else extractor().getPage(Page(url, "100101"))
                }
                assertEquals(listOf(url), requests)
            }
        }
    }

    @Test
    fun emptyDiscussionDiffersFromMalformedOrWrongVideoResponses() {
        responses[firstUrl] = fixture("empty-page.json")
        val empty = extractor().apply { fetchPage() }.initialPage
        assertTrue(empty.items.isEmpty())
        assertFalse(empty.hasNextPage())
        for (body in listOf(
            "{}",
            "{\"data\":{},\"paging\":{\"next\":null}}",
            "{\"data\":[],\"paging\":{\"next\":false}}",
            "{\"data\":[{\"uri\":\"/videos/100101/comments/300101\"}],\"paging\":{\"next\":null}}",
            fixture("comments-page1.json").body.replace("/videos/100101/comments", "/videos/100102/comments")
        )) {
            responses[firstUrl] = FixtureResponse(body)
            assertThrows(ParsingException::class.java) { extractor().fetchPage() }
        }
    }

    @Test
    fun missingOrRemovedTokenStopsCommentRequestsWithoutWebsiteFallback() {
        VimeoPublicApi.setTokenProvider { null }
        assertThrows(ExtractionException::class.java) { extractor().fetchPage() }
        assertTrue(requests.isEmpty())
        VimeoPublicApi.setTokenProvider { "test-public-token" }
        responses[firstUrl] = fixture("comments-page1.json")
        val first = extractor().apply { fetchPage() }.initialPage
        requests.clear()
        VimeoPublicApi.setTokenProvider { null }
        assertThrows(ExtractionException::class.java) { extractor().getPage(first.nextPage) }
        assertThrows(ContentNotAvailableException::class.java) {
            CommentsInfo.getMoreItems(ServiceList.Vimeo, "https://vimeo.com/100101", first.nextPage)
        }
        assertThrows(ContentNotAvailableException::class.java) {
            CommentsInfo.getMoreItems(ServiceList.Vimeo, "https://vimeo.com/100101", requireNotNull(first.items.first().replies))
        }
        assertTrue(requests.isEmpty())
    }

    private fun withNext(name: String, next: String): FixtureResponse {
        val response = JsonParser.`object`().from(fixture(name).body)
        response.getObject("paging")["next"] = next
        return FixtureResponse(JsonWriter.string(response))
    }
}
