package org.schabi.newpipe.extractor.services.youtube.extractors

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.comments.CommentSortOrder
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ParsingException

class YoutubePostCommentsProtocolTest {
    @Test
    fun resolvesChannelFromProviderParamsAndRejectsAnotherPost() {
        val response = fixture("representative-resolve.json")
        assertEquals(CHANNEL, YoutubePostCommentsContinuation.channelId(response, POST))
        assertThrows(ParsingException::class.java) {
            YoutubePostCommentsContinuation.channelId(response, "UgkxAnotherPost")
        }
    }

    @Test
    fun rejectsMalformedOrNonPostResolutionInsteadOfUsingAVideoTarget() {
        val response = fixture("representative-resolve.json")
        response.getObject("endpoint").getObject("browseEndpoint")["params"] = "not!base64"
        assertThrows(ParsingException::class.java) {
            YoutubePostCommentsContinuation.channelId(response, POST)
        }
        response.getObject("endpoint").getObject("browseEndpoint")["browseId"] = CHANNEL
        assertThrows(ParsingException::class.java) {
            YoutubePostCommentsContinuation.channelId(response, POST)
        }
        assertThrows(ParsingException::class.java) {
            YoutubePostCommentsContinuation.channelId(JsonObject(), POST)
        }
    }

    @Test
    fun topAndNewestMatchIndependentlyEncodedReferenceContinuations() {
        // See fixture README: constants were encoded independently from the upstream wire fields.
        assertEquals(TOP_TOKEN, YoutubePostCommentsContinuation.initial(POST, CHANNEL, CommentSortOrder.TOP))
        assertEquals(NEW_TOKEN, YoutubePostCommentsContinuation.initial(POST, CHANNEL, CommentSortOrder.NEWEST))
    }

    @Test
    fun capturedResolveContextProducesTheSuccessfullyRequestedBrowseTokens() {
        val post = "UgkxISQDxlrLJ5sy6q4H5TsejrpoueuwZTvp"
        val channel = YoutubePostCommentsContinuation.channelId(fixture("captured-resolve.json"), post)
        assertEquals("UCBR8-60-B28hp2BmDPdntcQ", channel)
        val requests = fixture("captured-initial-requests.json")
        assertEquals(requests.getString("top"), YoutubePostCommentsContinuation.initial(post, channel, CommentSortOrder.TOP))
        assertEquals(requests.getString("newest"), YoutubePostCommentsContinuation.initial(post, channel, CommentSortOrder.NEWEST))
    }

    @Test
    fun readsModernBodyAndPreservesMutationsAndContinuationWithoutMutatingInput() {
        val response = fixture("representative-modern.json")
        repeat(2) {
            val body = YoutubePostCommentsResponse.read(response)
            assertEquals(1, body.items.size)
            assertEquals(3, body.mutations.size)
            assertEquals("comments-next", body.continuation)
        }
        assertEquals(
            2,
            response.getArray("onResponseReceivedEndpoints").getObject(1)
                .getObject("reloadContinuationItemsCommand").getArray("continuationItems").size
        )
    }

    @Test
    fun readsLegacyReplyContainerAndButtonContinuation() {
        val body = YoutubePostCommentsResponse.read(fixture("representative-replies.json"))
        assertEquals("reply-comment", body.items.single().getObject("commentRenderer").getString("commentId"))
        assertEquals("replies-next", body.continuation)
    }

    @Test
    fun normalizesLegacyReplyLinkWithoutChangingTheSourceResponse() {
        val response = fixture("representative-legacy.json")
        val body = YoutubePostCommentsResponse.read(response)
        val normalized = body.items.single().getObject("commentThreadRenderer")
            .getObject("replies").getObject("commentRepliesRenderer")
        assertEquals(
            "legacy-replies",
            normalized.getArray("contents").getObject(0)
                .getObject("continuationItemRenderer").getObject("continuationEndpoint")
                .getObject("continuationCommand").getString("token")
        )
        assertFalse(
            response.getArray("onResponseReceivedEndpoints").getObject(0)
                .getObject("appendContinuationItemsAction").getArray("continuationItems").getObject(0)
                .getObject("commentThreadRenderer").getObject("replies")
                .getObject("commentRepliesRenderer").has("contents")
        )
    }

    @Test
    fun recognizedEmptyBodiesEndPagination() {
        for (json in listOf(
            """{"onResponseReceivedEndpoints":[{"reloadContinuationItemsCommand":{"slot":"RELOAD_CONTINUATION_SLOT_BODY"}}]}""",
            """{"continuationContents":{"itemSectionContinuation":{"contents":[]}}}""",
            """{"continuationContents":{"commentRepliesContinuation":{"contents":[]}}}"""
        )) {
            val result = YoutubePostCommentsResponse.read(parse(json))
            assertTrue(result.items.isEmpty())
            assertNull(result.continuation)
        }
    }

