/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import com.grack.nanojson.JsonObject
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ServiceTemporaryBlockedException
import org.schabi.newpipe.extractor.linkhandler.LinkHandler
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream

/** Playback from the same anonymous configuration that Vimeo advertises in its public player. */
class VimeoStreamExtractor(service: StreamingService, linkHandler: LinkHandler) : StreamExtractor(service, linkHandler) {
    private val target = VimeoVideoTarget.fromId(linkHandler.id)
        ?: throw ParsingException("Invalid Vimeo video identity")
    private lateinit var video: JsonObject
    private var metadata = JsonObject()
    private var progressive = emptyList<VideoStream>()
    private var subtitles = emptyList<SubtitlesStream>()
    private var hls = ""
    private var dash = ""

    override fun onFetchPage(downloader: Downloader) {
        hls = ""
        dash = ""
        progressive = emptyList()
        subtitles = emptyList()
        metadata = JsonObject()
        val document = VimeoApi.getDocument(target.playerUrl, downloader)
        val config = VimeoPlayerConfig.fromDocument(document, target)
        video = VimeoPlayerConfig.validate(config, target)
        val request = config["request"] as? JsonObject ?: JsonObject()
        val liveStatus = (video["live_event"] as? JsonObject)?.getString("status", "").orEmpty()
        if (liveStatus in setOf("pending", "active", "started")) {
            throw ContentNotSupportedException("Live Vimeo events are not supported")
        }
        val files = (video["files"] as? JsonObject)?.takeIf { it.isNotEmpty() }
            ?: (request["files"] as? JsonObject) ?: JsonObject()
        val protectedAdaptive = VimeoPlayerConfig.hasDrm(request["drm"]) || VimeoPlayerConfig.hasDrm(video["drm"]) ||
            VimeoPlayerConfig.hasDrm(files["drm"])
        var protectedResource = protectedAdaptive
        progressive = files.getArray("progressive")?.filterIsInstance<JsonObject>().orEmpty().mapNotNull { resource ->
            val url = VimeoPlayerConfig.mediaUrl(resource.getString("url")) ?: return@mapNotNull null
            if (VimeoPlayerConfig.isProtected(resource, url)) {
                protectedResource = true
                return@mapNotNull null
            }
            val format = when (resource.getString("mime", "").substringBefore(';')) {
                "video/mp4" -> MediaFormat.MPEG_4

                "video/webm" -> MediaFormat.WEBM

                else -> when (URI(url).path.substringAfterLast('.')) {
                    "mp4" -> MediaFormat.MPEG_4
                    "webm" -> MediaFormat.WEBM
                    else -> return@mapNotNull null
                }
            }
            val height = resource.getInt("height", -1)
            VideoStream.Builder()
                .setId(resource.getString("id", "").ifBlank { "${target.id}-${resource.getString("quality", "unknown")}" })
                .setContent(url, true)
                .setMediaFormat(format)
                .setIsVideoOnly(false)
                .setResolution(resource.getString("quality", "").ifBlank { if (height > 0) "${height}p" else VideoStream.RESOLUTION_UNKNOWN })
                .setWidth(resource.getInt("width", -1))
                .setHeight(height)
                .setFps(resource.getInt("fps", -1))
                .build()
        }.distinctBy { it.content }
        if (!protectedAdaptive) {
            hls = manifest(files["hls"] as? JsonObject, "m3u8")
            dash = manifest(files["dash"] as? JsonObject, "mpd")
            protectedResource = protectedResource || listOf("hls", "dash").any { key ->
                val manifest = files[key] as? JsonObject ?: return@any false
                VimeoPlayerConfig.hasDrm(manifest["drm"]) || VimeoPlayerConfig.hasDrm(manifest["drm_info"]) ||
                    VimeoPlayerConfig.hasDrm(manifest["encrypted"]) ||
                    (manifest["cdns"] as? JsonObject)?.values?.filterIsInstance<JsonObject>().orEmpty().any { cdn ->
                        listOf("avc_url", "url").any { field ->
                            VimeoPlayerConfig.mediaUrl(cdn.getString(field))?.let { VimeoPlayerConfig.isProtected(cdn, it) } == true
                        }
                    }
            }
        }
        if (progressive.isEmpty() && hls.isEmpty() && dash.isEmpty()) {
            if (protectedResource) throw ContentNotSupportedException("This Vimeo video only offers DRM-protected playback")
            throw ContentNotAvailableException("Vimeo did not provide a supported public playback resource")
        }
        subtitles = request.getArray("text_tracks")?.filterIsInstance<JsonObject>().orEmpty().mapNotNull { track ->
            val language = track.getString("lang", "")
            if (!Regex("[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*").matches(language)) return@mapNotNull null
            if (track.getString("kind", "") !in setOf("", "captions", "subtitles")) return@mapNotNull null
            val url = VimeoPlayerConfig.mediaUrl(track.getString("url")) ?: return@mapNotNull null
            if (!URI(url).path.endsWith(".vtt", true)) return@mapNotNull null
            if (VimeoPlayerConfig.isProtected(track, url)) return@mapNotNull null
            SubtitlesStream.Builder()
                .setId(track["id"]?.toString() ?: language)
                .setContent(url, true)
                .setMediaFormat(MediaFormat.VTT)
                .setLanguageCode(language)
                .setAutoGenerated(track.getString("provenance", "") in setOf("auto_generated", "machine_generated"))
                .build()
        }
        metadata = optionalMetadata(downloader)
    }

