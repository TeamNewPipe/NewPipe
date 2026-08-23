package org.schabi.newpipe.util.urlfinder

import java.net.URI

/**
 * Normalizes common YouTube URL variants to the watch URL shape already handled by PipePlay's
 * router/extractor.
 */
object YouTubeUrlNormalizer {
    private const val WATCH_PREFIX = "https://www.youtube.com/watch?v="

    @JvmStatic
    fun normalize(url: String?): String? {
        if (url.isNullOrBlank()) {
            return url
        }

        val trimmed = url.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return trimmed
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return trimmed
        val path = uri.rawPath.orEmpty()

        val videoId = when {
            host == "youtu.be" -> firstPathSegment(path)
            host == "youtube.com" || host == "m.youtube.com" ||
                host == "mobile.youtube.com" || host == "music.youtube.com" -> {
                queryParameter(uri.rawQuery, "v")
                    ?: pathSegmentAfter(path, "shorts")
                    ?: pathSegmentAfter(path, "embed")
                    ?: pathSegmentAfter(path, "v")
            }
            host == "youtube-nocookie.com" ->
                queryParameter(uri.rawQuery, "v")
                    ?: pathSegmentAfter(path, "embed")
                    ?: pathSegmentAfter(path, "v")
            else -> null
        }

        return if (videoId.isNullOrBlank()) trimmed else WATCH_PREFIX + videoId
    }

    private fun queryParameter(rawQuery: String?, key: String): String? {
        if (rawQuery.isNullOrEmpty()) {
            return null
        }
        return rawQuery.split('&')
            .mapNotNull { parameter ->
                val separator = parameter.indexOf('=')
                if (separator <= 0) {
                    null
                } else {
                    parameter.substring(0, separator) to parameter.substring(separator + 1)
                }
            }
            .firstOrNull { it.first == key }
            ?.second
            ?.substringBefore('&')
    }

    private fun firstPathSegment(path: String): String? = path
        .trim('/')
        .split('/')
        .firstOrNull()
        ?.takeIf { it.isNotBlank() }

    private fun pathSegmentAfter(path: String, marker: String): String? {
        val segments = path.trim('/').split('/')
        val index = segments.indexOf(marker)
        return if (index >= 0 && index + 1 < segments.size) {
            segments[index + 1].takeIf { it.isNotBlank() }
        } else {
            null
        }
    }
}
