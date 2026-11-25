/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.youtube.extractors

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import org.schabi.newpipe.extractor.exceptions.ParsingException

/** Selects one channel list update without treating a separate chip-bar update as its body. */
object YoutubeChannelContinuationItems {
    private const val RELOAD = "reloadContinuationItemsCommand"
    private const val APPEND = "appendContinuationItemsAction"
    private val responseArrays = listOf("onResponseReceivedActions", "onResponseReceivedEndpoints", "onResponseReceivedCommands")
    private val itemRenderers = setOf(
        "gridVideoRenderer", "videoRenderer", "gridPlaylistRenderer", "playlistRenderer",
        "radioRenderer", "gridChannelRenderer", "channelRenderer", "backstagePostRenderer",
        "postRenderer", "continuationItemRenderer"
    )
    private val lockupTypes = setOf("LOCKUP_CONTENT_TYPE_VIDEO", "LOCKUP_CONTENT_TYPE_EPISODE", "LOCKUP_CONTENT_TYPE_PLAYLIST", "LOCKUP_CONTENT_TYPE_PODCAST")

    data class Selection(val items: JsonArray, val targetId: String?)

    private data class Command(val name: String, val targetId: String?, val items: JsonArray, val header: Boolean)

    @JvmStatic
    fun targetId(tab: JsonObject): String? {
        val content = tab.getObject("content")
        val sectionList = content.getObject("sectionListRenderer")
        val grid = sectionList.getArray("contents").getObject(0).getObject("itemSectionRenderer")
            .getArray("contents").getObject(0).getObject("gridRenderer")
        return listOf(content.getObject("richGridRenderer"), grid, sectionList)
            .firstNotNullOfOrNull { it.getString("targetId")?.takeIf(String::isNotBlank) }
    }

    @JvmStatic
    @Throws(ParsingException::class)
    fun select(response: JsonObject, preferredCommand: String, expectedTargetId: String?): Selection {
        val commands = mutableListOf<Command>()
        responseArrays.forEach { collect(response[it], commands) }
        val matchingCommands = commands.filter { command ->
            expectedTargetId == null || command.targetId == null || command.targetId == expectedTargetId
        }
        val candidates = matchingCommands.filter { !it.header && it.items.any(::hasListItem) }.ifEmpty {
            // A nonempty message/header update can legitimately finish a list. Preserve that
            // empty result only after checking both command types for actual content.
            matchingCommands.filter { it.items.isNotEmpty() }
        }
        if (candidates.mapNotNull { it.targetId }.distinct().size > 1) {
            throw ParsingException("YouTube channel continuation contains multiple list targets")
        }
        // A response can include both forms of the same update. Keep the requested body once;
        // only fall back when that form contains headers rather than cards or a next-page token.
        val selected = candidates.filter { it.name == preferredCommand }.ifEmpty {
            candidates.filter { it.name != preferredCommand }
        }
        if (selected.isEmpty()) throw ParsingException("Could not get YouTube channel tab continuation items")
        val items = JsonArray()
        selected.forEach { items.addAll(it.items) }
        return Selection(items, expectedTargetId ?: selected.firstNotNullOfOrNull { it.targetId })
    }

    private fun collect(value: Any?, commands: MutableList<Command>) {
        when (value) {
            is JsonArray -> value.forEach { collect(it, commands) }

            is JsonObject -> {
                for (name in listOf(RELOAD, APPEND)) {
                    val command = value[name] as? JsonObject ?: continue
                    commands += Command(
                        name,
                        command.getString("targetId")?.takeIf(String::isNotBlank),
                        command.getArray("continuationItems"),
                        command.getString("slot") == "RELOAD_CONTINUATION_SLOT_HEADER"
                    )
                }
                // These are executable response commands. Do not visit commands embedded in
                // returned cards or chips: their endpoints belong to later user interactions.
                collect(value.getObject("commandExecutorCommand")["commands"], commands)
            }
        }
    }

    private fun hasListItem(value: Any?): Boolean {
        val item = value as? JsonObject ?: return false
        if (item.isEmpty()) return false
        if (itemRenderers.any(item::has)) return true
        if (item.getObject("lockupViewModel").getString("contentType") in lockupTypes) return true
        if (item.getObject("backstagePostThreadRenderer").getObject("post")
                .getObject("backstagePostRenderer").isNotEmpty()
        ) {
            return true
        }
        val richContent = item.getObject("richItemRenderer").getObject("content")
        if (richContent.has("shortsLockupViewModel") || richContent.has("reelItemRenderer")) return true
        if (hasListItem(richContent) ||
            hasListItem(item.getObject("shelfRenderer")["content"])
        ) {
            return true
        }
        return item.getObject("itemSectionRenderer").getArray("contents").any(::hasListItem) ||
            item.getObject("horizontalListRenderer").getArray("items").any(::hasListItem) ||
            item.getObject("expandedShelfContentsRenderer").getArray("items").any(::hasListItem)
    }
}
