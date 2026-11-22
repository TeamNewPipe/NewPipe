/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class VimeoChannelTabExtractor(service: StreamingService, handler: ListLinkHandler) : ChannelTabExtractor(service, handler) {
    private val channel = VimeoChannelExtractor(service, handler)

    init {
        if (handler.contentFilters.size != 1 || handler.contentFilters.single().name != ChannelTabs.VIDEOS ||
            !handler.sortFilter.isNullOrEmpty()
        ) {
            throw ContentNotSupportedException("Vimeo only provides the public Videos tab")
        }
    }

    override fun onFetchPage(downloader: Downloader) = channel.fetchPage()

    override fun getInitialPage(): InfoItemsPage<InfoItem> = convert(channel.initialPage)

    override fun getPage(page: Page?): InfoItemsPage<InfoItem> = convert(channel.getPage(page))

    private fun convert(page: InfoItemsPage<StreamInfoItem>): InfoItemsPage<InfoItem> = InfoItemsPage(page.items.map { it as InfoItem }, page.nextPage, page.errors)
}
