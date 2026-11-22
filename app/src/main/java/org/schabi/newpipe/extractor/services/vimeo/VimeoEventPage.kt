/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException

internal data class VimeoEventPlayback(val target: VimeoVideoTarget, val config: JsonObject, val scheduledStart: String?)

/** Follows the public event embed's selected player without using the authenticated Events API. */
internal object VimeoEventPage {
    fun fetch(target: VimeoEventTarget, downloader: Downloader): VimeoEventPlayback {
        val document = VimeoApi.getDocument(target.embedUrl, downloader)
        val redirected = VimeoEventTarget.fromUrl(document.location())
        if (redirected == null || redirected.eventId != target.eventId || redirected.unlistedHash != target.unlistedHash) {
            throw ParsingException("Vimeo redirected to another event")
        }
        val entity = VimeoPlayerConfig.assignment(document, "entityData")
            ?: throw ParsingException("Vimeo embed has no event identity")
        if (entity.getString("entity_type", "") != "event" || entity["entity_id"].toString() != target.eventId ||
            entity["event_id"] != null && entity["event_id"].toString() != target.eventId
        ) {
            throw ParsingException("Vimeo embed belongs to another event")
        }
        val data = VimeoPlayerConfig.assignment(document, "dataForPlayer")
            ?: throw ParsingException("Vimeo embed has no public player metadata")
        val playlist = data["playlist_data"] as? JsonObject
            ?: throw ParsingException("Vimeo embed has no event metadata")
        if (playlist["id"].toString() != target.eventId || playlist.getString("type", "") != "RLE") {
            throw ParsingException("Vimeo player metadata belongs to another event")
        }
        val error = data.getString("error", "").orEmpty()
        if (error.isNotEmpty()) {
            if (error in setOf("not_accessible", "not_embeddable", "password", "password_required", "private")) {
                throw PrivateContentException("Vimeo restricts access to this event ($error)")
            }
            throw ContentNotAvailableException("This Vimeo event is unavailable ($error)")
        }
        if (playlist.getBoolean("sso", false)) throw PrivateContentException("This Vimeo event requires an organization sign-in")
        if (entity.getBoolean("has_registration_enabled", false)) {
            throw ContentNotSupportedException("Registration for Vimeo events is not supported")
        }
        val hash = playlist.getString("unlisted_hash", "").orEmpty()
        if (hash.isNotEmpty() && hash != target.unlistedHash) throw PrivateContentException("This Vimeo event requires its complete shared URL")

        val players = document.select(".player[data-clip-id][data-config-url]")
        if (target.videoId == null && players.size != 1) {
            throw ContentNotAvailableException("The Vimeo event did not advertise a selected player")
        }
        val videoId = target.videoId ?: players.single().attr("data-clip-id")
        val clips = data.getArray("clips")?.filterIsInstance<JsonObject>().orEmpty()
        val clip = clips.singleOrNull { it["id"].toString() == videoId }
            ?: throw ContentNotAvailableException("The requested recording is not available in this Vimeo event")
        val configUrl = if (target.videoId == null) {
            players.single().attr("data-config-url")
        } else {
            clip.getString("config_no_autoplay", "").ifBlank { clip.getString("config", "") }
        }
        val videoTarget = VimeoPlayerConfig.configTarget(configUrl, videoId)
        val response = VimeoApi.getResponse(configUrl, downloader)
        val finalTarget = VimeoPlayerConfig.configTarget(response.latestUrl().takeIf { it.isNotBlank() } ?: configUrl, videoId)
        if (finalTarget.unlistedHash != videoTarget.unlistedHash) {
            throw ParsingException("Vimeo redirected to another recording identity")
        }
        val config = try {
            JsonParser.`object`().from(response.responseBody())
        } catch (error: Exception) {
            throw ParsingException("Vimeo event returned invalid player configuration", error)
        }
        val live = data["live_event"] as? JsonObject
        val schedule = live?.get("schedule") as? JsonObject
        val scheduledStart = if (live?.getBoolean("show_start_time", false) == true || playlist.getBoolean("show_schedule", false)) {
            schedule?.getString("start_time")?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        return VimeoEventPlayback(videoTarget, config, scheduledStart)
    }
}
