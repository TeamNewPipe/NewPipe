/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

/** Custom showcase names are aliases until the public page declares its numeric canonical URL. */
data class VimeoPlaylistTarget private constructor(val value: String, val custom: Boolean) {
    val id: String get() = if (custom) "slug:$value" else value
    val canonicalUrl: String get() = "https://vimeo.com/showcase/$value"
    val embedUrl: String get() = "$canonicalUrl/embed"

    companion object {
        private val NUMBER = Regex("[1-9][0-9]{0,18}")
        private val SLUG = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,99}")

        @JvmStatic
        fun fromId(value: String?): VimeoPlaylistTarget? {
            if (value == null) return null
            if (NUMBER.matches(value) && value.toLongOrNull() != null) return VimeoPlaylistTarget(value, false)
            if (!value.startsWith("slug:")) return null
            val slug = value.removePrefix("slug:")
            return slug.takeIf { SLUG.matches(it) && !it.all(Char::isDigit) }
                ?.let { VimeoPlaylistTarget(it, true) }
        }

        @JvmStatic
        fun fromUrl(value: String?): VimeoPlaylistTarget? {
            val uri = VimeoVideoTarget.publicUri(value) ?: return null
            if (uri.host.equals("player.vimeo.com", true)) return null
            val parts = uri.rawPath.orEmpty().removePrefix("/").removeSuffix("/").split('/')
            if (parts.size !in 2..3 || parts[0] !in setOf("showcase", "album") ||
                parts.size == 3 && parts[2] !in setOf("embed", "embed2")
            ) {
                return null
            }
            return fromId(parts[1]) ?: if (parts[0] == "showcase") fromId("slug:${parts[1]}") else null
        }
    }
}
