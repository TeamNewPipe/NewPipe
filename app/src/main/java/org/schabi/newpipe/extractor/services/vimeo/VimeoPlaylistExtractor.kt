/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.jsoup.nodes.Document
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector
import org.schabi.newpipe.extractor.stream.StreamType

/** Public embed lists are complete only when their clip count agrees with the advertised total. */
class VimeoPlaylistExtractor(service: StreamingService, handler: ListLinkHandler) : PlaylistExtractor(service, handler) {
    private val originalTarget = requireNotNull(VimeoPlaylistTarget.fromId(handler.id)) { "Invalid Vimeo showcase identity" }
    private var resolvedTarget: VimeoPlaylistTarget? = originalTarget.takeUnless { it.custom }
    private lateinit var initial: InfoItemsPage<StreamInfoItem>
    private var title = ""
    private var ownerName = ""
    private var ownerUrl = ""
    private var ownerAvatar = ""
    private var thumbnail = ""
    private var count = 0L

    override fun onFetchPage(downloader: Downloader) {
        val target = resolve(downloader)
        val document = VimeoApi.getDocument(target.embedUrl, downloader)
        val publicData = VimeoPlayerConfig.assignment(document, "dataForPlayer")
        unavailable(document, publicData == null || publicData["error"] !in listOf(null, false, ""))
        if (VimeoPlaylistTarget.fromUrl(document.location()) != target) {
            throw ParsingException("Vimeo redirected to another showcase")
        }
        val data = publicData
            ?: throw ParsingException("Vimeo did not provide a public showcase playlist")
        if (data["error"] !in listOf(null, false, "")) {
            if (data["error"] in setOf("not_accessible", "not_embeddable", "password", "password_required", "private")) {
                throw PrivateContentException("Vimeo restricts access to this showcase")
            }
            throw ContentNotAvailableException("Vimeo does not make this showcase available anonymously")
        }
        val entity = VimeoPlayerConfig.assignment(document, "entityData")
            ?: throw ParsingException("Vimeo showcase has no public identity")
        if (entity["has_registration_enabled"] !is Boolean) {
            throw ParsingException("Vimeo showcase did not declare its registration status")
        }
        if (entity["has_registration_enabled"] == true) {
            throw PrivateContentException("This Vimeo showcase requires registration")
        }
        val playlist = data["playlist_data"] as? JsonObject
            ?: throw ParsingException("Vimeo showcase has no public playlist metadata")
        if (playlist["sso"] !is Boolean) {
            throw ParsingException("Vimeo showcase did not declare its sign-in status")
        }
        if (playlist["sso"] == true) {
            throw PrivateContentException("This Vimeo showcase requires organization sign-in")
        }
        if (playlist["id"].toString() != target.value || playlist["type"] != "Album" ||
            entity["entity_id"].toString() != target.value || entity["entity_type"] != "album"
        ) {
            throw ParsingException("Vimeo returned a different showcase identity")
        }
        title = (playlist["title"] as? String)?.takeIf(String::isNotBlank)
            ?: throw ParsingException("Vimeo showcase has no title")
        ownerName = (playlist["user"] as? String).orEmpty()
        count = nonnegativeCount(playlist["total"])
        val clips = (data["clips"] as? JsonArray)?.map {
            it as? JsonObject ?: throw ParsingException("Vimeo showcase returned an invalid video")
        } ?: throw ParsingException("Vimeo showcase has no public video list")
        if (clips.size.toLong() > count) throw ParsingException("Vimeo showcase count disagrees with its videos")
        if (clips.size.toLong() < count) {
            if (!VimeoPublicApi.isConfigured()) {
                throw ContentNotSupportedException(
                    "Vimeo exposed only part of this showcase. Configure a public Vimeo API token in Settings > Content > Vimeo to load the complete list"
                )
            }
            loadApiMetadata(target, downloader)
            initial = loadApiPage("/albums/${target.value}/videos?per_page=100", target, count, downloader)
            return
        }
        val collector = StreamInfoItemsCollector(serviceId)
        val seen = mutableSetOf<String>()
        clips.forEach { clip ->
            val item = Clip(clip)
            if (!seen.add(item.getUrl())) throw ParsingException("Vimeo showcase repeats a video")
            collector.commit(item)
        }
        if (collector.items.size != clips.size) throw ParsingException("Vimeo showcase videos could not all be read")
        thumbnail = collector.items.firstOrNull()?.thumbnailUrl.orEmpty()
        initial = InfoItemsPage(collector, null)
    }

    override fun getName(): String = title

    override fun getId(): String = (resolvedTarget ?: originalTarget).id

    override fun getUrl(): String = (resolvedTarget ?: originalTarget).canonicalUrl

    override fun getLinkHandler(): ListLinkHandler {
        val original = super.getLinkHandler()
        val target = resolvedTarget ?: return original
        return ListLinkHandler(original.originalUrl, target.canonicalUrl, target.id, original.contentFilters, original.sortFilter)
    }

