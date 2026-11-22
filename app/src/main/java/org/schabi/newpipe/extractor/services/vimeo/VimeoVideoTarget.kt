/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/** The share hash is part of a video's identity and must survive every URL round trip. */
data class VimeoVideoTarget private constructor(val videoId: String, val unlistedHash: String?) {
    val id: String get() = videoId + unlistedHash?.let { "/$it" }.orEmpty()
    val canonicalUrl: String get() = "https://vimeo.com/$id"
    val playerUrl: String get() = "https://player.vimeo.com/video/$videoId" + hashQuery
    private val hashQuery: String get() = unlistedHash?.let { "?h=$it" }.orEmpty()

    companion object {
        private val VIDEO_ID = Regex("[1-9][0-9]{0,18}")
        private val SHARE_HASH = Regex("[a-fA-F0-9]{10}")
        private val SLUG = Regex("[A-Za-z0-9_-]+")

        @JvmStatic
        fun fromId(value: String?): VimeoVideoTarget? {
            val parts = value?.split('/') ?: return null
            if (parts.size !in 1..2 || !validId(parts[0])) return null
            val hash = parts.getOrNull(1)
            if (hash != null && !SHARE_HASH.matches(hash)) return null
            return VimeoVideoTarget(parts[0], hash)
        }

        @JvmStatic
        fun fromUrl(value: String?): VimeoVideoTarget? {
            val uri = publicUri(value) ?: return null
            val parts = uri.rawPath.orEmpty().removePrefix("/").removeSuffix("/").split('/')
            val pathTarget = when {
                uri.host.equals("player.vimeo.com", true) ->
                    parts.takeIf { it.size == 2 && it[0] == "video" }?.let { fromId(it[1]) }

                parts.size in 1..2 -> fromId(parts.joinToString("/"))

                parts.size == 3 && parts[0] == "channels" && SLUG.matches(parts[1]) -> fromId(parts[2])

                parts.size == 4 && parts[0] == "groups" && SLUG.matches(parts[1]) && parts[2] == "videos" -> fromId(parts[3])

                parts.size == 4 && parts[0] in setOf("album", "showcase") && validId(parts[1]) && parts[2] == "video" -> fromId(parts[3])

                else -> null
            } ?: return null
            val hashes = try {
                uri.rawQuery.orEmpty().split('&').mapNotNull { parameter ->
                    val key = URLDecoder.decode(parameter.substringBefore('='), StandardCharsets.UTF_8.name())
                    if (key == "h") URLDecoder.decode(parameter.substringAfter('=', ""), StandardCharsets.UTF_8.name()) else null
                }
            } catch (_: IllegalArgumentException) {
                return null
            }
            if (hashes.size > 1 || hashes.any { !SHARE_HASH.matches(it) }) return null
            val queryHash = hashes.singleOrNull()
            if (pathTarget.unlistedHash != null && queryHash != null && pathTarget.unlistedHash != queryHash) return null
            return VimeoVideoTarget(pathTarget.videoId, pathTarget.unlistedHash ?: queryHash)
        }

        internal fun publicUri(value: String?): URI? {
            if (value.isNullOrBlank()) return null
            val uri = try {
                URI(value)
            } catch (_: Exception) {
                return null
            }
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            if (scheme !in setOf("http", "https") || uri.rawUserInfo != null ||
                uri.host?.lowercase(Locale.ROOT) !in setOf("vimeo.com", "www.vimeo.com", "player.vimeo.com") ||
                uri.port != -1 && uri.port != (if (scheme == "https") 443 else 80)
            ) {
                return null
            }
            return uri
        }

        private fun validId(value: String): Boolean = VIDEO_ID.matches(value) && value.toLongOrNull() != null
    }
}
