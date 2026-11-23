package org.schabi.newpipe.extractor.services.youtube.extractors

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

/** The known public post browse envelopes; unrelated or malformed responses are not empty pages. */
internal object YoutubePostCommentsResponse {
    data class Body(val items: List<JsonObject>, val mutations: JsonArray, val continuation: String?)

    fun checkError(response: JsonObject) {
        if (!response.has("error")) return
        val error = response.getObject("error")
        val message = error.getString("message").orEmpty().ifBlank {
            "YouTube community post comments are unavailable"
        }
        if (error.getInt("code") == 404 || error.getInt("code") == 403) {
            throw ContentNotAvailableException(message)
        }
        throw ExtractionException("YouTube post comments request failed: $message")
    }

    fun read(response: JsonObject): Body {
        checkError(response)
        val items = mutableListOf<JsonObject>()
        var recognizedBody = false
        if (response.has("onResponseReceivedEndpoints")) {
            for (entry in response.getArray("onResponseReceivedEndpoints")) {
                val endpoint = entry as? JsonObject ?: continue
                val command = when {
                    endpoint.has("reloadContinuationItemsCommand") ->
                        endpoint.getObject("reloadContinuationItemsCommand")

                    endpoint.has("appendContinuationItemsAction") ->
                        endpoint.getObject("appendContinuationItemsAction")

                    else -> continue
                }
                val slot = command.getString("slot").orEmpty()
                if (slot == "RELOAD_CONTINUATION_SLOT_HEADER") continue
                if (slot.isNotBlank() && slot != "RELOAD_CONTINUATION_SLOT_BODY") {
                    throw ParsingException("Unknown YouTube post comments reload slot")
                }
                if (!command.has("continuationItems") &&
                    command.getString("slot") != "RELOAD_CONTINUATION_SLOT_BODY"
                ) {
                    continue
                }
                recognizedBody = true
                appendItems(items, command, "continuationItems")
            }
        } else if (response.has("continuationContents")) {
            val contents = response.getObject("continuationContents")
            val key = when {
                contents.has("itemSectionContinuation") -> "itemSectionContinuation"
                contents.has("commentRepliesContinuation") -> "commentRepliesContinuation"
                else -> throw ParsingException("Unknown YouTube post comments continuation")
            }
            val body = contents[key] as? JsonObject
                ?: throw ParsingException("Malformed YouTube post comments continuation")
            recognizedBody = true
            appendItems(items, body, "contents")
        }
        if (!recognizedBody) {
            throw ParsingException("YouTube did not return a post comments page")
        }

        var continuation: String? = null
        val comments = mutableListOf<JsonObject>()
        for (item in items) {
            when {
                item.has("continuationItemRenderer") -> {
                    val token = continuationToken(item.getObject("continuationItemRenderer"))
                        ?: throw ParsingException("YouTube post comments continuation has no token")
                    if (continuation != null && continuation != token) {
                        throw ParsingException("YouTube returned conflicting post comment continuations")
                    }
                    continuation = token
                }

                item.has("messageRenderer") -> {
                    val message = YoutubeParsingHelper.getTextFromObject(
                        item.getObject("messageRenderer").getObject("text")
                    ).orEmpty().ifBlank { "YouTube community post comments are unavailable" }
                    // Includes disabled/restricted notices. Preserve provider text instead of
                    // treating a localized notice as an empty successful page.
                    throw ContentNotAvailableException(message)
                }

                item.has("commentThreadRenderer") || item.has("commentRenderer") ||
                    item.has("commentViewModel") -> comments += normalizeReplies(item)

                item.has("commentsHeaderRenderer") || item.has("commentFilterContextViewModel") -> Unit

                else -> throw ParsingException("Unknown item in YouTube post comments page")
            }
        }
        return Body(
            comments,
            response.getObject("frameworkUpdates").getObject("entityBatchUpdate").getArray("mutations"),
            continuation
        )
    }

    private fun appendItems(target: MutableList<JsonObject>, container: JsonObject, key: String) {
        if (!container.has(key)) return // Known body without contents represents no comments.
        val values = container[key] as? JsonArray
            ?: throw ParsingException("Malformed YouTube post comments items")
        for (value in values) {
            target += value as? JsonObject
                ?: throw ParsingException("Malformed YouTube post comment")
        }
    }

    private fun continuationToken(renderer: JsonObject): String? {
        return endpointToken(renderer.getObject("continuationEndpoint"))
            ?: endpointToken(renderer.getObject("button").getObject("buttonRenderer").getObject("command"))
    }

    private fun endpointToken(endpoint: JsonObject): String? {
        val api = endpoint.getObject("commandMetadata").getObject("webCommandMetadata")
            .getString("apiUrl").orEmpty()
        val command = endpoint.getObject("continuationCommand")
        val request = command.getString("request").orEmpty()
        if ((api.isNotBlank() && api != "/youtubei/v1/browse") ||
            (request.isNotBlank() && request != "CONTINUATION_REQUEST_TYPE_BROWSE")
        ) {
            throw ParsingException("YouTube post comments supplied a non-browse continuation")
        }
        return command.getString("token")?.takeIf(String::isNotBlank)
    }

    private fun normalizeReplies(item: JsonObject): JsonObject {
        val thread = item.getObject("commentThreadRenderer")
        val replies = thread.getObject("replies").getObject("commentRepliesRenderer")
        if (replies.has("contents")) {
            replies.getArray("contents").filterIsInstance<JsonObject>().forEach { reply ->
                if (reply.has("continuationItemRenderer")) {
                    continuationToken(reply.getObject("continuationItemRenderer"))
                        ?: throw ParsingException("YouTube post replies continuation has no token")
                }
            }
            return item
        }
        if (!replies.has("continuations")) return item
        val continuations = replies["continuations"] as? JsonArray
            ?: throw ParsingException("Malformed YouTube post replies continuations")
        if (continuations.isEmpty()) return item
        val token = continuations.asSequence().filterIsInstance<JsonObject>()
            .mapNotNull { it.getObject("nextContinuationData").getString("continuation") }
            .firstOrNull(String::isNotBlank)
            ?: throw ParsingException("YouTube post replies continuation has no token")
        // The shared renderer parser accepts the current contents representation. Older post
        // replies use nextContinuationData; normalize only this post response before reuse.
        val normalizedReplies = JsonObject(replies).apply {
            put(
                "contents",
                JsonArray().apply {
                    add(
                        JsonObject().apply {
                            put(
                                "continuationItemRenderer",
                                JsonObject().apply {
                                    put(
                                        "continuationEndpoint",
                                        JsonObject().apply {
                                            put("continuationCommand", JsonObject().apply { put("token", token) })
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
        }
        return JsonObject(item).apply {
            put(
                "commentThreadRenderer",
                JsonObject(thread).apply {
                    put(
                        "replies",
                        JsonObject(thread.getObject("replies")).apply {
                            put("commentRepliesRenderer", normalizedReplies)
                        }
                    )
                }
            )
        }
    }
}
