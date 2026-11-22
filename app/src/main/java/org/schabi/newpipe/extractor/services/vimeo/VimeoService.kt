/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelExtractor
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor
import org.schabi.newpipe.extractor.comments.CommentsExtractor
import org.schabi.newpipe.extractor.kiosk.KioskList
import org.schabi.newpipe.extractor.linkhandler.LinkHandler
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.search.filter.FilterItem
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.subscription.SubscriptionExtractor
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor

class VimeoService(id: Int) : StreamingService(id, "Vimeo", listOf(ServiceInfo.MediaCapability.VIDEO, ServiceInfo.MediaCapability.AUDIO)) {
    override fun getBaseUrl(): String = "https://vimeo.com"

    override fun getStreamLHFactory(): LinkHandlerFactory = VimeoStreamLinkHandlerFactory.INSTANCE

    override fun getChannelLHFactory(): ListLinkHandlerFactory = VimeoChannelLinkHandlerFactory.INSTANCE

    override fun getChannelTabLHFactory(): ListLinkHandlerFactory = VimeoChannelTabLinkHandlerFactory.INSTANCE

    override fun getPlaylistLHFactory(): ListLinkHandlerFactory? = null

    override fun getSearchQHFactory(): SearchQueryHandlerFactory? = null

    override fun getCommentsLHFactory(): ListLinkHandlerFactory? = null

    override fun getSearchExtractor(queryHandler: SearchQueryHandler): SearchExtractor = unsupportedSearch()

    override fun getSearchExtractor(query: String): SearchExtractor = unsupportedSearch()

    override fun getSearchExtractor(query: String, contentFilter: List<FilterItem>?, sortFilter: List<FilterItem>?): SearchExtractor = unsupportedSearch()

    override fun getSuggestionExtractor(): SuggestionExtractor? = null

    override fun getSubscriptionExtractor(): SubscriptionExtractor? = null

    override fun getKioskList(): KioskList = KioskList(this).apply {
        val handlerFactory = VimeoKioskLinkHandlerFactory.INSTANCE
        addKioskEntry(
            { service, url, kioskId -> VimeoStaffPicksKioskExtractor(service, handlerFactory.fromUrl(url), kioskId) },
            handlerFactory,
            VimeoKioskLinkHandlerFactory.STAFF_PICKS
        )
        setDefaultKiosk(VimeoKioskLinkHandlerFactory.STAFF_PICKS)
    }

    override fun getChannelExtractor(linkHandler: ListLinkHandler): ChannelExtractor = VimeoChannelExtractor(this, linkHandler)

    override fun getChannelTabExtractor(linkHandler: ListLinkHandler): ChannelTabExtractor = VimeoChannelTabExtractor(this, linkHandler)

    override fun getPlaylistExtractor(linkHandler: ListLinkHandler): PlaylistExtractor? = null

    override fun getStreamExtractor(linkHandler: LinkHandler): StreamExtractor = VimeoStreamExtractor(this, linkHandler)

    override fun getCommentsExtractor(linkHandler: ListLinkHandler): CommentsExtractor? = null

    private fun unsupportedSearch(): Nothing = throw UnsupportedOperationException("Vimeo keyword search is unavailable; open a video or channel link instead")
}
