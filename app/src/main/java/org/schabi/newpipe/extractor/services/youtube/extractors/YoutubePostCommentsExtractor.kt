package org.schabi.newpipe.extractor.services.youtube.extractors

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonWriter
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.comments.CommentsInfoItemsCollector
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubePostCommentsTarget

/** Public community-post discussion. All continuation pages, including replies, use browse. */
class YoutubePostCommentsExtractor(service: StreamingService, handler: ListLinkHandler) :
    YoutubeCommentsExtractor(service, handler) {
    private val target = YoutubePostCommentsTarget.fromId(handler.id)
        ?: throw IllegalArgumentException("A YouTube post comments target is required")
    private var initialPage: InfoItemsPage<CommentsInfoItem>? = null

    override fun onFetchPage(downloader: Downloader) {
        val resolved = request("navigation/resolve_url", "url", target.canonicalUrl)
        YoutubePostCommentsResponse.checkError(resolved)
        val channelId = YoutubePostCommentsContinuation.channelId(resolved, target.postId)
        initialPage = extractPostComments(
            request(
                "browse",
                "continuation",
                YoutubePostCommentsContinuation.initial(target.postId, channelId, sortOrder)
            )
        )
    }

    override fun getInitialPage(): InfoItemsPage<CommentsInfoItem> {
        assertPageFetched()
        return initialPage ?: throw ParsingException("YouTube post comments were not loaded")
    }

    override fun getPage(page: Page?): InfoItemsPage<CommentsInfoItem> {
        require(page != null && !page.id.isNullOrBlank()) { "Page has no post comments continuation" }
        require(page.url == target.canonicalUrl) { "Page belongs to another comments target" }
        // CommentsInfo and CommentRepliesFragment recreate the extractor. The opaque token and
        // canonical source URL are sufficient; no original instance or resolved channel is needed.
        return extractPostComments(request("browse", "continuation", page.id))
    }

    private fun request(endpoint: String, key: String, value: String): JsonObject {
        val body = JsonWriter.string(
            YoutubeParsingHelper.prepareDesktopJsonBuilder(
                extractorLocalization,
                extractorContentCountry
            ).value(key, value).done()
        ).toByteArray(Charsets.UTF_8)
        return YoutubeParsingHelper.getJsonPostResponse(endpoint, body, extractorLocalization)
    }

    private fun extractPostComments(response: JsonObject): InfoItemsPage<CommentsInfoItem> {
        val body = YoutubePostCommentsResponse.read(response)
        val collector = CommentsInfoItemsCollector(serviceId)
        body.items.forEach { item ->
            collectCommentItem(body.mutations, item, collector, target.canonicalUrl, timeAgoParser)
        }
        return InfoItemsPage(
            collector,
            body.continuation?.let { Page(target.canonicalUrl, it) }
        )
    }
}