    override fun getUploaderName(): String = ownerName

    override fun getUploaderUrl(): String = ownerUrl

    override fun getUploaderAvatarUrl(): String = ownerAvatar

    override fun isUploaderVerified(): Boolean = false

    override fun getThumbnailUrl(): String = thumbnail

    override fun getStreamCount(): Long = count

    override fun getInitialPage(): InfoItemsPage<StreamInfoItem> {
        assertPageFetched()
        return initial
    }

    override fun getPage(page: Page?): InfoItemsPage<StreamInfoItem> {
        if (page == null) throw ParsingException("Vimeo showcase continuation has no URL")
        val target = resolve(downloader)
        val url = validateApiPage(page, target)
        val expectedTotal = Regex("showcase:${target.value}:([1-9][0-9]*)")
            .matchEntire(page.id.orEmpty())?.groupValues?.get(1)?.toLongOrNull()
            ?: throw ParsingException("Vimeo showcase continuation has no valid collection count; reload the showcase")
        return loadApiPage(url, target, expectedTotal, downloader)
    }

    private fun resolve(downloader: Downloader): VimeoPlaylistTarget {
        resolvedTarget?.let { return it }
        val document = VimeoApi.getDocument(originalTarget.canonicalUrl, downloader)
        val canonical = document.selectFirst("link[rel=canonical][href]")?.absUrl("href")
        unavailable(document, canonical == null)
        val target = VimeoPlaylistTarget.fromUrl(canonical)?.takeUnless { it.custom }
            ?: throw ParsingException("Vimeo did not declare this showcase's numeric canonical URL")
        val actual = VimeoPlaylistTarget.fromUrl(document.location())
        if (actual != originalTarget && actual != target) throw ParsingException("Vimeo redirected to another showcase")
        resolvedTarget = target
        return target
    }

    private fun loadApiMetadata(target: VimeoPlaylistTarget, downloader: Downloader) {
        val metadata = VimeoPublicApi.getJson("/albums/${target.value}", downloader)
        val uri = metadata["uri"] as? String
        if (uri == null || !Regex("/(?:users/[1-9][0-9]*/)?albums/${target.value}").matches(uri)) {
            throw ParsingException("Vimeo API returned another showcase")
        }
        val privacy = (metadata["privacy"] as? JsonObject)?.get("view")
        if (privacy != null && privacy !in setOf("anybody", "embed_only")) {
            throw PrivateContentException("This Vimeo showcase is not public")
        }
        title = (metadata["name"] as? String)?.takeIf(String::isNotBlank)
            ?: throw ParsingException("Vimeo API showcase has no title")
        val owner = metadata["user"] as? JsonObject
        ownerName = (owner?.get("name") as? String).orEmpty()
        ownerUrl = VimeoChannelTarget.fromUrl(owner?.get("link") as? String)?.canonicalUrl.orEmpty()
        ownerAvatar = VimeoApiImages.from(owner?.get("pictures") as? JsonObject).firstOrNull()?.url.orEmpty()
        thumbnail = VimeoApiImages.from(metadata["pictures"] as? JsonObject).firstOrNull()?.url.orEmpty()
    }

    private fun loadApiPage(url: String, target: VimeoPlaylistTarget, expectedTotal: Long, downloader: Downloader): InfoItemsPage<StreamInfoItem> {
        val response = VimeoPublicApi.getJson(url, downloader)
        val total = nonnegativeCount(response["total"])
        if (total != expectedTotal) {
            throw ParsingException("Vimeo did not return the advertised showcase video count; reload the showcase")
        }
        val items = VimeoApiCollection.items(response)
        val next = VimeoApiCollection.next(response, "/albums/${target.value}/videos", url)
        val number = nonnegativeCount(response["page"])
        val perPage = nonnegativeCount(response["per_page"])
        if (number != pageNumber(url) || perPage != query(url)["per_page"]?.toLongOrNull() ||
            perPage !in 1L..100L || items.size > perPage ||
            number > Long.MAX_VALUE / perPage
        ) {
            throw ParsingException("Vimeo API returned invalid showcase page metadata")
        }
        val end = (number - 1) * perPage + items.size
        if (end > total || next == null && end != total || next != null && (items.size.toLong() != perPage || end >= total)) {
            throw ParsingException("Vimeo API returned inconsistent showcase pagination")
        }
        if (next != null) {
            val nextUrl = validateApiPage(next, target)
            if (pageNumber(nextUrl) != number + 1 || query(nextUrl)["per_page"]?.toLongOrNull() != perPage) {
                throw ParsingException("Vimeo API did not advance consistently to the next showcase page")
            }
        }
        val collector = StreamInfoItemsCollector(serviceId)
        val seen = mutableSetOf<String>()
        items.forEach {
            val item = VimeoApiStreamInfoItemExtractor(it)
            if (!seen.add(item.getUrl())) throw ParsingException("Vimeo API showcase repeats a video")
            collector.commit(item)
        }
        if (collector.items.size != items.size) throw ParsingException("Vimeo API showcase videos could not all be read")
        count = expectedTotal
        if (thumbnail.isBlank()) thumbnail = collector.items.firstOrNull()?.thumbnailUrl.orEmpty()
        return InfoItemsPage(collector, next?.let { Page(it.url, "showcase:${target.value}:$expectedTotal") })
    }

