/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe

import java.util.concurrent.TimeUnit
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response as HttpResponse
import okhttp3.ResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization

class DownloaderStreamingTest {
    private val url = "https://example.org/plugin-stream"

    @Test
    fun streamingGetDoesNotBufferTheBodyAndClosesItsSource() {
        val body = TrackedBody()
        val downloader: Downloader = DownloaderImpl.init(
            OkHttpClient.Builder().addInterceptor { chain ->
                HttpResponse.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(503).message("fixture").header("X-Fixture", "stream").body(body).build()
            }
        )
        downloader.getStreaming(url, null, null).use { response ->
            assertEquals(503, response.responseCode())
            assertEquals("stream", response.getHeader("x-fixture"))
            assertNull(response.getHeader("Missing"))
            assertEquals(0, body.reads)
            assertFalse(body.closed)
            assertArrayEquals(body.payload, response.body().readBytes())
            assertTrue(body.reads > 0)
        }
        assertTrue(body.closed)
    }

    @Test
    fun streamingPostPreservesCallerHeadersLocalizationAndNullablePayload() {
        for (payload in listOf(null, byteArrayOf(0, 1, -1))) {
            val downloader = DownloaderImpl.init(
                OkHttpClient.Builder().addInterceptor { chain ->
                    val request = chain.request()
                    assertEquals("POST", request.method)
                    assertEquals("Plugin fixture", request.header("User-Agent"))
                    assertEquals("session=profile-local", request.header("Cookie"))
                    assertEquals("fr-FR, fr;q=0.9", request.header("Accept-Language"))
                    assertEquals(listOf("one", "two"), request.headers.values("X-Multi"))
                    val sent = Buffer()
                    request.body!!.writeTo(sent)
                    assertArrayEquals(payload ?: byteArrayOf(), sent.readByteArray())
                    HttpResponse.Builder().request(request).protocol(Protocol.HTTP_1_1)
                        .code(200).message("fixture").body(TrackedBody()).build()
                }
            )
            val headers = mapOf(
                "User-Agent" to listOf("Plugin fixture"),
                "Cookie" to listOf("session=profile-local"),
                "X-Multi" to listOf("one", "two")
            )
            downloader.postStreaming(url, headers, payload, Localization("fr", "FR")).use {
                assertEquals(200, it.responseCode())
            }
        }
    }

    @Test
    fun antiBotErrorsCloseTheUnconsumedBody() {
        val body = TrackedBody()
        val downloader = DownloaderImpl.init(
            OkHttpClient.Builder().addInterceptor { chain ->
                HttpResponse.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(429).message("fixture").body(body).build()
            }
        )
        val error = assertThrows(ReCaptchaException::class.java) {
            downloader.getStreaming(url, null, null)
        }
        assertEquals(url, error.url)
        assertEquals(0, body.reads)
        assertTrue(body.closed)
    }

    @Test
    fun callerDeadlineDoesNotChangeTheDefaultClientTimeouts() {
        val downloader = DownloaderImpl.init(
            OkHttpClient.Builder()
                .connectTimeout(7, TimeUnit.SECONDS)
                .callTimeout(8, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    assertEquals(2500, chain.connectTimeoutMillis())
                    assertEquals(2500, chain.readTimeoutMillis())
                    assertEquals(TimeUnit.MILLISECONDS.toNanos(2500), chain.call().timeout().timeoutNanos())
                    HttpResponse.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                        .code(200).message("fixture").body(TrackedBody()).build()
                }
        )
        downloader.getStreaming(url, null, null, 2500).close()
        assertEquals(7000, downloader.client.connectTimeoutMillis)
        assertEquals(30000, downloader.client.readTimeoutMillis)
        assertEquals(8000, downloader.client.callTimeoutMillis)
    }

    @Test
    fun existingDownloaderSubclassesReceiveBinarySafeStreamingFallbacks() {
        val payload = byteArrayOf(0, -1, 0x42)
        val requests = mutableListOf<Request>()
        val downloader = object : Downloader() {
            override fun execute(request: Request): Response {
                requests += request
                return Response(206, "fixture", null, "not the binary payload", payload, url)
            }

            override fun executeAsync(request: Request, callback: AsyncCallback) = throw UnsupportedOperationException("Synchronous fixture")
        }
        downloader.getStreaming(url, null, null, 500).use {
            assertEquals(206, it.responseCode())
            assertArrayEquals(payload, it.body().readBytes())
        }
        downloader.postStreaming(url, null, payload, null).use {
            assertArrayEquals(payload, it.body().readBytes())
        }
        assertEquals(listOf("GET", "POST"), requests.map { it.httpMethod() })
        assertArrayEquals(payload, requests.last().dataToSend())
    }

    private class TrackedBody : ResponseBody() {
        val payload = byteArrayOf(0, 1, -1, 42)
        var reads = 0
        var closed = false
        private val bytes = Buffer().write(payload)
        private val stream = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                reads++
                return bytes.read(sink, byteCount)
            }

            override fun timeout() = Timeout.NONE

            override fun close() {
                closed = true
            }
        }.buffer()

        override fun contentType(): MediaType? = null
        override fun contentLength() = payload.size.toLong()
        override fun source() = stream
    }
}
