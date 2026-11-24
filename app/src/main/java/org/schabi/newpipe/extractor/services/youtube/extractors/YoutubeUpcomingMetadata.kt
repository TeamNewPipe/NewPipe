/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.youtube.extractors

import com.grack.nanojson.JsonObject
import org.schabi.newpipe.extractor.stream.StreamType

/** Reads provider status/duration metadata, never video titles or a guessed start-time cutoff. */
object YoutubeUpcomingMetadata {
    private val durationText = Regex("\\d+(?::[0-5]\\d){1,3}")
    private val liveBadgeStyles = setOf(
        "THUMBNAIL_OVERLAY_BADGE_STYLE_LIVE",
        "BADGE_STYLE_TYPE_LIVE_NOW",
        "BADGE_STYLE_TYPE_LIVE"
    )
    private val startedStreamTypes = setOf(
        StreamType.LIVE_STREAM,
        StreamType.AUDIO_LIVE_STREAM,
        StreamType.POST_LIVE_STREAM,
        StreamType.POST_LIVE_AUDIO_STREAM
    )

    @JvmStatic
    fun status(video: JsonObject, streamType: StreamType): Boolean? {
        if (video["upcomingEventData"] is JsonObject) return true

        val timeOverlays = video.getArray("thumbnailOverlays").filterIsInstance<JsonObject>()
            .map { it.getObject("thumbnailOverlayTimeStatusRenderer") }
        if (timeOverlays.any { it.getString("style", "").equals("UPCOMING", ignoreCase = true) }) return true

        val badges = video.getObject("contentImage").getObject("thumbnailViewModel")
            .getArray("overlays").filterIsInstance<JsonObject>().flatMap { overlay ->
                overlay.getObject("thumbnailBottomOverlayViewModel").getArray("badges") +
                    overlay.getObject("thumbnailOverlayBadgeViewModel").getArray("thumbnailBadges")
            }.filterIsInstance<JsonObject>().map { it.getObject("thumbnailBadgeViewModel") }
        // Some lockup cards use DEFAULT style with a provider Upcoming label; others have a
        // dedicated status style that also works when the visible badge text is localized.
        if (badges.any {
                it.getString("badgeStyle", "") == "THUMBNAIL_OVERLAY_BADGE_STYLE_UPCOMING" ||
                    it.getString("text", "").trim().equals("Upcoming", ignoreCase = true)
            }
        ) {
            return true
        }

        if (streamType in startedStreamTypes ||
            timeOverlays.any { it.getString("style", "").equals("LIVE", ignoreCase = true) } ||
            badges.any { it.getString("badgeStyle", "") in liveBadgeStyles }
        ) {
            return false
        }

        val durations = listOf(text(video.getObject("lengthText"))) +
            timeOverlays.map { text(it.getObject("text")) } + badges.map { it.getString("text", "") }
        if (durations.any { durationText.matches(it.trim()) }) return false

        // Sparse endpoints, including RSS, must not erase an earlier explicit provider state.
        return null
    }

    private fun text(value: JsonObject): String = value.getString("simpleText")
        ?: value.getArray("runs").filterIsInstance<JsonObject>().joinToString("") { it.getString("text", "") }
}
