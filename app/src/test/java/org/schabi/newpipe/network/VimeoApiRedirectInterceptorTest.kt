/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.network

import java.net.ProtocolException
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class VimeoApiRedirectInterceptorTest {
    @Test
    fun authenticatedApiRedirectsCloseTheResponseBeforeAnyFollowUpAndDoNotExposeSecrets() {
        for (code in listOf(300, 301, 302, 303, 304, 305, 307, 308, 399)) {
            for (destination in listOf("/oauth/verify", "https://api.vimeo.com/albums/123", "https://external.example/collect")) {
                val request = request("https://api.vimeo.com/videos?query=film", authenticated = true)
                val body = mock(ResponseBody::class.java)
                val response = response(request, code, body).newBuilder()
                    .header("Location", destination)
                    .header("X-Fixture-Secret", "test-public-token")
                    .build()
                val chain = chain(request, response)

                val error = assertThrows(ProtocolException::class.java) { VimeoApiRedirectInterceptor().intercept(chain) }

                assertEquals("Vimeo API redirects are not permitted", error.message)
                assertNull(error.cause)
                verify(chain, times(1)).proceed(request)
                verify(body).close()
                verify(body, never()).string()
                verify(body, never()).bytes()
            }
        }
    }

    @Test
    fun normalAndApiErrorResponsesRemainAvailableToTheApiParser() {
        for (code in listOf(200, 401, 403, 404, 429, 500)) {
            val request = request("https://api.vimeo.com/videos/76979871/comments", authenticated = true)
            val body = mock(ResponseBody::class.java)
            val response = response(request, code, body)
            val chain = chain(request, response)

            assertSame(response, VimeoApiRedirectInterceptor().intercept(chain))
            verify(body, never()).close()
            response.close()
        }
    }

    @Test
    fun publicPagesAndUnrelatedRequestsKeepTheirExistingRedirectBehavior() {
        for ((url, authenticated) in listOf(
            "https://api.vimeo.com/videos" to false,
            "https://vimeo.com/76979871" to false,
            "https://player.vimeo.com/video/76979871" to false,
            "https://api.vimeo.com.other.example/videos" to true,
            "https://other.example/videos" to true,
            "http://api.vimeo.com/videos" to true
        )) {
            val request = request(url, authenticated)
            val body = mock(ResponseBody::class.java)
            val response = response(request, 302, body).newBuilder().header("Location", "/next").build()

            assertSame(response, VimeoApiRedirectInterceptor().intercept(chain(request, response)))
            verify(body, never()).close()
            response.close()
        }
    }

    private fun request(url: String, authenticated: Boolean): Request = Request.Builder().url(url).apply {
        if (authenticated) header("Authorization", "Bearer test-public-token")
    }.build()

    private fun response(request: Request, code: Int, body: ResponseBody): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("synthetic fixture")
        .body(body)
        .build()

    private fun chain(request: Request, response: Response): Interceptor.Chain = mock(Interceptor.Chain::class.java).apply {
        `when`(this.request()).thenReturn(request)
        `when`(proceed(request)).thenReturn(response)
    }
}
