/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import com.grack.nanojson.JsonObject
import java.net.URI
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.comments.CommentsExtractor
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.comments.CommentsInfoItemExtractor
import org.schabi.newpipe.extractor.comments.CommentsInfoItemsCollector
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.localization.DateWrapper

/** Reads viewer comments and replies. No interaction/write API is used. */
class VimeoCommentsExtractor(service: StreamingService, handler: ListLinkHandler) : CommentsExtractor(service, handler) {
    private val target = VimeoVideoTarget.fromId(handler.id) ?: throw ParsingException("Invalid Vimeo video identity")
    private val commentsPath = "/videos/${target.videoId}/comments"
    private val repliesPath = Regex("${Regex.escape(commentsPath)}/[1-9][0-9]*/replies")
    private lateinit var initial: InfoItemsPage<CommentsInfoItem>

    override fun onFetchPage(downloader: Downloader) {
        initial = load("$commentsPath?per_page=25&direction=desc", commentsPath, downloader)
    }

    override fun getInitialPage(): InfoItemsPage<CommentsInfoItem> {
        assertPageFetched()
        return initial
    }

    override fun getPage(page: Page): InfoItemsPage<CommentsInfoItem> {
        if (page.id != target.id) throw ParsingException("Vimeo comments continuation belongs to another video")
        val path = runCatching { URI(page.url).rawPath }.getOrNull()
            ?: throw ParsingException("Invalid Vimeo comments continuation")
        if (path != commentsPath && !repliesPath.matches(path)) throw ParsingException("Invalid Vimeo comments continuation")
        val url = VimeoPublicApi.validatePage(page, path)
        validateQuery(url, path)
        return load(url, path, downloader)
    }

    private fun load(url: String, path: String, downloader: Downloader): InfoItemsPage<CommentsInfoItem> {
        val response = VimeoPublicApi.getJson(url, downloader)
        val items = VimeoApiCollection.items(response)
        val next = VimeoApiCollection.next(response, path, url)?.let {
            validateQuery(it.url, path)
            Page(it.url, target.id)
        }
        val collector = CommentsInfoItemsCollector(serviceId)
        items.forEach { collector.commit(Comment(it)) }
        if (items.isNotEmpty() && collector.items.isEmpty()) throw ParsingException("Vimeo API comments could not be read")
        return InfoItemsPage(collector, next)
    }

    private fun validateQuery(url: String, path: String) {
        val parameters = URI(url).rawQuery.orEmpty().split('&').filter(String::isNotEmpty).associate { parameter ->
            val pair = parameter.split('=', limit = 2)
            if (pair.size != 2) throw ParsingException("Invalid Vimeo comments continuation")
            VimeoSearchQueryHandlerFactory.decode(pair[0]) to VimeoSearchQueryHandlerFactory.decode(pair[1])
        }
        val allowed = if (path == commentsPath) setOf("page", "per_page", "direction") else setOf("page", "per_page")
        if (parameters.keys.any { it !in allowed } || parameters["direction"]?.let { it != "desc" } == true) {
            throw ParsingException("Invalid Vimeo comments continuation")
        }
    }

    private inner class Comment(private val comment: JsonObject) : CommentsInfoItemExtractor {
        private val identity = (comment["uri"] as? String)?.let {
            Regex("${Regex.escape(commentsPath)}/([1-9][0-9]*)").matchEntire(it)?.groupValues?.get(1)
        } ?: throw ParsingException("Vimeo API comment belongs to another video")
        private val text = comment["text"] as? String ?: throw ParsingException("Vimeo API comment has no text")
        private val author = comment["user"] as? JsonObject
        private val replies = ((comment["metadata"] as? JsonObject)?.get("connections") as? JsonObject)?.get("replies") as? JsonObject

        override fun getName(): String = getUploaderName()

        // Replies navigation and saved comments use the source URL, not a comment permalink.
        override fun getUrl(): String = target.canonicalUrl

        override fun getCommentId(): String = identity

        override fun getCommentText(): String = text

        override fun getUploaderName(): String = (author?.get("name") as? String).orEmpty()

        override fun getUploaderUrl(): String = VimeoChannelTarget.fromUrl(author?.get("link") as? String)?.canonicalUrl.orEmpty()

        override fun getUploaderAvatars(): List<Image> = VimeoApiImages.from(author?.get("pictures") as? JsonObject)

        override fun getTextualUploadDate(): String = (comment["created_on"] as? String).orEmpty()

        override fun getUploadDate(): DateWrapper? = VimeoApiDates.parse(getTextualUploadDate())

        override fun getReplyCount(): Int = (replies?.get("total") as? Number)?.toInt()?.coerceAtLeast(0) ?: CommentsInfoItem.UNKNOWN_REPLY_COUNT

        override fun getReplies(): Page? {
            if (getReplyCount() == 0) return null
            val uri = replies?.get("uri") as? String ?: return null
            val path = "$commentsPath/$identity/replies"
            val url = VimeoPublicApi.validatePage(Page(uri), path)
            if (URI(url).rawQuery != null) throw ParsingException("Vimeo API returned invalid replies identity")
            return Page("$url?per_page=25", target.id)
        }
    }
}
