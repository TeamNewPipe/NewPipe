package org.schabi.newpipe.extractor.services.youtube.extractors

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import org.schabi.newpipe.extractor.channel.ChannelExternalLink

internal data class YoutubeChannelAboutInfo(
    val joinedDateText: String?,
    val externalLinks: List<ChannelExternalLink>
) {
    companion object {
        @JvmStatic
        fun from(response: JsonObject?): YoutubeChannelAboutInfo {
            val about = findFirstObject(response, "aboutChannelViewModel")
                ?: return YoutubeChannelAboutInfo(null, emptyList())
            val joinedDateText = text(about["joinedDateText"])
            val linkModels = mutableListOf<JsonObject>()
            collectObjects(about, "channelExternalLinkViewModel", linkModels)

            val links = linkModels.mapNotNull(::externalLink)
                .distinctBy(ChannelExternalLink::url)
            return YoutubeChannelAboutInfo(joinedDateText, links)
        }

        private fun externalLink(model: JsonObject): ChannelExternalLink? {
            val url = safeUrl(commandUrl(model) ?: return null) ?: return null
            val title = text(model["title"]).orEmpty().ifBlank {
                runCatching { URI(url).host.removePrefix("www.") }.getOrDefault("")
            }
            return ChannelExternalLink(title, url)
        }

        private fun commandUrl(model: JsonObject): String? {
            urlFromCommand(objectAt(model, "onTap", "innertubeCommand"))?.let {
                return it
            }
            urlFromCommand(model["navigationEndpoint"] as? JsonObject)?.let {
                return it
            }

            val commandRuns = model["commandRuns"] as? JsonArray ?: return null
            for (runValue in commandRuns) {
                val run = runValue as? JsonObject ?: continue
                val command = objectAt(run, "onTap", "innertubeCommand")
                urlFromCommand(command)?.let { return it }
            }
            return null
        }

        private fun urlFromCommand(command: JsonObject?): String? {
            return nestedString(command, "commandMetadata", "webCommandMetadata", "url")
                ?: nestedString(command, "urlEndpoint", "url")
        }

        private fun safeUrl(rawUrl: String): String? {
            val unwrapped = unwrapYoutubeRedirect(rawUrl.trim())
            val uri = runCatching { URI(unwrapped) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            if (scheme != "http" && scheme != "https") {
                return null
            }
            if (uri.host.isNullOrBlank()) {
                return null
            }
            return unwrapped
        }

        private fun unwrapYoutubeRedirect(rawUrl: String): String {
            val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return rawUrl
            if (uri.path != "/redirect") {
                return rawUrl
            }

            val host = uri.host
            if (host != null && host != "youtube.com" && host != "www.youtube.com") {
                return rawUrl
            }
            return queryParameter(uri.rawQuery, "q") ?: rawUrl
        }

        private fun queryParameter(query: String?, name: String): String? {
            if (query == null) {
                return null
            }
            for (part in query.split("&")) {
                val separator = part.indexOf('=')
                val rawName = if (separator >= 0) part.substring(0, separator) else part
                if (decode(rawName) == name) {
                    val rawValue = if (separator >= 0) part.substring(separator + 1) else ""
                    return decode(rawValue)
                }
            }
            return null
        }

        private fun decode(value: String): String {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }

        private fun text(value: Any?): String? {
            if (value is String) {
                return value.trim().takeIf { it.isNotEmpty() }
            }
            val objectValue = value as? JsonObject ?: return null
            for (key in listOf("content", "simpleText", "text")) {
                val candidate = objectValue[key]
                if (candidate is String && candidate.isNotBlank()) {
                    return candidate.trim()
                }
            }

            val runs = objectValue["runs"] as? JsonArray ?: return null
            val combined = buildString {
                for (runValue in runs) {
                    val run = runValue as? JsonObject ?: continue
                    append(run.getString("text", ""))
                }
            }
            return combined.trim().takeIf { it.isNotEmpty() }
        }

        private fun objectAt(root: JsonObject, vararg path: String): JsonObject? {
            var current: JsonObject? = root
            for (key in path) {
                current = current?.get(key) as? JsonObject ?: return null
            }
            return current
        }

        private fun nestedString(root: JsonObject?, vararg path: String): String? {
            if (root == null || path.isEmpty()) {
                return null
            }

            var current: Any? = root
            for (key in path) {
                current = (current as? JsonObject)?.get(key) ?: return null
            }
            return (current as? String)?.takeIf { it.isNotBlank() }
        }

        private fun findFirstObject(value: Any?, key: String): JsonObject? {
            when (value) {
                is JsonObject -> {
                    (value[key] as? JsonObject)?.let { return it }
                    for (child in value.values) {
                        findFirstObject(child, key)?.let { return it }
                    }
                }

                is JsonArray -> {
                    for (child in value) {
                        findFirstObject(child, key)?.let { return it }
                    }
                }
            }
            return null
        }

        private fun collectObjects(
            value: Any?,
            key: String,
            result: MutableList<JsonObject>
        ) {
            when (value) {
                is JsonObject -> {
                    (value[key] as? JsonObject)?.let(result::add)
                    for (child in value.values) {
                        collectObjects(child, key, result)
                    }
                }

                is JsonArray -> {
                    for (child in value) {
                        collectObjects(child, key, result)
                    }
                }
            }
        }
    }
}
