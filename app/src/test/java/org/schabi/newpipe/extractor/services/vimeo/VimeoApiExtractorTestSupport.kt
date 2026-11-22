/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response

abstract class VimeoApiExtractorTestSupport {
    private val previousDownloader = NewPipe.getDownloader()
    private val previousLocalization = NewPipe.getPreferredLocalization()
    private val previousCountry = NewPipe.getPreferredContentCountry()
    private lateinit var previousTokenProvider: () -> String?
    protected val requests = mutableListOf<String>()
    protected val responses = mutableMapOf<String, FixtureResponse>()

    @Before
    fun installApiFixtures() {
        previousTokenProvider = VimeoPublicApi.setTokenProvider { "test-public-token" }
        NewPipe.init(object : Downloader() {
            override fun execute(request: Request): Response {
                assertEquals("GET", request.httpMethod())
                assertTrue(request.url().startsWith("https://api.vimeo.com/"))
                val authorization = request.headers().entries.single { it.key.equals("Authorization", true) }.value.single()
                assertEquals("bearer test-public-token", authorization.lowercase())
                assertFalse(request.headers().keys.any { it.equals("Cookie", true) })
                requests += request.url()
                val fixture = responses[request.url()] ?: throw AssertionError("Unexpected Vimeo API request: ${request.url()}")
                return Response(
                    fixture.status,
                    "synthetic fixture",
                    mapOf("Content-Type" to listOf("application/json")),
                    fixture.body,
                    fixture.body.toByteArray(Charsets.UTF_8),
                    request.url()
                )
            }

            override fun executeAsync(request: Request, callback: AsyncCallback) = throw UnsupportedOperationException()
        })
    }

    @After
    fun restoreApiFixtures() {
        VimeoPublicApi.setTokenProvider(previousTokenProvider)
        NewPipe.init(previousDownloader, previousLocalization, previousCountry)
    }

    protected fun fixture(name: String): FixtureResponse = FixtureResponse(
        requireNotNull(javaClass.getResourceAsStream("/vimeo/api/$name"))
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
    )

    protected data class FixtureResponse(val body: String, val status: Int = 200)
}
