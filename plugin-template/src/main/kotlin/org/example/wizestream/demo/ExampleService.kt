/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Service skeleton adapted from InfinityLoop1308/PipePipePluginTemplate
 * 91950887dfd173263ecacfc466437a33777d242d.
 */
package org.example.wizestream.demo

import java.net.URLEncoder
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelExtractor
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor
import org.schabi.newpipe.extractor.comments.CommentsExtractor
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.kiosk.KioskList
import org.schabi.newpipe.extractor.linkhandler.LinkHandler
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor
import org.schabi.newpipe.extractor.plugin.WizeStreamPluginContext
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.search.filter.FilterItem
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.subscription.SubscriptionExtractor
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor

/**
 * Compilable starting point, not a working website extractor. Keep the host-assigned service ID
 * unchanged and make the service name match plugin.json. Implement only advertised capabilities.
 */
class ExampleService(
    id: Int,
    private val context: WizeStreamPluginContext
) : StreamingService(id, "WizeStream Example", listOf(ServiceInfo.MediaCapability.VIDEO)) {
    override fun getBaseUrl(): String = context.getPreferences().getString("base_url") ?: "https://example.invalid/wizestream-demo"

    /** Demonstrates the native UUID profile context; the integration fixture also checks this. */
    fun getBoundProfileId(): String = context.getProfileId()

    override fun getStreamLHFactory(): LinkHandlerFactory = object : LinkHandlerFactory() {
        // TODO: recognize only your service's URLs and implement stable URL/ID conversion.
        override fun onAcceptUrl(url: String): Boolean = false
        override fun getId(url: String): String = throw ParsingException("Implement stream URL parsing")
        override fun getUrl(id: String): String = throw ParsingException("Implement stream URL generation")
    }

    override fun getSearchQHFactory(): SearchQueryHandlerFactory = object : SearchQueryHandlerFactory() {
        override fun getUrl(query: String, contentFilter: List<FilterItem>?, sortFilter: List<FilterItem>?): String = "${getBaseUrl()}/search?q=${URLEncoder.encode(query, "UTF-8")}"
    }

    // TODO: implement search and stream extractors using NewPipe.getDownloader(). The host supplies
    // normal networking, result types and the player; the plugin supplies website-specific parsing.
    override fun getSearchExtractor(queryHandler: SearchQueryHandler): SearchExtractor = throw UnsupportedOperationException("Implement search extraction for your service")

    override fun getStreamExtractor(linkHandler: LinkHandler): StreamExtractor = throw ExtractionException("Implement stream extraction for your service")

    override fun getChannelLHFactory(): ListLinkHandlerFactory? = null
    override fun getChannelTabLHFactory(): ListLinkHandlerFactory? = null
    override fun getPlaylistLHFactory(): ListLinkHandlerFactory? = null
    override fun getCommentsLHFactory(): ListLinkHandlerFactory? = null
    override fun getSuggestionExtractor(): SuggestionExtractor? = null
    override fun getSubscriptionExtractor(): SubscriptionExtractor? = null
    override fun getKioskList(): KioskList = KioskList(this)
    override fun getChannelExtractor(linkHandler: ListLinkHandler): ChannelExtractor = throw ExtractionException("Channels are not implemented")
    override fun getChannelTabExtractor(linkHandler: ListLinkHandler): ChannelTabExtractor = throw ExtractionException("Channel tabs are not implemented")
    override fun getPlaylistExtractor(linkHandler: ListLinkHandler): PlaylistExtractor = throw ExtractionException("Playlists are not implemented")
    override fun getCommentsExtractor(linkHandler: ListLinkHandler): CommentsExtractor = throw ExtractionException("Comments are not implemented")
}
