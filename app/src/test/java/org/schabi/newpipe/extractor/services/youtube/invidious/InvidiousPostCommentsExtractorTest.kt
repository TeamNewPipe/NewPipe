package org.schabi.newpipe.extractor.services.youtube.invidious

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.comments.CommentSortOrder
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler

class InvidiousPostCommentsExtractorTest {
    private val previousDownloader = NewPipe.getDownloader()
    private val previousLocalization = NewPipe.getPreferredLocalization()
    private val previousCountry = NewPipe.getPreferredContentCountry()
    private val requests = mutableListOf<String>()
    private val responses = mutableMapOf<String, FixtureResponse>()
    private val base = "https://instance.example"
    private val postId = "UgkxPostFixture_123"
    private val postUrl = "https://www.youtube.com/post/$postId"
    private val channelId = "UCAAAAAAAAAAAAAAAAAAAAAA"
    private val bootstrapUrl = "$base/api/v1/post/$postId"
    private val topUrl = "$bootstrapUrl/comments?ucid=$channelId&sort_by=top"
    private val newUrl = "$bootstrapUrl/comments?ucid=$channelId&sort_by=new"

    @Before
    fun setup() {
        InvidiousBackend.configure(true, base)
        responses[bootstrapUrl] = fixture("post.json")
        responses[topUrl] = fixture("post-comments.json")
        responses[newUrl] = fixture("post-comments.json")
        NewPipe.init(object : Downloader() {
            override fun execute(request: Request): Response {
                val url = request.url()
                assertTrue("Unexpected direct request: $url", url.startsWith("$base/api/v1/"))
                requests.add(url)
                val response = responses[url] ?: throw AssertionError("Unexpected fixture request: $url")
                return Response(
                    response.status,
                    "fixture",
                    emptyMap(),
                    response.body,
                    response.body.toByteArray(Charsets.UTF_8),
                    url
                )
            }

            override fun executeAsync(request: Request, callback: AsyncCallback) = throw UnsupportedOperationException()
        })
    }

    @After
    fun reset() {
        InvidiousBackend.configure(false, "")
        NewPipe.init(previousDownloader, previousLocalization, previousCountry)
    }

    @Test
    fun initialPageResolvesAuthorOnInstanceAndPreservesPostIdentityAndMetadata() {
        val extractor = extractor()
        extractor.fetchPage()
        val page = extractor.initialPage
        assertTrue(page.errors.toString(), page.errors.isEmpty())
        val comment = page.items.single()
        assertEquals(postUrl, comment.url)
        assertEquals("UgzComment_1", comment.commentId)
        assertEquals("First comment\nWith a second line", comment.commentText)
        assertEquals("Commenter", comment.uploaderName)
        assertEquals("https://www.youtube.com/channel/UCBBBBBBBBBBBBBBBBBBBBBB", comment.uploaderUrl)
        assertEquals("$base/ggpht/comment-avatar=s88", comment.uploaderAvatarUrl)
        assertEquals(1720000000L, requireNotNull(comment.uploadDate).offsetDateTime().toEpochSecond())
        assertEquals("2 days ago", comment.textualUploadDate)
        assertEquals(4, comment.likeCount)
        assertTrue(comment.isPinned)
        assertTrue(comment.isHeartedByUploader)
        assertEquals(2, comment.replyCount)
        assertEquals("$topUrl&continuation=replies+%2B%2F%3D+%26+token", requireNotNull(comment.replies).url)
        assertEquals("$topUrl&continuation=next+%2B%2F%3D+%26+token", page.nextPage.url)
        assertEquals(listOf(bootstrapUrl, topUrl), requests)
    }

