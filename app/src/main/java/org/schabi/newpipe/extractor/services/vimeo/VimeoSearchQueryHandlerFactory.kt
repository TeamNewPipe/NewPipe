/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory
import org.schabi.newpipe.extractor.search.filter.FilterItem

/** Global public-video keyword search through the optional official API integration. */
class VimeoSearchQueryHandlerFactory private constructor() : SearchQueryHandlerFactory() {
    override fun getUrl(query: String, selectedContentFilter: List<FilterItem>, selectedSortFilter: List<FilterItem>?): String {
        if (query.isBlank()) throw ParsingException("Enter a Vimeo search query")
        if (selectedContentFilter.isNotEmpty() || !selectedSortFilter.isNullOrEmpty()) {
            throw ContentNotSupportedException("Vimeo keyword search currently supports public videos in relevance order")
        }
        return "https://vimeo.com/search?q=${encode(query)}"
    }

    override fun getSearchString(url: String): String {
        val uri = VimeoVideoTarget.publicUri(url) ?: return ""
        if (uri.host.equals("player.vimeo.com", true) || uri.rawPath != "/search") return ""
        return runCatching {
            val values = uri.rawQuery.orEmpty().split('&').mapNotNull { parameter ->
                val pair = parameter.split('=', limit = 2)
                if (pair.size == 2 && decode(pair[0]) == "q") decode(pair[1]) else null
            }
            values.singleOrNull().orEmpty()
        }.getOrDefault("")
    }

    companion object {
        @JvmField val INSTANCE = VimeoSearchQueryHandlerFactory()

        internal fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

        internal fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }
}
