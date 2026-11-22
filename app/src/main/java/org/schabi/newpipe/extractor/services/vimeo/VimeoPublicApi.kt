/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ServiceTemporaryBlockedException

/** Read-only official API access, using only a token explicitly supplied by the user. */
object VimeoPublicApi {
    const val ORIGIN = "https://api.vimeo.com"
    private const val VERIFY_PATH = "/oauth/verify"
    private val READ_PATH = Regex("/(?:videos(?:/[1-9][0-9]*(?:/comments(?:/[1-9][0-9]*/replies)?)?)?|albums/[1-9][0-9]*(?:/videos)?)")
    private val TOKEN = Regex("[A-Za-z0-9._~+/-]{16,512}={0,2}")
    private val QUERY_KEYS = setOf("page", "per_page", "query", "sort", "direction", "filter", "fields", "filter_embeddable", "filter_playable")

    @Volatile private var tokenProvider: () -> String? = { null }

    /** Returns the old provider so tests can restore the application's configuration. */
    @Synchronized
    fun setTokenProvider(provider: () -> String?): () -> String? {
        val previous = tokenProvider
        tokenProvider = provider
        return previous
    }

    fun isConfigured(): Boolean = !tokenProvider().isNullOrBlank()

    fun normalizeToken(value: String): String = value.trim().also {
        require(TOKEN.matches(it)) { "Enter a Vimeo API access token without the Bearer prefix" }
    }

    /** Never accepts private, upload, edit or other broader account permissions. */
    fun verifyPublicToken(value: String, downloader: Downloader = NewPipe.getDownloader()): String {
        val token = normalizeToken(value)
        val response = request("$ORIGIN$VERIFY_PATH", token, downloader, verify = true)
        val scopes = response.getString("scope", "").split(Regex("\\s+")).filter(String::isNotBlank).toSet()
        if (scopes != setOf("public") || !response.getString("token_type", "").equals("bearer", true)) {
            throw PrivateContentException("Use an unauthenticated Vimeo API token with only the public scope")
        }
        return token
    }

    fun getJson(pathOrUrl: String, downloader: Downloader = NewPipe.getDownloader()): JsonObject {
        val url = normalizedUrl(pathOrUrl)
        val token = tokenProvider()?.takeIf(String::isNotBlank)
            ?: throw ContentNotAvailableException("Configure a public Vimeo API token in Settings > Content > Vimeo to use this feature")
        return request(url, normalizeToken(token), downloader)
    }

    fun nextPage(next: String?, expectedPath: String): Page? = next?.takeIf(String::isNotBlank)?.let {
        Page(validatePage(Page(it), expectedPath))
    }

    fun validatePage(page: Page, expectedPath: String): String {
        val url = normalizedUrl(page.url ?: throw ParsingException("Vimeo pagination has no URL"))
        if (URI(url).rawPath != expectedPath) throw ParsingException("Vimeo pagination changed the requested resource")
        return url
    }

    internal fun normalizedUrl(value: String, verify: Boolean = false): String {
        val uri = try {
            URI(if (value.startsWith('/') && !value.startsWith("//")) ORIGIN + value else value)
        } catch (_: Exception) {
            throw ParsingException("Invalid Vimeo API URL")
        }
        if (uri.scheme != "https" || uri.host != "api.vimeo.com" || uri.port !in setOf(-1, 443) ||
            uri.rawUserInfo != null || uri.rawFragment != null ||
            !(READ_PATH.matches(uri.rawPath.orEmpty()) || verify && uri.rawPath == VERIFY_PATH)
        ) {
            throw ParsingException("Vimeo API requests must stay on the requested public endpoint")
        }
        val keys = mutableSetOf<String>()
        uri.rawQuery?.split('&')?.forEach { parameter ->
            if (!parameter.contains('=')) throw ParsingException("Invalid Vimeo API query")
            val (key, decodedValue) = try {
                URLDecoder.decode(parameter.substringBefore('='), StandardCharsets.UTF_8.name()) to
                    URLDecoder.decode(parameter.substringAfter('='), StandardCharsets.UTF_8.name())
            } catch (_: IllegalArgumentException) {
                throw ParsingException("Invalid Vimeo API query")
            }
            if (key !in QUERY_KEYS || !keys.add(key)) throw ParsingException("Unexpected Vimeo API query parameter")
            if (decodedValue.any { it.code < 32 || it.code == 127 }) throw ParsingException("Invalid Vimeo API query value")
            if (key in setOf("page", "per_page")) {
                val number = decodedValue.toIntOrNull()
                if (number == null || number < 1 || key == "per_page" && number > 100) throw ParsingException("Invalid Vimeo API page size or number")
            }
        }
        if (verify && uri.rawQuery != null) throw ParsingException("Unexpected Vimeo token verification query")
        return uri.toASCIIString()
    }

    private fun request(url: String, token: String, downloader: Downloader, verify: Boolean = false): JsonObject {
        val validatedUrl = normalizedUrl(url, verify)
        val response = try {
            downloader.execute(
                Request.newBuilder().get(validatedUrl).followRedirects(false).headers(
                    mapOf(
                        "Authorization" to listOf("Bearer $token"),
                        "Accept" to listOf("application/vnd.vimeo.*+json;version=3.4")
                    )
                ).build()
            )
        } catch (_: Exception) {
            // Exceptions from third-party transports must not expose request headers to reports.
            throw ExtractionException("Could not contact the Vimeo API")
        }
        val finalUrl = response.latestUrl().takeIf(String::isNotBlank) ?: validatedUrl
        if (normalizedUrl(finalUrl, verify) != validatedUrl) throw ParsingException("Vimeo redirected an API request")
        when (response.responseCode()) {
            200 -> Unit
            401 -> throw PrivateContentException("The Vimeo API token expired or was rejected; replace it in Settings > Content > Vimeo")
            403 -> throw PrivateContentException("Vimeo does not permit access to this public API resource")
            404, 410 -> throw ContentNotAvailableException("This Vimeo content is unavailable through the public API")
            429 -> throw ServiceTemporaryBlockedException("The Vimeo API request limit was reached; try again later")
            451 -> throw GeographicRestrictionException("Vimeo has restricted this content in your region")
            else -> throw ExtractionException("Vimeo API returned HTTP ${response.responseCode()}")
        }
        return try {
            JsonParser.`object`().from(response.responseBody())
        } catch (_: Exception) {
            throw ParsingException("Vimeo returned invalid public API data")
        }
    }
}