    @Test
    fun freshExtractorsLoadPostPagesAndReplyPagesWithTheirOriginalNewestOrder() {
        val extractor = extractor()
        extractor.setSortOrder(CommentSortOrder.NEWEST)
        extractor.fetchPage()
        val initial = extractor.initialPage
        val nextUrl = "$newUrl&continuation=next+%2B%2F%3D+%26+token"
        val repliesUrl = "$newUrl&continuation=replies+%2B%2F%3D+%26+token"
        val moreRepliesUrl = "$newUrl&continuation=more+replies+%2B%2F%3D+%26+token"
        responses[nextUrl] = fixture("post-comments-next.json")
        responses[repliesUrl] = fixture("post-comment-replies.json")
        responses[moreRepliesUrl] = FixtureResponse("""{"comments":[]}""")

        // These objects have never fetched the post, and still default to TOP locally.
        val next = extractor().getPage(initial.nextPage)
        assertTrue(next.errors.toString(), next.errors.isEmpty())
        assertEquals("UgzComment_2", next.items.single().commentId)
        assertEquals(postUrl, next.items.single().url)
        assertEquals("$base/ggpht/another-avatar", next.items.single().uploaderAvatarUrl)
        assertEquals("$newUrl&continuation=next+page+2", next.nextPage.url)
        val replies = extractor().getPage(requireNotNull(initial.items.single().replies))
        assertTrue(replies.errors.toString(), replies.errors.isEmpty())
        assertEquals("UgzComment_1.Reply_1", replies.items.single().commentId)
        assertEquals(postUrl, replies.items.single().url)
        assertEquals("$base/ggpht/reply-avatar", replies.items.single().uploaderAvatarUrl)
        assertEquals(moreRepliesUrl, replies.nextPage.url)
        val last = extractor().getPage(replies.nextPage)
        assertTrue(last.items.isEmpty())
        assertFalse(last.hasNextPage())
        assertEquals(listOf(bootstrapUrl, newUrl, nextUrl, repliesUrl, moreRepliesUrl), requests)
    }

    @Test
    fun emptyCommentsReturnAnEmptyPageWithoutInventingAnotherProviderRequest() {
        responses[topUrl] = FixtureResponse("""{"comments":[]}""")
        val extractor = extractor()
        extractor.fetchPage()
        val page = extractor.initialPage
        assertTrue(page.items.isEmpty())
        assertTrue(page.errors.isEmpty())
        assertFalse(page.hasNextPage())
        assertNull(page.nextPage)
        assertEquals(listOf(bootstrapUrl, topUrl), requests)
    }

    @Test
    fun malformedAndWrongTargetResponsesFailForInitialAndContinuationPages() {
        val initial = extractor().apply { fetchPage() }.initialPage
        val nextUrl = initial.nextPage.url
        val invalidResponses = listOf(
            "{}",
            """{"comments":null}""",
            """{"comments":{}}""",
            """{"comments":"not an array"}""",
            """{"comments":[],"postId":"UgOtherPost"}""",
            """{"comments":[],"postId":null}""",
            """{"comments":[],"postId":7}""",
            """{"comments":[],"videoId":"BaW_jenozKc"}""",
            """{"comments":[],"postId":"$postId","videoId":""}"""
        )
        for (body in invalidResponses) {
            responses[topUrl] = FixtureResponse(body)
            responses[nextUrl] = FixtureResponse(body)
            requests.clear()
            assertThrows(body, ExtractionException::class.java) { extractor().fetchPage() }
            assertEquals(listOf(bootstrapUrl, topUrl), requests)
            requests.clear()
            assertThrows(body, ExtractionException::class.java) { extractor().getPage(initial.nextPage) }
            assertEquals(listOf(nextUrl), requests)
        }
    }

    @Test
    fun missingOrInvalidAuthorFailsBeforeRequestingComments() {
        for (body in listOf("{}", """{"authorId":""}""", """{"authorId":"not-a-channel"}""")) {
            responses[bootstrapUrl] = FixtureResponse(body)
            requests.clear()
            val error = assertThrows(ExtractionException::class.java) { extractor().fetchPage() }
            assertTrue(error.message.orEmpty().contains("post's channel"))
            assertEquals(listOf(bootstrapUrl), requests)
        }
    }

