/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import java.net.URI
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor
import org.schabi.newpipe.extractor.stream.StreamType

/** Public API video representations, shared by keyword search and collection pages. */
class VimeoApiStreamInfoItemExtractor(private val video: JsonObject) : StreamInfoItemExtractor {
    private val target = VimeoVideoTarget.fromUrl(video["link"] as? String)
        ?: throw ParsingException("Vimeo API video has no supported shared URL")
    private val owner = video["user"] as? JsonObject

    init {
        val uri = video["uri"] as? String
        val expectedUris = listOfNotNull("/videos/${target.videoId}", target.unlistedHash?.let { "/videos/${target.videoId}:$it" })
        if (uri != null && uri !in expectedUris) {
            throw ParsingException("Vimeo API video identities do not match")
        }
        if ((video["privacy"] as? JsonObject)?.getString("view") == "unlisted" && target.unlistedHash == null) {
            throw ParsingException("Vimeo API omitted the video's complete shared URL")
        }
    }

    override fun getName(): String = (video["name"] as? String)?.takeIf(String::isNotBlank)
        ?: throw ParsingException("Vimeo API video has no title")

    override fun getUrl(): String = target.canonicalUrl

    override fun getStreamType(): StreamType = StreamType.VIDEO_STREAM

    override fun getThumbnails(): List<Image> = VimeoApiImages.from(video["pictures"] as? JsonObject)

    override fun getDuration(): Long = (video["duration"] as? Number)?.toLong()?.coerceAtLeast(-1L) ?: -1L

    override fun getViewCount(): Long = ((video["stats"] as? JsonObject)?.get("plays") as? Number)
        ?.toLong()?.coerceAtLeast(-1L) ?: -1L

    override fun getUploaderName(): String = (owner?.get("name") as? String).orEmpty()

    override fun getUploaderUrl(): String = VimeoChannelTarget.fromUrl(owner?.get("link") as? String)?.canonicalUrl.orEmpty()

    override fun getUploaderAvatarUrl(): String? = VimeoApiImages.from(owner?.get("pictures") as? JsonObject).firstOrNull()?.url

    override fun getTextualUploadDate(): String? = (video["release_time"] as? String)?.takeIf(String::isNotBlank)
        ?: (video["created_time"] as? String)?.takeIf(String::isNotBlank)

    override fun getUploadDate(): DateWrapper? = VimeoApiDates.parse(getTextualUploadDate())

    override fun getShortDescription(): String? = video["description"] as? String
}

internal object VimeoApiImages {
    fun from(pictures: JsonObject?): List<Image> {
        val sizes = pictures?.get("sizes") as? JsonArray ?: return emptyList()
        return sizes.filterIsInstance<JsonObject>().mapNotNull { size ->
            val url = VimeoPlayerConfig.mediaUrl(size["link"] as? String) ?: return@mapNotNull null
            val height = (size["height"] as? Number)?.toInt()?.takeIf { it > 0 } ?: Image.HEIGHT_UNKNOWN
            val width = (size["width"] as? Number)?.toInt()?.takeIf { it > 0 } ?: Image.WIDTH_UNKNOWN
            val resolution = when {
                height >= 720 -> Image.ResolutionLevel.HIGH
                height >= 175 -> Image.ResolutionLevel.MEDIUM
                height > 0 -> Image.ResolutionLevel.LOW
                else -> Image.ResolutionLevel.UNKNOWN
            }
            Image(url, height, width, resolution)
        }.distinctBy { it.url }.sortedByDescending { it.width }
    }
}

internal object VimeoApiDates {
    fun parse(value: String?): DateWrapper? {
        if (value.isNullOrBlank()) return null
        return try {
            DateWrapper(OffsetDateTime.parse(value))
        } catch (error: DateTimeParseException) {
            throw ParsingException("Vimeo API returned an invalid publication date", error)
        }
    }
}

/** A recognized empty collection is distinct from an API error or an unknown response shape. */
internal object VimeoApiCollection {
    fun items(response: JsonObject): List<JsonObject> {
        val data = response["data"] as? JsonArray ?: throw ParsingException("Vimeo API returned an invalid collection")
        return data.map { it as? JsonObject ?: throw ParsingException("Vimeo API returned an invalid collection item") }
    }

    fun next(response: JsonObject, expectedPath: String): Page? {
        val paging = response["paging"] as? JsonObject ?: throw ParsingException("Vimeo API collection has no pagination")
        if (!paging.has("next") || paging["next"] != null && paging["next"] !is String) {
            throw ParsingException("Vimeo API returned invalid pagination")
        }
        return VimeoPublicApi.nextPage(paging["next"] as? String, expectedPath)
    }

    /** Page identity is checked against the request, independently of a reduced or filtered list. */
    fun next(response: JsonObject, expectedPath: String, requestUrl: String): Page? {
        val next = next(response, expectedPath) ?: return null
        val requested = parameters(VimeoPublicApi.validatePage(Page(requestUrl), expectedPath))
        val continued = parameters(next.url)
        val currentPage = requested["page"]?.toInt() ?: 1
        val nextPage = continued["page"]?.toInt() ?: 1
        if (nextPage <= currentPage) throw ParsingException("Vimeo API pagination did not advance")
        val requestedSize = requested["per_page"]?.toInt()
        if (requestedSize != null && continued["per_page"]?.toInt() != requestedSize) {
            throw ParsingException("Vimeo API pagination changed the requested page size")
        }
        return next
    }

    private fun parameters(url: String): Map<String, String> = URI(url).rawQuery.orEmpty().split('&')
        .filter(String::isNotEmpty).associate { parameter ->
            VimeoSearchQueryHandlerFactory.decode(parameter.substringBefore('=')) to
                VimeoSearchQueryHandlerFactory.decode(parameter.substringAfter('='))
        }
}
