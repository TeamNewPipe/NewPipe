/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import java.net.URI
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector
import org.schabi.newpipe.extractor.stream.StreamType

/** Public server-rendered browse pages; no API tokens, deprecated APIs, or guessed next pages. */
internal data class VimeoVideoList(
    val name: String,
    val description: String,
    val avatars: List<Image>,
    val page: InfoItemsPage<StreamInfoItem>
) {
    companion object {
        fun load(serviceId: Int, target: VimeoChannelTarget, number: Int, downloader: Downloader): VimeoVideoList {
            val document = VimeoApi.getDocument(target.pageUrl(number), downloader)
            val list = document.selectFirst("#browse_content ol.browse_videos") ?: unavailable(document)
            val heading = document.selectFirst("#page_header h1 a[href]")
                ?: throw ParsingException("Vimeo listing has no creator or channel identity")
            val owner = VimeoChannelTarget.fromUrl(heading.absUrl("href"))
                ?: throw ParsingException("Vimeo listing has an invalid creator or channel identity")
            validateOwner(document, target, owner)
            val name = heading.text().trim().takeIf(String::isNotEmpty)
                ?: throw ParsingException("Vimeo listing has no name")
            val avatar = if (target.curated) {
                document.selectFirst("img[src*=/channels_uploaded_thumb/]")
            } else {
                document.selectFirst("img.portrait")
            }
            val avatars = image(avatar?.absUrl("src").orEmpty())
            val collector = StreamInfoItemsCollector(serviceId)
            val cards = list.select("li[id^=clip_]")
            if (cards.isEmpty() && list.children().isNotEmpty()) {
                throw ParsingException("Vimeo returned an unrecognized video listing")
            }
            cards.forEach { collector.commit(Card(it, owner, name, avatars)) }
            if (cards.isNotEmpty() && collector.items.isEmpty()) {
                throw ParsingException("Vimeo video cards could not be read")
            }

            val nextLink = document.selectFirst("link[rel=next][href]")
                ?: document.selectFirst("a[rel=next][href]")
            val next = nextLink?.let {
                val nextNumber = owner.pageNumber(Page(it.absUrl("href")))
                if (nextNumber != number + 1) throw ParsingException("Vimeo returned an invalid next page")
                // Retain numeric creator aliases so continuation needs no earlier extractor state.
                Page(target.pageUrl(nextNumber))
            }
            val description = if (target.curated) {
                document.selectFirst("meta[name=description]")?.attr("content").orEmpty()
            } else {
                // Creator browse pages use Vimeo's generic marketing description.
                ""
            }
            return VimeoVideoList(name, description, avatars, InfoItemsPage(collector, next))
        }

        private fun validateOwner(document: Document, target: VimeoChannelTarget, owner: VimeoChannelTarget) {
            if (owner == target) return
            // Vimeo serves /user123 pages with the creator's custom URL in the heading and
            // canonical link, without an HTTP redirect. Only this provider-declared alias is allowed.
            val canonical = document.selectFirst("link[rel=canonical][href]")?.absUrl("href")
            val uri = VimeoVideoTarget.publicUri(canonical)
            val alias = !target.curated && Regex("user[1-9][0-9]*").matches(target.slug) && !owner.curated &&
                uri != null && !uri.host.equals("player.vimeo.com", true) &&
                uri.rawQuery == null && uri.rawFragment == null &&
                uri.rawPath in setOf("/${owner.slug}/videos/sort:date", "/${owner.slug}/videos/sort:date/")
            if (!alias) throw ParsingException("Vimeo returned a different creator or channel")
        }

        private fun unavailable(document: Document): Nothing {
            val body = document.selectFirst("#main, main") ?: document.body()
            val text = body.text().lowercase()
            if (text.contains("not available in your region") || text.contains("not available in your country")) {
                throw GeographicRestrictionException("Vimeo discovery is unavailable in this region")
            }
            if (body.selectFirst("input[type=password]") != null ||
                URI.create(document.location()).path in setOf("/log_in", "/login") ||
                text.contains("this profile is private") || text.contains("this channel is private")
            ) {
                throw PrivateContentException("This Vimeo listing is not available anonymously")
            }
            throw ParsingException("Vimeo did not provide a public video listing")
        }

        private fun image(value: String): List<Image> {
            val uri = runCatching { URI(value) }.getOrNull() ?: return emptyList()
            if (uri.scheme != "https" || uri.rawUserInfo != null || uri.port !in setOf(-1, 443) ||
                uri.host?.lowercase()?.let { it == "vimeocdn.com" || it.endsWith(".vimeocdn.com") } != true
            ) {
                return emptyList()
            }
            return listOf(Image(value, Image.HEIGHT_UNKNOWN, Image.WIDTH_UNKNOWN, Image.ResolutionLevel.UNKNOWN))
        }
    }

    private class Card(
        private val element: Element,
        private val source: VimeoChannelTarget,
        private val sourceName: String,
        private val sourceAvatars: List<Image>
    ) : StreamInfoItemExtractor {
        private fun target(): VimeoVideoTarget {
            val link = element.selectFirst("a[href]")?.absUrl("href")
            val target = VimeoVideoTarget.fromUrl(link)
                ?: throw ParsingException("Vimeo card has no public video URL")
            if (element.id() != "clip_${target.videoId}") throw ParsingException("Vimeo card identities do not match")
            return target
        }

        override fun getName(): String = element.selectFirst("a[title]")?.attr("title")?.takeIf(String::isNotBlank)
            ?: element.selectFirst(".iris_video-vital__title .l-ellipsis")?.text()?.takeIf(String::isNotBlank)
            ?: throw ParsingException("Vimeo card has no title")

        override fun getUrl(): String = target().canonicalUrl

        override fun getThumbnails(): List<Image> = image(element.selectFirst("img.thumbnail")?.absUrl("src").orEmpty())

        override fun getStreamType(): StreamType = StreamType.VIDEO_STREAM

        override fun getDuration(): Long = -1L

        override fun getViewCount(): Long = -1L

        // A curated collection is not the author of the videos it contains.
        override fun getUploaderName(): String = if (source.curated) "" else sourceName

        override fun getUploaderUrl(): String = if (source.curated) "" else source.canonicalUrl

        override fun getUploaderAvatarUrl(): String? = if (source.curated) null else sourceAvatars.firstOrNull()?.url

        override fun getTextualUploadDate(): String? = element.selectFirst("time")?.text()?.takeIf(String::isNotBlank)

        override fun getUploadDate(): DateWrapper? {
            val date = element.selectFirst("time[datetime]")?.attr("datetime") ?: return null
            return try {
                DateWrapper(OffsetDateTime.parse(date))
            } catch (error: DateTimeParseException) {
                throw ParsingException("Invalid Vimeo upload date", error)
            }
        }
    }
}
