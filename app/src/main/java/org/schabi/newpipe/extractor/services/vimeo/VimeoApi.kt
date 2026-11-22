/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ServiceTemporaryBlockedException

/** Ordinary public pages and player metadata only: no sign-in, visitor token or referrer spoofing. */
object VimeoApi {
    const val BASE_URL = "https://vimeo.com"

    fun getResponse(url: String, downloader: Downloader = NewPipe.getDownloader()): Response {
        if (VimeoVideoTarget.publicUri(url) == null) throw ParsingException("Invalid Vimeo request origin")
        val response = downloader.get(url)
        val finalUrl = response.latestUrl().takeIf { it.isNotBlank() } ?: url
        if (VimeoVideoTarget.publicUri(finalUrl) == null) throw ParsingException("Vimeo redirected outside its public origin")
        when (response.responseCode()) {
            in 200..299 -> return response
            401, 403 -> throw PrivateContentException("Vimeo restricts anonymous access to this content")
            402 -> throw PaidContentException("This Vimeo video requires a purchase")
            404, 410 -> throw ContentNotAvailableException("This Vimeo content is unavailable")
            429 -> throw ServiceTemporaryBlockedException("Vimeo temporarily limited requests; try again later")
            451 -> throw GeographicRestrictionException("This Vimeo content is unavailable in this region")
            else -> throw ExtractionException("Vimeo returned HTTP ${response.responseCode()}")
        }
    }

    fun getJson(url: String, downloader: Downloader = NewPipe.getDownloader()): JsonObject {
        val response = getResponse(url, downloader)
        return try {
            JsonParser.`object`().from(response.responseBody())
        } catch (error: Exception) {
            throw ParsingException("Vimeo returned invalid JSON metadata", error)
        }
    }

    fun getDocument(url: String, downloader: Downloader = NewPipe.getDownloader()): Document {
        val response = getResponse(url, downloader)
        return Jsoup.parse(response.responseBody(), response.latestUrl().takeIf { it.isNotBlank() } ?: url)
    }
}