    private fun validateApiPage(page: Page, target: VimeoPlaylistTarget): String {
        val url = VimeoPublicApi.validatePage(page, "/albums/${target.value}/videos")
        val parameters = query(url)
        if (parameters.keys.any { it !in setOf("page", "per_page") } ||
            parameters["page"]?.toLongOrNull()?.let { it < 1 } == true ||
            parameters.containsKey("page") && parameters["page"]?.toLongOrNull() == null ||
            parameters["per_page"]?.toLongOrNull()?.let { it !in 1L..100L } == true ||
            parameters["per_page"]?.toLongOrNull() == null
        ) {
            throw ParsingException("Invalid Vimeo showcase continuation parameters")
        }
        return url
    }

    private class Clip(private val clip: JsonObject) : StreamInfoItemExtractor {
        private val id = clip["id"].toString()
        private val target = VimeoPlayerConfig.configTarget(
            (clip["config"] as? String)?.takeIf(String::isNotBlank)
                ?: (clip["config_no_autoplay"] as? String)
                ?: throw ParsingException("Vimeo showcase video has no public player URL"),
            id
        )

        override fun getName(): String = (clip["title"] as? String)?.takeIf(String::isNotBlank)
            ?: throw ParsingException("Vimeo showcase video has no title")

        override fun getUrl(): String = target.canonicalUrl

        override fun getStreamType(): StreamType = if (clip.getBoolean("is_live", false) || clip.getBoolean("is_pending", false)) {
            StreamType.LIVE_STREAM
        } else {
            StreamType.VIDEO_STREAM
        }

        override fun getThumbnails(): List<Image> = VimeoPlayerConfig.mediaUrl(clip["thumbnail"] as? String)?.let {
            listOf(Image(it, Image.HEIGHT_UNKNOWN, Image.WIDTH_UNKNOWN, Image.ResolutionLevel.UNKNOWN))
        }.orEmpty()

        override fun getDuration(): Long {
            if (getStreamType() == StreamType.LIVE_STREAM) return -1L
            val text = clip["duration"] as? String ?: return -1L
            if (!Regex("[0-9]{1,7}(?::[0-5][0-9]){1,2}").matches(text)) return -1L
            return text.split(':').fold(0L) { total, part -> total * 60L + part.toLong() }
        }

        override fun getViewCount(): Long = if (getStreamType() == StreamType.LIVE_STREAM) {
            -1L
        } else {
            (clip["plays"] as? Number)?.toLong()?.coerceAtLeast(-1L) ?: -1L
        }

        override fun getUploaderName(): String = (clip["owner"] as? String).orEmpty()

        override fun getTextualUploadDate(): String? = null

        override fun getUploadDate(): DateWrapper? = null
    }

    companion object {
        private fun query(url: String): Map<String, String> = try {
            URI(url).rawQuery?.split('&')?.associate { part ->
                val key = URLDecoder.decode(part.substringBefore('='), StandardCharsets.UTF_8.name())
                val value = URLDecoder.decode(part.substringAfter('=', ""), StandardCharsets.UTF_8.name())
                key to value
            }.orEmpty()
        } catch (error: IllegalArgumentException) {
            throw ParsingException("Invalid Vimeo showcase continuation query", error)
        }

        private fun pageNumber(url: String): Long = query(url)["page"]?.toLongOrNull() ?: 1L

        private fun nonnegativeCount(value: Any?): Long = (value as? Number)?.toString()?.toLongOrNull()?.takeIf { it >= 0 }
            ?: throw ParsingException("Vimeo showcase has no valid video count")

        private fun unavailable(document: Document, recognizeText: Boolean) {
            if (document.selectFirst("input[type=password]") != null ||
                URI(document.location()).path in setOf("/log_in", "/login")
            ) {
                throw PrivateContentException("This Vimeo showcase is not available anonymously")
            }
            // A public collection's own title can contain words such as "private" or "password".
            if (!recognizeText) return
            val text = document.title() + " " + document.select("h1, h2, .exception, .message").text()
            if (text.contains("password", true) || text.contains("private", true)) {
                throw PrivateContentException("This Vimeo showcase is not available anonymously")
            }
            if (text.contains("not available in your region", true) || text.contains("not available in your country", true)) {
                throw GeographicRestrictionException("This Vimeo showcase is not available in this region")
            }
            if (text.contains("not found", true) || text.contains("does not exist", true)) {
                throw ContentNotAvailableException("This Vimeo showcase is unavailable")
            }
        }
    }
}