    @Test
    fun disabledProviderNoticeIsUnavailableAndNotAnEmptySuccess() {
        val error = assertThrows(ContentNotAvailableException::class.java) {
            YoutubePostCommentsResponse.read(
                parse(
                    """
                {"onResponseReceivedEndpoints":[{"reloadContinuationItemsCommand":{
                  "slot":"RELOAD_CONTINUATION_SLOT_BODY","continuationItems":[
                    {"messageRenderer":{"text":{"simpleText":"Comments are turned off."}}}
                  ]}}]}
            """
                )
            )
        }
        assertEquals("Comments are turned off.", error.message)
        val missing = assertThrows(ContentNotAvailableException::class.java) {
            YoutubePostCommentsResponse.read(parse("""{"error":{"code":404,"message":"Post unavailable"}}"""))
        }
        assertEquals("Post unavailable", missing.message)
    }

    @Test
    fun providerFailureIsNotMisreportedAsUnavailableContent() {
        val error = assertThrows(ExtractionException::class.java) {
            YoutubePostCommentsResponse.read(parse("""{"error":{"code":500,"message":"Service error"}}"""))
        }
        assertFalse(error is ContentNotAvailableException)
    }

    @Test
    fun unknownMalformedAndIncompleteResponsesFailInsteadOfHidingErrors() {
        for (json in listOf(
            "{}",
            """{"contents":{"twoColumnWatchNextResults":{}}}""",
            """{"onResponseReceivedEndpoints":[{"unrecognizedCommand":{}}]}""",
            """{"onResponseReceivedEndpoints":[{"reloadContinuationItemsCommand":{"slot":"UNKNOWN","continuationItems":[]}}]}""",
            """{"onResponseReceivedEndpoints":[{"appendContinuationItemsAction":{"continuationItems":"bad"}}]}""",
            """{"onResponseReceivedEndpoints":[{"appendContinuationItemsAction":{"continuationItems":[{"continuationItemRenderer":{}}]}}]}""",
            """{"continuationContents":{"unknownContinuation":{"contents":[]}}}"""
        )) {
            assertThrows(ParsingException::class.java) { YoutubePostCommentsResponse.read(parse(json)) }
        }
    }

    @Test
    fun rejectsVideoContinuationsInsidePostRepliesOrPagination() {
        val next = fixture("representative-modern.json")
        val items = next.getArray("onResponseReceivedEndpoints").getObject(1)
            .getObject("reloadContinuationItemsCommand").getArray("continuationItems")
        val endpoint = items.getObject(1).getObject("continuationItemRenderer").getObject("continuationEndpoint")
        endpoint["commandMetadata"] = parse("""{"webCommandMetadata":{"apiUrl":"/youtubei/v1/next"}}""")
        assertThrows(ParsingException::class.java) { YoutubePostCommentsResponse.read(next) }

        val replies = fixture("representative-modern.json")
        val command = replies.getArray("onResponseReceivedEndpoints").getObject(1)
            .getObject("reloadContinuationItemsCommand").getArray("continuationItems").getObject(0)
            .getObject("commentThreadRenderer").getObject("replies").getObject("commentRepliesRenderer")
            .getArray("contents").getObject(0).getObject("continuationItemRenderer")
            .getObject("continuationEndpoint").getObject("continuationCommand")
        command["request"] = "CONTINUATION_REQUEST_TYPE_WATCH_NEXT"
        assertThrows(ParsingException::class.java) { YoutubePostCommentsResponse.read(replies) }
    }

    companion object {
        const val POST = "UgkxPostCommentsFixture"
        const val CHANNEL = "UCAAAAAAAAAAAAAAAAAAAAAA"
        const val URL = "https://www.youtube.com/post/$POST"
        const val TOP_TOKEN = "4qmFsgKoARIoRkVjb21tZW50X3Bvc3RfZGV0YWlsX3BhZ2Vfd2ViX3RvcF9sZXZlbBp8RWdWd2IzTjBjNm9EVWlJOE1BQjRBc2dCQU9vQkYxVm5hM2hRYjNOMFEyOXRiV1Z1ZEhOR2FYaDBkWEpsOGdFWVZVTkJRVUZCUVVGQlFVRkJRVUZCUVVGQlFVRkJRVUZCT0FCQ0VHTnZiVzFsYm5SekxYTmxZM1JwYjI0PQ%3D%3D"
        const val NEW_TOKEN = "4qmFsgKoARIoRkVjb21tZW50X3Bvc3RfZGV0YWlsX3BhZ2Vfd2ViX3RvcF9sZXZlbBp8RWdWd2IzTjBjNm9EVWlJOE1BRjRBc2dCQU9vQkYxVm5hM2hRYjNOMFEyOXRiV1Z1ZEhOR2FYaDBkWEpsOGdFWVZVTkJRVUZCUVVGQlFVRkJRVUZCUVVGQlFVRkJRVUZCT0FCQ0VHTnZiVzFsYm5SekxYTmxZM1JwYjI0PQ%3D%3D"

        fun fixture(name: String): JsonObject = parse(fixtureText(name))

        fun fixtureText(name: String): String = requireNotNull(
            YoutubePostCommentsProtocolTest::class.java.getResourceAsStream("/youtube/post-comments/$name")
        ).bufferedReader().use { it.readText() }

        fun parse(value: String): JsonObject = JsonParser.`object`().from(value.trimIndent())
    }
}
