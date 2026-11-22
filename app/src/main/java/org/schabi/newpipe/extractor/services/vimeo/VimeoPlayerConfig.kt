/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import java.net.URI
import java.util.Locale
import org.jsoup.nodes.Document
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException

/** Reads the ordinary public player's advertised configuration without executing its scripts. */
internal object VimeoPlayerConfig {
    private val ASSIGNMENT = Regex("\\b(?:window\\.)?(?:playerConfig|config)\\s*=\\s*(?=\\{)")

    fun fromDocument(document: Document, target: VimeoVideoTarget): JsonObject {
        val redirected = VimeoVideoTarget.fromUrl(document.location())
        if (redirected == null || redirected.videoId != target.videoId ||
            redirected.unlistedHash != null && redirected.unlistedHash != target.unlistedHash
        ) {
            throw ParsingException("Vimeo player redirected to another video")
        }
        for (script in document.select("script")) {
            val source = script.data()
            val assignment = ASSIGNMENT.find(source) ?: continue
            val start = assignment.range.last + 1
            val json = balancedObject(source, start)
                ?: throw ParsingException("Vimeo player returned an incomplete configuration")
            return try {
                JsonParser.`object`().from(json)
            } catch (error: Exception) {
                throw ParsingException("Vimeo player returned invalid configuration JSON", error)
            }
        }
        val message = document.title() + " " + document.select("h1, h2, .exception, .message").text()
        when {
            message.contains("password", true) -> throw PrivateContentException("This Vimeo video requires a password")

            message.contains("private", true) || message.contains("privacy", true) ->
                throw PrivateContentException("Vimeo restricts anonymous access to this video")

            message.contains("does not exist", true) || message.contains("not found", true) ->
                throw ContentNotAvailableException("This Vimeo video is unavailable")

            else -> throw ParsingException("The Vimeo player did not advertise a video configuration")
        }
    }

    fun validate(config: JsonObject, target: VimeoVideoTarget): JsonObject {
        when (config.getInt("view", 1)) {
            2 -> throw PrivateContentException("This Vimeo video is private")
            4 -> throw PrivateContentException("This Vimeo video requires a password")
        }
        when (config.getInt("domain_status_code", 200)) {
            403 -> throw PrivateContentException("This Vimeo video cannot be embedded here")
            451 -> throw GeographicRestrictionException("This Vimeo video is unavailable in this region")
        }
        if (config.getInt("view", 1) != 1) {
            throw ContentNotAvailableException("The Vimeo player does not permit playback of this video")
        }
        val video = config["video"] as? JsonObject
            ?: throw ParsingException("Vimeo player configuration has no video")
        if (video["id"].toString() != target.videoId) {
            throw ParsingException("Vimeo player configuration belongs to another video")
        }
        val hash = video.getString("unlisted_hash", "").orEmpty()
        if (hash.isNotEmpty() && hash != target.unlistedHash) {
            throw PrivateContentException("This Vimeo video requires its complete shared URL")
        }
        when (video.getString("privacy", "").lowercase(Locale.ROOT)) {
            "anybody", "disable", "" -> Unit

            "unlisted" -> if (target.unlistedHash == null) {
                throw PrivateContentException("This Vimeo video requires its complete shared URL")
            }

            "password" -> throw PrivateContentException("This Vimeo video requires a password")

            else -> throw PrivateContentException("Vimeo restricts anonymous access to this video")
        }
        val embedPermission = video.getString("embed_permission", "").lowercase(Locale.ROOT)
        if (embedPermission.isNotEmpty() && embedPermission != "public") {
            throw PrivateContentException("This Vimeo video cannot be embedded here")
        }
        if (config.getInt("status", 200) == 402) {
            throw PaidContentException("This Vimeo video requires a purchase")
        }
        return video
    }

    /** Signed media URLs are used verbatim; they are never changed into guessed clear resources. */
    fun mediaUrl(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val uri = try {
            URI("https://player.vimeo.com").resolve(value)
        } catch (_: Exception) {
            return null
        }
        if (uri.scheme != "https" || uri.rawUserInfo != null || uri.port !in setOf(-1, 443)) return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (host !in setOf("player.vimeo.com", "captions.vimeo.com", "captions.cloud.vimeo.com", "vod-progressive.akamaized.net", "vod-adaptive.akamaized.net") &&
            host != "vimeocdn.com" && !host.endsWith(".vimeocdn.com")
        ) {
            return null
        }
        return uri.toString()
    }

    fun hasDrm(value: Any?): Boolean = when (value) {
        is JsonObject -> value.any { (key, entry) -> key != "user" && hasDrm(entry) }
        is Collection<*> -> value.any(::hasDrm)
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> value.isNotEmpty() && value != "0" && !value.equals("false", true)
        else -> false
    }

    fun isProtected(resource: JsonObject, url: String): Boolean = hasDrm(resource["drm"]) || hasDrm(resource["drm_info"]) || hasDrm(resource["encrypted"]) ||
        URI(url).path.orEmpty().split('/').any { it.equals("drm", true) }

    private fun balancedObject(source: String, start: Int): String? {
        var depth = 0
        var quoted = false
        var escaped = false
        for (index in start until source.length) {
            val character = source[index]
            if (quoted) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> quoted = false
                }
            } else {
                when (character) {
                    '"' -> quoted = true
                    '{' -> depth++
                    '}' -> if (--depth == 0) return source.substring(start, index + 1)
                }
            }
        }
        return null
    }
}
