package org.schabi.newpipe.extractor.services.youtube.invidious

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.comments.CommentSortOrder
import org.schabi.newpipe.extractor.comments.CommentsExtractor
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubePostCommentsTarget

/** Post comments stay on the selected instance, including author discovery and replies. */
class InvidiousPostCommentsExtractor(service: StreamingService, handler: ListLinkHandler) : CommentsExtractor(service, handler) {
    private val api = InvidiousApi()
    private val target = requireNotNull(YoutubePostCommentsTarget.fromId(handler.id)) {
        "Invalid YouTube post comments target"
    }
    private val postPath = "post/${InvidiousApi.encode(target.postId)}"
    private val commentsUrl = api.url("$postPath/comments")
    private var order = CommentSortOrder.TOP
    private lateinit var first: JsonObject
    private lateinit var firstContext: RequestContext

    override fun setSortOrder(order: CommentSortOrder) {
        this.order = order
    }

    override fun getSortOrder(): CommentSortOrder = order

    override fun onFetchPage(downloader: Downloader) {
        // Invidious requires the post author's UCID for the first comments request.
        // Its post endpoint resolves that context without a direct YouTube request.
        val authorId = api.`object`(api.url(postPath)).getString("authorId", "")
        if (!CHANNEL_ID.matches(authorId)) {
            throw ExtractionException("Invidious could not resolve this post's channel")
        }
        firstContext = RequestContext(authorId, order)
        first = commentsResponse(requestUrl(firstContext))
    }

    override fun getInitialPage(): InfoItemsPage<CommentsInfoItem> {
        assertPageFetched()
        return result(first, firstContext)
    }

    override fun getPage(page: Page): InfoItemsPage<CommentsInfoItem> {
        val (context, token) = continuationRequest(page)
        return result(commentsResponse(requestUrl(context, token)), context)
    }

    private fun commentsResponse(url: String): JsonObject {
        val json = api.`object`(url)
        if (json["comments"] !is JsonArray) {
            throw ExtractionException("Invidious returned an invalid post comments response")
        }
        if (json.has("videoId") || json.has("postId") && json["postId"] != target.postId) {
            throw ExtractionException("Invidious returned comments for a different target")
        }
        return json
    }

    private fun result(json: JsonObject, context: RequestContext): InfoItemsPage<CommentsInfoItem> = InvidiousCommentsExtractor.parseComments(
        json,
        serviceId,
        api,
        // RepliesFragment uses an item's URL as the source and archive identity.
        { target.canonicalUrl },
        { continuation(it, context) }
    )

    private fun continuation(json: JsonObject, context: RequestContext): Page? {
        val token = json.getString("continuation", "")
        return if (token.isEmpty()) null else Page(requestUrl(context, token))
    }

    private fun requestUrl(context: RequestContext, token: String? = null): String {
        val sort = if (context.sort == CommentSortOrder.NEWEST) "new" else "top"
        val url = "$commentsUrl?ucid=${InvidiousApi.encode(context.authorId)}&sort_by=$sort"
        return if (token == null) url else "$url&continuation=${InvidiousApi.encode(token)}"
    }

    private fun continuationRequest(page: Page): Pair<RequestContext, String> {
        if (!Page.isValid(page) || page.url.isNullOrEmpty()) throw invalidPage()
        val uri = try {
            URI.create(page.url)
        } catch (error: IllegalArgumentException) {
            throw ExtractionException("Invalid Invidious post comments page", error)
        }
        if (!InvidiousBackend.sameOrigin(URI.create(api.base), uri) ||
            uri.rawPath != URI.create(commentsUrl).rawPath || uri.rawFragment != null
        ) {
            throw invalidPage()
        }
        val parameters = mutableMapOf<String, String>()
        try {
            for (parameter in (uri.rawQuery ?: "").split('&')) {
                val pair = parameter.split('=', limit = 2)
                if (pair.size != 2) throw invalidPage()
                val key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8.name())
                val value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name())
                if (parameters.put(key, value) != null) throw invalidPage()
            }
        } catch (error: IllegalArgumentException) {
            throw ExtractionException("Invalid Invidious post comments page", error)
        }
        if (parameters.keys != setOf("ucid", "sort_by", "continuation")) throw invalidPage()
        val authorId = parameters.getValue("ucid")
        val token = parameters.getValue("continuation")
        if (!CHANNEL_ID.matches(authorId) || token.isEmpty()) throw invalidPage()
        val sort = when (parameters.getValue("sort_by")) {
            "top" -> CommentSortOrder.TOP
            "new" -> CommentSortOrder.NEWEST
            else -> throw invalidPage()
        }
        // The page carries its channel and order, so a fresh extractor needs no bootstrap.
        return RequestContext(authorId, sort) to token
    }

    private fun invalidPage() = ExtractionException("Invalid Invidious post comments page")

    private data class RequestContext(val authorId: String, val sort: CommentSortOrder)

    companion object {
        private val CHANNEL_ID = Regex("UC[A-Za-z0-9_-]{22}")
    }
}
