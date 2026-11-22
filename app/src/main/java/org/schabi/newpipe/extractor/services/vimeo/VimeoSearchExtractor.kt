/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import java.net.URI
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.MultiInfoItemsCollector
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.search.SearchExtractor

class VimeoSearchExtractor(service: StreamingService, handler: SearchQueryHandler) : SearchExtractor(service, handler) {
    private lateinit var initial: InfoItemsPage<InfoItem>

    init {
        VimeoSearchQueryHandlerFactory.INSTANCE.getUrl(handler.searchString, handler.contentFilters, handler.sortFilter)
    }

    override fun onFetchPage(downloader: Downloader) {
        initial = load("/videos?query=${VimeoSearchQueryHandlerFactory.encode(searchString)}&per_page=25&sort=relevant", downloader)
    }

    override fun getInitialPageInternal(): InfoItemsPage<InfoItem> {
        assertPageFetched()
        return initial
    }

    override fun getPageInternal(page: Page): InfoItemsPage<InfoItem> = load(validate(page), downloader)

    private fun load(url: String, downloader: Downloader): InfoItemsPage<InfoItem> {
        val response = VimeoPublicApi.getJson(url, downloader)
        val items = VimeoApiCollection.items(response)
        val next = VimeoApiCollection.next(response, "/videos", url)?.also { validate(it) }
        val collector = MultiInfoItemsCollector(serviceId)
        items.forEach { collector.commit(VimeoApiStreamInfoItemExtractor(it)) }
        if (items.isNotEmpty() && collector.items.isEmpty()) throw ParsingException("Vimeo API search videos could not be read")
        return InfoItemsPage(collector, next)
    }

    private fun validate(page: Page): String {
        val url = VimeoPublicApi.validatePage(page, "/videos")
        val parameters = URI(url).rawQuery.orEmpty().split('&').associate { parameter ->
            val pair = parameter.split('=', limit = 2)
            if (pair.size != 2) throw ParsingException("Invalid Vimeo search continuation")
            VimeoSearchQueryHandlerFactory.decode(pair[0]) to VimeoSearchQueryHandlerFactory.decode(pair[1])
        }
        if (parameters.keys.any { it !in setOf("query", "page", "per_page", "sort", "direction") } ||
            parameters["query"] != searchString || parameters["sort"]?.let { it != "relevant" } == true ||
            parameters["direction"]?.let { it != "desc" } == true
        ) {
            throw ParsingException("Vimeo search continuation belongs to another query")
        }
        return url
    }
}
