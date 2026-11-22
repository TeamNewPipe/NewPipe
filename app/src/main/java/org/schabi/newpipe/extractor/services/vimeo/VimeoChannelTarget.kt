/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import java.util.Locale
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.exceptions.ParsingException

/** Vimeo creator profiles and curated channels have separate, stable list identities. */
data class VimeoChannelTarget private constructor(val slug: String, val curated: Boolean) {
    val id: String get() = if (curated) "channel:$slug" else "user:$slug"
    val canonicalUrl: String get() = "https://vimeo.com/" + if (curated) "channels/$slug" else slug

    fun pageUrl(number: Int): String {
        require(number > 0) { "Invalid Vimeo page number" }
        return "$canonicalUrl/videos/page:$number/"
    }

    internal fun pageNumber(page: Page?): Int {
        val uri = VimeoVideoTarget.publicUri(page?.url)
            ?: throw ParsingException("Invalid Vimeo listing continuation")
        if (uri.host.equals("player.vimeo.com", true) || uri.rawQuery != null || uri.rawFragment != null) {
            throw ParsingException("Invalid Vimeo listing continuation")
        }
        val path = if (curated) "/channels/$slug" else "/$slug"
        val sort = if (curated) "preset" else "date"
        val match = Regex("${Regex.escape(path)}/videos/page:([1-9][0-9]*)(?:/sort:$sort)?/?").matchEntire(uri.rawPath)
            ?: throw ParsingException("Vimeo continuation belongs to another listing")
        return match.groupValues[1].toIntOrNull()
            ?: throw ParsingException("Invalid Vimeo page number")
    }

    companion object {
        private val SLUG = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")
        private val RESERVED = setOf(
            "about", "album", "api", "blog", "categories", "channels", "create", "enterprise",
            "event", "events", "features", "groups", "help", "join", "live", "log_in", "login",
            "logout", "manage", "ondemand", "pricing", "search", "settings", "showcase", "stock",
            "terms", "upgrade", "upload", "video", "videos", "watch", "watchlater"
        )

        @JvmStatic
        fun fromId(value: String?): VimeoChannelTarget? {
            val kind = value?.substringBefore(':') ?: return null
            if (kind != "user" && kind != "channel") return null
            val slug = value.substringAfter(':', "")
            if (!SLUG.matches(slug)) return null
            if (kind == "user" && (slug.all(Char::isDigit) || slug.lowercase(Locale.ROOT) in RESERVED)) return null
            return VimeoChannelTarget(slug, kind == "channel")
        }

        @JvmStatic
        fun fromUrl(value: String?): VimeoChannelTarget? {
            val uri = VimeoVideoTarget.publicUri(value) ?: return null
            if (uri.host.equals("player.vimeo.com", true)) return null
            val parts = uri.rawPath.orEmpty().removePrefix("/").removeSuffix("/").split('/')
            return when {
                parts.size in 2..3 && parts[0] == "channels" &&
                    (parts.size == 2 || parts[2] == "videos") -> fromId("channel:${parts[1]}")

                parts.size in 1..2 && (parts.size == 1 || parts[1] == "videos") -> fromId("user:${parts[0]}")

                else -> null
            }
        }
    }
}
