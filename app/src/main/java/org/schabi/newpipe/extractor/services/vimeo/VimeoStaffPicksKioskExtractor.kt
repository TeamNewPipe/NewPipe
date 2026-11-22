/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.kiosk.KioskExtractor
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class VimeoStaffPicksKioskExtractor(service: StreamingService, handler: ListLinkHandler, kioskId: String) :
    KioskExtractor<StreamInfoItem>(service, handler, kioskId) {
    private val target = requireNotNull(VimeoChannelTarget.fromId("channel:staffpicks"))
    private lateinit var listing: VimeoVideoList

    init {
        require(
            kioskId == VimeoKioskLinkHandlerFactory.STAFF_PICKS &&
                VimeoChannelTarget.fromUrl(handler.url) == target
        ) { "Invalid Vimeo kiosk identity" }
    }

    override fun onFetchPage(downloader: Downloader) {
        listing = VimeoVideoList.load(serviceId, target, 1, downloader)
    }

    override fun getName(): String = VimeoKioskLinkHandlerFactory.STAFF_PICKS

    override fun getInitialPage(): InfoItemsPage<StreamInfoItem> {
        assertPageFetched()
        return listing.page
    }

    override fun getPage(page: Page?): InfoItemsPage<StreamInfoItem> = VimeoVideoList.load(serviceId, target, target.pageNumber(page), downloader).page
}
