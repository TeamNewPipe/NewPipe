/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ServiceTemporaryBlockedException

/** Synthetic API-contract responses; no real credential or authenticated network request. */
class VimeoPublicApiTest {
    private lateinit var previousProvider: () -> String?
    private val requests = mutableListOf<Request>()
    private var responseBody = "{}"
    private var responseCode = 200
    private var finalUrl: String? = null
    private val downloader = object : Downloader() {
        override fun execute(request: Request): Response {
            requests += request
            return Response(responseCode, "fixture", emptyMap(), responseBody, responseBody.toByteArray(), finalUrl ?: request.url())
        }

        override fun executeAsync(request: Request, callback: AsyncCallback) = throw AssertionError("Unexpected asynchronous request")
    }

    @Before
    fun configure() {
        previousProvider = VimeoPublicApi.setTokenProvider { TEST_TOKEN }
    }

    @After
    fun restore() {
        VimeoPublicApi.setTokenProvider(previousProvider)
    }

    @Test
    fun configuredRequestsAreReadOnlyAndCarryTheTokenOnlyInTheAuthorizationHeader() {
        VimeoPublicApi.getJson("/videos?query=nature&page=1&per_page=25", downloader)
        val request = requests.single()
        assertEquals("GET", request.httpMethod())
        assertFalse(request.followRedirects())
        assertEquals("https://api.vimeo.com/videos?query=nature&page=1&per_page=25", request.url())
        assertEquals(listOf("Bearer $TEST_TOKEN"), request.headers()["Authorization"])
        assertEquals(listOf("application/vnd.vimeo.*+json;version=3.4"), request.headers()["Accept"])
        assertFalse(request.url().contains(TEST_TOKEN))
        assertFalse(request.headers().keys.any { it.equals("Cookie", true) || it.equals("Referer", true) })
    }

    @Test
    fun missingConfigurationMakesNoRequestAndDoesNotEnableApiFeatures() {
        VimeoPublicApi.setTokenProvider { null }
        assertFalse(VimeoPublicApi.isConfigured())
        val error = assertThrows(ContentNotAvailableException::class.java) { VimeoPublicApi.getJson("/videos?query=nature", downloader) }
        assertTrue(error.message.orEmpty().contains("Settings > Content > Vimeo"))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun verifiesPublicOnlyPermissionBeforeAcceptingTheProvidedToken() {
        responseBody = """{"scope":"public","token_type":"bearer"}"""
        assertEquals(TEST_TOKEN, VimeoPublicApi.verifyPublicToken(TEST_TOKEN, downloader))
        assertEquals("https://api.vimeo.com/oauth/verify", requests.single().url())
        for (response in listOf(
            """{"scope":"public private","token_type":"bearer"}""",
            """{"scope":"private create edit upload public","token_type":"bearer"}""",
            """{"scope":"public","token_type":"basic"}""",
            """{"token_type":"bearer"}"""
        )) {
            responseBody = response
            assertThrows(PrivateContentException::class.java) { VimeoPublicApi.verifyPublicToken(TEST_TOKEN, downloader) }
        }
    }

    @Test
    fun rejectsHeaderInjectionAndTokenPrefixesBeforeAnyRequest() {
        for (value in listOf("Bearer $TEST_TOKEN", "$TEST_TOKEN\r\nCookie: secret", "", "not a token")) {
            assertThrows(IllegalArgumentException::class.java) { VimeoPublicApi.verifyPublicToken(value, downloader) }
        }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun rejectsForeignOriginsCredentialsEncodedPathsAndUnknownEndpointsBeforeSendingHeaders() {
        for (url in listOf(
            "https://api.vimeo.com.evil.example/videos",
            "https://api.vimeo.com@evil.example/videos",
            "http://api.vimeo.com/videos",
            "https://api.vimeo.com:8443/videos",
            "https://player.vimeo.com/video/123",
            "//evil.example/videos",
            "/videos/../oauth/verify",
            "/videos/%2e%2e/oauth/verify",
            "/users/123",
            "/me",
            "/oauth/verify",
            "/videos#fragment",
            "/videos?access_token=credential",
            "/videos?%61ccess_token=credential",
            "/videos?page=1&page=2",
            "/videos?page=0",
            "/videos?page=2&per_page=101",
            "/videos?query=%zz",
            "/videos?page"
        )) {
            assertThrows(url, ParsingException::class.java) { VimeoPublicApi.getJson(url, downloader) }
        }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun continuationsStayOnTheirVideoCollectionOrReplyEndpoint() {
        assertEquals("https://api.vimeo.com/videos?query=nature&page=2", VimeoPublicApi.nextPage("/videos?query=nature&page=2", "/videos")!!.url)
        assertNotNull(VimeoPublicApi.nextPage("/albums/123/videos?page=2", "/albums/123/videos"))
        assertNotNull(VimeoPublicApi.nextPage("/videos/123/comments/456/replies?page=2", "/videos/123/comments/456/replies"))
        assertNull(VimeoPublicApi.nextPage(null, "/videos"))
        assertNull(VimeoPublicApi.nextPage("", "/videos"))
        assertThrows(ParsingException::class.java) { VimeoPublicApi.validatePage(Page("/albums/456/videos?page=2"), "/albums/123/videos") }
        assertThrows(ParsingException::class.java) { VimeoPublicApi.validatePage(Page("/videos/123/comments/789/replies?page=2"), "/videos/123/comments/456/replies") }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun deniedAndRateLimitedResponsesRemainErrorsWithoutPublishingTheResponseBody() {
        responseBody = """{"error":"private response $TEST_TOKEN"}"""
        for ((status, type) in listOf(
            401 to PrivateContentException::class.java,
            403 to PrivateContentException::class.java,
            404 to ContentNotAvailableException::class.java,
            429 to ServiceTemporaryBlockedException::class.java,
            500 to ExtractionException::class.java
        )) {
            responseCode = status
            val error = assertThrows(type) { VimeoPublicApi.getJson("/videos?query=nature", downloader) }
            assertFalse(error.toString().contains(TEST_TOKEN))
            assertNull(error.cause)
        }
    }

    @Test
    fun redirectsAndMalformedDataCannotBecomeSuccessfulEmptyResults() {
        finalUrl = "https://evil.example/videos"
        assertThrows(ParsingException::class.java) { VimeoPublicApi.getJson("/videos", downloader) }
        finalUrl = null
        responseBody = "<html>not API data</html>"
        assertThrows(ParsingException::class.java) { VimeoPublicApi.getJson("/videos", downloader) }
    }

    companion object {
        private const val TEST_TOKEN = "test-public-token"
    }
}