    private fun manifest(manifest: JsonObject?, extension: String): String {
        if (manifest == null || VimeoPlayerConfig.hasDrm(manifest["drm"])) return ""
        val cdns = manifest["cdns"] as? JsonObject ?: return ""
        val preferred = manifest.getString("default_cdn", "")
        val names = (listOf(preferred) + cdns.keys.sorted()).distinct()
        for (name in names) {
            val cdn = cdns[name] as? JsonObject ?: continue
            for (field in listOf("avc_url", "url")) {
                val url = VimeoPlayerConfig.mediaUrl(cdn.getString(field)) ?: continue
                // Vimeo also calls its proprietary playlist.json format "dash"; Media3 needs an MPD.
                if (URI(url).path.endsWith(".$extension", true) &&
                    !VimeoPlayerConfig.isProtected(manifest, url) && !VimeoPlayerConfig.isProtected(cdn, url)
                ) {
                    return url
                }
            }
        }
        return ""
    }

    private fun optionalMetadata(downloader: Downloader): JsonObject {
        val url = "${VimeoApi.BASE_URL}/api/oembed.json?url=" + URLEncoder.encode(target.canonicalUrl, StandardCharsets.UTF_8.name())
        val result = try {
            VimeoApi.getJson(url, downloader)
        } catch (error: ExtractionException) {
            if (error is PrivateContentException || error is PaidContentException ||
                error is GeographicRestrictionException || error is ServiceTemporaryBlockedException
            ) {
                throw error
            }
            return JsonObject()
        } catch (_: IOException) {
            return JsonObject()
        }
        if (result.getInt("domain_status_code", 200) == 403) {
            throw PrivateContentException("This Vimeo video cannot be embedded here")
        }
        if (result["video_id"] != null && result["video_id"].toString() != target.videoId) {
            throw ParsingException("Vimeo oEmbed metadata belongs to another video")
        }
        return result
    }

    override fun getName(): String = video.getString("title", "").ifBlank {
        metadata.getString("title", "").ifBlank { throw ParsingException("Vimeo did not provide a video title") }
    }

    override fun getLength(): Long = video.getLong("duration", -1L)

    override fun getUploaderName(): String = (video["owner"] as? JsonObject)?.getString("name", "").orEmpty()

    override fun getUploaderUrl(): String {
        val url = (video["owner"] as? JsonObject)?.getString("url", "") ?: return ""
        return VimeoChannelTarget.fromUrl(url)?.canonicalUrl.orEmpty()
    }

