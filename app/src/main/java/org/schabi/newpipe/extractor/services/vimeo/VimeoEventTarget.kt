/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** A recurring event is a stable identity, separate from the video selected for its current session. */
data class VimeoEventTarget private constructor(val eventId: String, val unlistedHash: String?, val videoId: String?) {
    val id: String get() = "event/$eventId" + unlistedHash?.let { "/$it" }.orEmpty() + videoId?.let { "/videos/$it" }.orEmpty()
    val canonicalUrl: String get() = "https://vimeo.com/$id"
    val embedUrl: String get() = "https://vimeo.com/event/$eventId/embed" + unlistedHash?.let { "/$it" }.orEmpty()

    companion object {
        @JvmStatic
        fun fromId(value: String?): VimeoEventTarget? {
            val parts = value?.split('/') ?: return null
            if (parts.size !in 2..4 || parts[0] != "event" || !validId(parts[1])) return null
            return when {
                parts.size == 2 -> VimeoEventTarget(parts[1], null, null)
                parts.size == 3 && validHash(parts[2]) -> VimeoEventTarget(parts[1], parts[2], null)
                parts.size == 4 && parts[2] == "videos" && validId(parts[3]) -> VimeoEventTarget(parts[1], null, parts[3])
                else -> null
            }
        }

        @JvmStatic
        fun fromUrl(value: String?): VimeoEventTarget? {
            val uri = VimeoVideoTarget.publicUri(value) ?: return null
            if (uri.host.equals("player.vimeo.com", true)) return null
            val parts = uri.rawPath.orEmpty().removePrefix("/").removeSuffix("/").split('/')
            if (parts.size < 2 || parts[0] != "event") return null
            val path = when {
                parts.size == 3 && parts[2] == "embed" -> parts.take(2)
                parts.size == 4 && parts[2] == "embed" && validHash(parts[3]) -> parts.take(2) + parts[3]
                else -> parts
            }
            // Event share hashes use the path. Never silently discard a supplied alternative hash.
            try {
                if (uri.rawQuery.orEmpty().split('&').any {
                        URLDecoder.decode(it.substringBefore('='), StandardCharsets.UTF_8.name()) == "h"
                    }
                ) {
                    return null
                }
            } catch (_: IllegalArgumentException) {
                return null
            }
            return fromId(path.joinToString("/"))
        }

        private fun validId(value: String): Boolean = VimeoVideoTarget.fromId(value)?.let { it.videoId == value && it.unlistedHash == null } == true

        private fun validHash(value: String): Boolean = VimeoVideoTarget.fromId("1/$value") != null
    }
}
