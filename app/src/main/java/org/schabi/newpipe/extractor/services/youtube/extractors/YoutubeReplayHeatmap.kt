/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.youtube.extractors

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import java.util.Collections
import org.schabi.newpipe.extractor.stream.ReplayHeatmapSample

/** Reads optional replay data from the already fetched watch/next response. Never fetches a video. */
object YoutubeReplayHeatmap {
    private const val MAX_MARKER_LISTS = 512
    private const val MAX_SAMPLES = 2048

    @JvmStatic
    fun parse(response: JsonObject?, videoId: String, durationSeconds: Long): List<ReplayHeatmapSample> {
        if (response == null || videoId.isBlank() || durationSeconds <= 0 || durationSeconds > Long.MAX_VALUE / 1000) return emptyList()
        val durationMs = durationSeconds * 1000
        val mutations = response.objectAt("frameworkUpdates", "entityBatchUpdate")?.get("mutations") as? JsonArray
        if (mutations != null) {
            for (index in 0 until minOf(mutations.size, MAX_MARKER_LISTS)) {
                val entity = (mutations[index] as? JsonObject)?.objectAt("payload", "macroMarkersListEntity") ?: continue
                val markerVideoId = entity["externalVideoId"]
                if (markerVideoId != null && markerVideoId != videoId) continue
                val list = entity["markersList"] as? JsonObject ?: continue
                if (list["markerType"] != "MARKER_TYPE_HEATMAP") continue
                val samples = readMarkers(list["markers"] as? JsonArray, durationMs, legacy = false)
                if (samples.isNotEmpty()) return samples
            }
        }

        // Older watch responses put the same information in the player's marker overlay.
        val markerMaps = response.objectAt(
            "playerOverlays",
            "playerOverlayRenderer",
            "decoratedPlayerBarRenderer",
            "decoratedPlayerBarRenderer",
            "playerBar",
            "multiMarkersPlayerBarRenderer"
        )?.get("markersMap") as? JsonArray ?: return emptyList()
        for (index in 0 until minOf(markerMaps.size, MAX_MARKER_LISTS)) {
            val renderer = (markerMaps[index] as? JsonObject)?.objectAt("value", "heatmap", "heatmapRenderer") ?: continue
            val samples = readMarkers(renderer["heatMarkers"] as? JsonArray, durationMs, legacy = true)
            if (samples.isNotEmpty()) return samples
        }
        return emptyList()
    }

    private fun readMarkers(markers: JsonArray?, durationMs: Long, legacy: Boolean): List<ReplayHeatmapSample> {
        // A partial graph would misrepresent the remaining timeline. Omit oversized payloads.
        if (markers == null || markers.isEmpty() || markers.size > MAX_SAMPLES) return emptyList()
        val samples = ArrayList<ReplayHeatmapSample>(markers.size)
        for (item in markers) {
            val marker = item as? JsonObject ?: continue
            val fields = if (legacy) {
                marker["heatMarkerRenderer"] as? JsonObject ?: continue
            } else {
                marker
            }
            val start = millis(fields[if (legacy) "timeRangeStartMillis" else "startMillis"]) ?: continue
            val duration = millis(fields[if (legacy) "markerDurationMillis" else "durationMillis"]) ?: continue
            val intensity = intensity(fields[if (legacy) "heatMarkerIntensityScoreNormalized" else "intensityScoreNormalized"]) ?: continue
            if (start < 0 || start >= durationMs || duration <= 0) continue
            // Clamp before addition so corrupt durations cannot overflow a timestamp.
            val end = start + minOf(duration, durationMs - start)
            samples += ReplayHeatmapSample(start, end, intensity)
        }
        samples.sortWith(compareBy<ReplayHeatmapSample> { it.startTimeMs }.thenBy { it.endTimeMs }.thenByDescending { it.intensity })
        val ordered = ArrayList<ReplayHeatmapSample>(samples.size)
        for (sample in samples) {
            // Repeated or overlapping bins are ambiguous; retain one ordered, non-overlapping set.
            if (ordered.isEmpty() || sample.startTimeMs >= ordered.last().endTimeMs) ordered += sample
        }
        return Collections.unmodifiableList(ordered)
    }

    private fun millis(value: Any?): Long? = when (value) {
        is Number, is String -> value.toString().takeIf { it.length <= 20 }?.toLongOrNull()
        else -> null
    }

    private fun intensity(value: Any?): Float? {
        if (value !is Number && value !is String) return null
        val number = value.toString().takeIf { it.length <= 64 }?.toDoubleOrNull() ?: return null
        return number.takeIf { it.isFinite() && it in 0.0..1.0 }?.toFloat()
    }

    private fun JsonObject.objectAt(vararg path: String): JsonObject? {
        var current = this
        for (key in path) current = current[key] as? JsonObject ?: return null
        return current
    }
}