    override fun getUploaderAvatarUrl(): String {
        val owner = video["owner"] as? JsonObject ?: return ""
        return VimeoPlayerConfig.mediaUrl(owner.getString("img_2x")) ?: VimeoPlayerConfig.mediaUrl(owner.getString("img")) ?: ""
    }

    override fun getThumbnailUrl(): String = VimeoPlayerConfig.mediaUrl(video.getString("thumbnail_url"))
        ?: VimeoPlayerConfig.mediaUrl(metadata.getString("thumbnail_url")) ?: ""

    override fun getDescription(): Description = Description(metadata.getString("description", ""), Description.PLAIN_TEXT)

    override fun getTextualUploadDate(): String? = metadata.getString("upload_date")?.takeIf { it.isNotBlank() }

    override fun getUploadDate(): DateWrapper? {
        val text = textualUploadDate ?: return null
        return try {
            // oEmbed does not supply an offset; preserve its calendar date as an approximation.
            DateWrapper(LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atOffset(ZoneOffset.UTC), true)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    override fun getTimeStamp(): Long {
        val uri = VimeoVideoTarget.publicUri(originalUrl) ?: return 0L
        val timestamps = try {
            listOfNotNull(uri.rawQuery, uri.rawFragment).flatMap { it.split('&') }.mapNotNull { parameter ->
                if (URLDecoder.decode(parameter.substringBefore('='), StandardCharsets.UTF_8.name()) != "t") return@mapNotNull null
                URLDecoder.decode(parameter.substringAfter('=', ""), StandardCharsets.UTF_8.name())
            }
        } catch (_: IllegalArgumentException) {
            return 0L
        }
        val value = timestamps.singleOrNull() ?: return 0L
        val seconds = if (Regex("[0-9]+").matches(value)) {
            value.toLongOrNull() ?: return 0L
        } else {
            val match = Regex("(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?").matchEntire(value) ?: return 0L
            if (value.isEmpty()) return 0L
            try {
                match.groupValues.drop(1).zip(listOf(3600L, 60L, 1L)).fold(0L) { sum, (number, multiplier) ->
                    Math.addExact(sum, Math.multiplyExact(number.ifEmpty { "0" }.toLong(), multiplier))
                }
            } catch (_: ArithmeticException) {
                return 0L
            } catch (_: NumberFormatException) {
                return 0L
            }
        }
        // The player converts seconds to milliseconds before seeking.
        return seconds.takeIf { it <= Long.MAX_VALUE / 1000L } ?: 0L
    }

    override fun getPrivacy(): Privacy = if (target.unlistedHash != null) Privacy.UNLISTED else Privacy.PUBLIC

    override fun getHost(): String = "vimeo.com"

    override fun getStreamType(): StreamType = StreamType.VIDEO_STREAM

    override fun getVideoStreams(): List<VideoStream> {
        val (url, delivery) = if (hls.isNotEmpty()) hls to DeliveryMethod.HLS else dash to DeliveryMethod.DASH
        if (url.isEmpty()) return progressive
        val adaptive = VideoStream.Builder()
            .setId("${target.id}-${delivery.name.lowercase()}")
            .setContent(url, true)
            .setMediaFormat(MediaFormat.MPEG_4)
            .setDeliveryMethod(delivery)
            .setManifestUrl(url)
            .setIsVideoOnly(false)
            .setResolution(VideoStream.RESOLUTION_UNKNOWN)
            .build()
        return progressive + adaptive
    }

    override fun getVideoOnlyStreams(): List<VideoStream> = emptyList()

    override fun getAudioStreams(): List<AudioStream> = emptyList()

    override fun getHlsUrl(): String = hls

    override fun getDashMpdUrl(): String = dash

    override fun getSubtitlesDefault(): List<SubtitlesStream> = subtitles

    override fun getSubtitles(format: MediaFormat): List<SubtitlesStream> = if (format == MediaFormat.VTT) subtitles else emptyList()
}
