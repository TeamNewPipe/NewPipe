package org.schabi.newpipe.extractor.services.youtube.extractors

import com.grack.nanojson.JsonObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.net.URI
import java.util.ArrayDeque
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
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.downloader.CancellableCall
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubePostCommentsProtocolTest.Companion.NEW_TOKEN
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubePostCommentsProtocolTest.Companion.TOP_TOKEN
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubePostCommentsProtocolTest.Companion.URL
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubePostCommentsProtocolTest.Companion.fixtureText
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubePostCommentsProtocolTest.Companion.parse
import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousBackend

/** Offline protocol fixtures; every network request must match an explicitly queued expectation. */
class YoutubePostCommentsExtractorTest {
    private val previousDownloader = NewPipe.getDownloader()
    private val previousLocalization = NewPipe.getPreferredLocalization()
    private val previousCountry = NewPipe.getPreferredContentCountry()
    private val previousTokens = ServiceList.YouTube.tokens
    private val previousInvidious = InvidiousBackend.isEnabled()
    private val previousInstance = if (previousInvidious) InvidiousBackend.getInstance() else ""
    private val clientVersion = YoutubeParsingHelper::class.java.getDeclaredField("clientVersion")
        .apply { isAccessible = true }
    private var previousClientVersion: Any? = null
    private val exchanges = ArrayDeque<Exchange>()
    private var requests = 0

    @Before
    fun setUp() {
        previousClientVersion = clientVersion.get(null)
        clientVersion.set(null, "2.20261119.07.00")
        InvidiousBackend.configure(false, "")
        ServiceList.YouTube.tokens = "fixture-account-token"
        NewPipe.init(object : Downloader() {
            override fun execute(request: Request): Response {
                requests++
                val expected = exchanges.pollFirst()
                    ?: throw AssertionError("Unexpected request: ${request.url()}")
                assertEquals("POST", request.httpMethod())
                assertEquals(expected.endpoint, URI(request.url()).path.substringAfter("/youtubei/v1/"))
                assertFalse(request.headers().keys.any { it.equals("Authorization", ignoreCase = true) })
                assertFalse(request.headers().values.flatten().any { it.contains("fixture-account-token") })
                val body = parse(requireNotNull(request.dataToSend()).toString(Charsets.UTF_8))
                expected.check(body)
                return Response(
                    200,
                    "OK",
                    mapOf("Content-Type" to listOf("application/json")),
                    expected.response,
                    expected.response.toByteArray(),
                    request.url()
                )
            }

            override fun executeAsync(request: Request, callback: AsyncCallback): CancellableCall {
                throw AssertionError("Unexpected asynchronous request: ${request.url()}")
            }
        })
    }

    @After
    fun tearDown() {
        clientVersion.set(null, previousClientVersion)
        ServiceList.YouTube.tokens = previousTokens
        InvidiousBackend.configure(previousInvidious, previousInstance)
        NewPipe.init(previousDownloader, previousLocalization, previousCountry)
    }

    @Test
    fun capturedPublicPostSupportsTopNewestNextAndRepliesWithFreshExtractors() {
        val requestTokens = parse(fixtureText("captured-initial-requests.json"))
        initialResponse(
            fixtureText("captured-top.json"),
            requestTokens.getString("top"),
            CAPTURED_URL,
            "captured-resolve.json"
        )
        browse("captured-next.json", "captured-top-next")
        browse("captured-replies.json", "captured-replies-first")
        initialResponse(
            fixtureText("captured-new.json"),
            requestTokens.getString("newest"),
            CAPTURED_URL,
            "captured-resolve.json"
        )

        val top = CommentsInfo.getInfo(ServiceList.YouTube, CAPTURED_URL, CommentSortOrder.TOP)
        assertEquals(2, top.relatedItems.size)
        assertTrue(top.relatedItems.all { it.url == CAPTURED_URL && it.commentTextContent == "Captured top comment" })
        assertEquals("https://yt3.ggpht.com/captured-avatar", top.relatedItems.first().uploaderAvatarUrl)
        val next = CommentsInfo.getMoreItems(ServiceList.YouTube, top, roundTrip(top.nextPage))
        assertEquals(2, next.items.size)
        assertTrue(next.items.all { it.url == CAPTURED_URL && it.commentTextContent == "Captured next comment" })

        val parent = roundTrip(top.relatedItems.first { it.replies != null })
        val replies = CommentsInfo.getMoreItems(ServiceList.YouTube, parent.url, parent.replies)
        assertEquals(2, replies.items.size)
        assertTrue(replies.items.all { it.url == CAPTURED_URL && it.commentTextContent == "Captured replies comment" })
        assertNull(replies.nextPage)

        val newest = CommentsInfo.getInfo(ServiceList.YouTube, CAPTURED_URL, CommentSortOrder.NEWEST)
        assertEquals(CommentSortOrder.NEWEST, newest.sortOrder)
        assertEquals(2, newest.relatedItems.size)
        assertTrue(newest.relatedItems.all { it.url == CAPTURED_URL && it.commentTextContent == "Captured new comment" })
        assertEquals(6, requests)
        assertTrue(exchanges.isEmpty())
    }