    @Test
    fun unsupportedEndpointsAndApiErrorsNeverFallBackToYoutube() {
        responses[bootstrapUrl] = FixtureResponse("""{"error":"Post endpoint unavailable"}""", 404)
        assertThrows(ExtractionException::class.java) { extractor().fetchPage() }
        assertEquals(listOf(bootstrapUrl), requests)

        responses[bootstrapUrl] = fixture("post.json")
        for (failure in listOf(
            FixtureResponse("""{"error":"Post comments unavailable"}""", 503),
            FixtureResponse("""{"error":"Post comments unavailable"}"""),
            FixtureResponse("not JSON")
        )) {
            responses[topUrl] = failure
            requests.clear()
            assertThrows(ExtractionException::class.java) { extractor().fetchPage() }
            assertEquals(listOf(bootstrapUrl, topUrl), requests)
        }
    }

    @Test
    fun continuationsRejectForeignOriginsOtherPostsAndMalformedContextBeforeNetwork() {
        val validUrl = "$newUrl&continuation=next"
        val invalidUrls = listOf(
            "",
            validUrl.replace(base, "https://other-instance.example"),
            validUrl.replace(base, "https://www.youtube.com"),
            validUrl.replace("/post/$postId/", "/post/UgOtherPost/"),
            validUrl.replace("/post/$postId/comments", "/comments/BaW_jenozKc"),
            validUrl.replace("ucid=$channelId&", ""),
            validUrl.replace("sort_by=new", "sort_by=unsupported"),
            validUrl.replace("continuation=next", "continuation="),
            validUrl.replace("continuation=next", "continuation=%ZZ"),
            "$validUrl&sort_by=top",
            "$validUrl&format=html",
            "$validUrl#fragment"
        )
        for (url in invalidUrls) {
            assertThrows(url, ExtractionException::class.java) { extractor().getPage(Page(url)) }
        }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun changingOrDisablingTheBackendRejectsExistingAndFreshContinuationExtractors() {
        val original = extractor()
        original.fetchPage()
        val page = original.initialPage.nextPage
        InvidiousBackend.configure(true, "https://changed-instance.example")
        assertThrows(ExtractionException::class.java) { original.getPage(page) }
        assertThrows(ExtractionException::class.java) { extractor().getPage(page) }
        InvidiousBackend.configure(false, base)
        assertThrows(ExtractionException::class.java) { original.getPage(page) }
        assertThrows(ExtractionException::class.java) { extractor().getPage(page) }
        assertEquals(listOf(bootstrapUrl, topUrl), requests)
    }

    @Test
    fun sharedParserPreservesVideoCommentPermalinksAndReplyEndpoints() {
        val videoId = "BaW_jenozKc"
        val videoUrl = "https://www.youtube.com/watch?v=$videoId"
        val commentsUrl = "$base/api/v1/comments/$videoId?source=youtube&sort_by=top"
        responses[commentsUrl] = fixture("post-comments.json")
        val extractor = InvidiousCommentsExtractor(ServiceList.YouTube, handler(videoUrl, videoId))
        extractor.fetchPage()
        val comment = extractor.initialPage.items.single()
        assertEquals("$videoUrl&lc=UgzComment_1", comment.url)
        assertEquals("$commentsUrl&continuation=replies+%2B%2F%3D+%26+token", requireNotNull(comment.replies).url)
        assertEquals(listOf(commentsUrl), requests)
    }

    private fun extractor() = InvidiousPostCommentsExtractor(ServiceList.YouTube, handler(postUrl, "post:$postId"))

    private fun handler(url: String, id: String) = ListLinkHandler(url, url, id, emptyList(), null)

    private fun fixture(name: String): FixtureResponse {
        // Representative API shapes, not live captures; see post-comments-fixtures.md.
        val body = requireNotNull(javaClass.getResourceAsStream("/invidious/$name"))
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        return FixtureResponse(body)
    }

    private data class FixtureResponse(val body: String, val status: Int = 200)
}
