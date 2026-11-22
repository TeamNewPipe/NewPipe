/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelExtractor
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class VimeoChannelExtractor(service: StreamingService, handler: ListLinkHandler) : ChannelExtractor(service, handler) {
    private val target = requireNotNull(VimeoChannelTarget.fromId(handler.id)) { "Invalid Vimeo channel identity" }
    private lateinit var listing: VimeoVideoList

    override fun onFetchPage(downloader: Downloader) {
        listing = VimeoVideoList.load(serviceId, target, 1, downloader)
    }

    override fun getName(): String = listing.name

    override fun getDescription(): String = listing.description

    override fun getAvatars(): List<Image> = listing.avatars

    override fun getSubscriberCount(): Long = UNKNOWN_SUBSCRIBER_COUNT

    override fun getInitialPage(): InfoItemsPage<StreamInfoItem> {
        assertPageFetched()
        return listing.page
    }

    override fun getPage(page: Page?): InfoItemsPage<StreamInfoItem> = VimeoVideoList.load(serviceId, target, target.pageNumber(page), downloader).page

    override fun getTabs(): List<ListLinkHandler> = listOf(
        VimeoChannelTabLinkHandlerFactory.INSTANCE.fromQuery(
            target.id,
            listOf(VimeoChannelTabLinkHandlerFactory.VIDEOS),
            emptyList()
        )
    )
}