    @Test
    fun initialNextAndRestoredRepliesKeepCanonicalPostAndUseOnlyBrowse() {
        initial("representative-modern.json", TOP_TOKEN)
        browse("representative-legacy.json", "comments-next")
        browse("representative-replies.json", "replies-first")
        exchanges += Exchange("browse", EMPTY) { assertEquals("replies-next", it.getString("continuation")) }

        val info = CommentsInfo.getInfo(ServiceList.YouTube, URL, CommentSortOrder.TOP)
        assertEquals(URL, info.url)
        val comment = info.relatedItems.single()
        assertEquals(URL, comment.url)
        assertEquals("modern-comment", comment.commentId)
        assertEquals("A public post comment", comment.commentTextContent)
        assertEquals("https://yt3.ggpht.com/fixture-avatar", comment.uploaderAvatarUrl)
        assertEquals(1, comment.pictures.size)
        assertTrue(comment.isHeartedByUploader)
        assertEquals(URL, info.nextPage.url)

        // This public helper recreates the extractor from the source URL, as the UI does.
        val next = CommentsInfo.getMoreItems(ServiceList.YouTube, info, roundTrip(info.nextPage))
        assertEquals("legacy-comment", next.items.single().commentId)
        assertEquals(URL, next.items.single().url)
        assertEquals(URL, next.items.single().replies!!.url)
        assertEquals("legacy-replies", next.items.single().replies!!.id)
        assertNull(next.nextPage)

        // The parent comment, including its source URL and Page, survives saved-state serialization.
        val restored = roundTrip(comment)
        val replies = CommentsInfo.getMoreItems(ServiceList.YouTube, restored.url, restored.replies)
        assertEquals("reply-comment", replies.items.single().commentId)
        assertEquals(URL, replies.items.single().url)
        assertEquals(URL, replies.nextPage.url)
        val end = CommentsInfo.getMoreItems(ServiceList.YouTube, URL, roundTrip(replies.nextPage))
        assertTrue(end.items.isEmpty())
        assertNull(end.nextPage)
        assertEquals(5, requests)
        assertTrue(exchanges.isEmpty())
    }

    @Test
    fun newestUsesServerSortContinuationAndKnownEmptyPageIsNotDisabled() {
        initialResponse(EMPTY, NEW_TOKEN)
        val info = CommentsInfo.getInfo(ServiceList.YouTube, URL, CommentSortOrder.NEWEST)
        assertEquals(CommentSortOrder.NEWEST, info.sortOrder)
        assertTrue(info.relatedItems.isEmpty())
        assertFalse(info.isCommentsDisabled)
        assertNull(info.nextPage)
        assertTrue(exchanges.isEmpty())
    }

    @Test
    fun missingPostFailsDuringResolutionWithoutAnotherRequest() {
        exchanges += Exchange(
            "navigation/resolve_url",
            """
            {"error":{"code":404,"message":"The community post is unavailable"}}
            """.trimIndent()
        )
        assertThrows(ContentNotAvailableException::class.java) {
            ServiceList.YouTube.getCommentsExtractor(URL).fetchPage()
        }
        assertEquals(1, requests)
    }

    @Test
    fun malformedOrDisabledInitialResponseFailsDuringFetch() {
        initialResponse("""{"responseContext":{"visitorData":"fixture"},"unknownPage":{}}""", TOP_TOKEN)
        assertThrows(ParsingException::class.java) {
            ServiceList.YouTube.getCommentsExtractor(URL).fetchPage()
        }
        initialResponse(
            """
            {"continuationContents":{"itemSectionContinuation":{"contents":[
              {"messageRenderer":{"text":{"simpleText":"Comments are turned off."}}}
            ]}}}
            """.trimIndent(),
            TOP_TOKEN
        )
        assertThrows(ContentNotAvailableException::class.java) {
            ServiceList.YouTube.getCommentsExtractor(URL).fetchPage()
        }
        assertTrue(exchanges.isEmpty())
    }

    @Test
    fun continuationMustHaveTokenAndBelongToThePost() {
        val extractor = ServiceList.YouTube.getCommentsExtractor(URL)
        assertTrue(extractor is YoutubePostCommentsExtractor)
        assertThrows(IllegalArgumentException::class.java) { extractor.getPage(Page(URL)) }
        assertThrows(IllegalArgumentException::class.java) {
            extractor.getPage(Page("https://www.youtube.com/watch?v=abcdefghijk", "token"))
        }
        assertEquals(0, requests)
    }

    @Test
    fun videoCommentsKeepTheirExistingExtractorAndWatchIdentity() {
        val url = "https://www.youtube.com/watch?v=abcdefghijk"
        val extractor = ServiceList.YouTube.getCommentsExtractor(url)
        assertEquals(YoutubeCommentsExtractor::class.java, extractor.javaClass)
        assertEquals(url, extractor.url)
        assertEquals("abcdefghijk", extractor.id)
        assertEquals(0, requests)
    }

    private fun initial(file: String, token: String) = initialResponse(fixtureText(file), token)

    private fun initialResponse(
        response: String,
        token: String,
        url: String = URL,
        resolveFixture: String = "representative-resolve.json"
    ) {
        exchanges += Exchange("navigation/resolve_url", fixtureText(resolveFixture)) {
            assertEquals(url, it.getString("url"))
            assertFalse(it.has("videoId"))
        }
        exchanges += Exchange("browse", response) {
            assertEquals(token, it.getString("continuation"))
            assertFalse(it.has("videoId"))
        }
    }

    private fun browse(file: String, token: String) {
        exchanges += Exchange("browse", fixtureText(file)) { assertEquals(token, it.getString("continuation")) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Serializable> roundTrip(value: T): T {
        val output = ByteArrayOutputStream()
        ObjectOutputStream(output).use { it.writeObject(value) }
        return ObjectInputStream(ByteArrayInputStream(output.toByteArray())).use { it.readObject() as T }
    }

    private data class Exchange(
        val endpoint: String,
        val response: String,
        val check: (JsonObject) -> Unit = {}
    )

    companion object {
        private const val CAPTURED_URL = "https://www.youtube.com/post/UgkxISQDxlrLJ5sy6q4H5TsejrpoueuwZTvp"
        private const val EMPTY = "{\"onResponseReceivedEndpoints\":[{\"appendContinuationItemsAction\":{\"continuationItems\":[]}}]}"
    }
}
