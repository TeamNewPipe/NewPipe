/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.youtube.extractors

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.stream.ReplayHeatmapSample

class YoutubeReplayHeatmapTest {
    @Test
    fun capturedDesktopNextResponseProvidesTheCompleteMillisecondTimeline() {
        val response = requireNotNull(javaClass.getResourceAsStream("/youtube/replay-heatmap/big-buck-bunny-next.json")).use {
            JsonParser.`object`().from(it.bufferedReader())
        }
        val samples = YoutubeReplayHeatmap.parse(response, VIDEO_ID, 597)

        assertEquals(100, samples.size)
        assertEquals(ReplayHeatmapSample(0, 5970, 1f), samples.first())
        assertEquals(ReplayHeatmapSample(591030, 597000, 0.50236124f), samples.last())
        assertTrue(samples.zipWithNext().all { (left, right) -> left.endTimeMs == right.startTimeMs })
        assertTrue(samples.all { it.intensity in 0f..1f })
    }

    @Test
    fun malformedOrMissingOptionalDataLeavesPlaybackWithoutAHeatmap() {
        listOf(
            null,
            json("{}"),
            json("""{"frameworkUpdates":false}"""),
            json("""{"frameworkUpdates":{"entityBatchUpdate":{"mutations":"wrong type"}}}"""),
            current("null", "true", "[]", "{}"),
            json("""{"playerOverlays":{"playerOverlayRenderer":[]}}""")
        ).forEach { response ->
            assertTrue(YoutubeReplayHeatmap.parse(response, VIDEO_ID, 60).isEmpty())
        }
    }

    @Test
    fun unrelatedEntityListsAndOtherVideoHeatmapsAreIgnored() {
        val response = current(marker("0", "1000", "0.4"))
        val mutations = mutations(response)
        val otherVideo = current(marker("0", "1000", "1"), videoId = "other-video")
        val unrelated = current(marker("0", "1000", "0.8"))
        entity(unrelated).getObject("markersList")["markerType"] = "MARKER_TYPE_CHAPTERS"
        mutations.add(0, mutations(otherVideo)[0])
        mutations.add(0, mutations(unrelated)[0])

        assertEquals(listOf(ReplayHeatmapSample(0, 1000, 0.4f)), YoutubeReplayHeatmap.parse(response, VIDEO_ID, 60))
        assertTrue(YoutubeReplayHeatmap.parse(otherVideo, VIDEO_ID, 60).isEmpty())
    }

    @Test
    fun numericAndStringTimesAreAcceptedWithoutLosingMilliseconds() {
        val response = current(marker("1234", "500", "0.25"), marker("\"1734\"", "\"750\"", "\"0.75\""))

        assertEquals(
            listOf(ReplayHeatmapSample(1234, 1734, 0.25f), ReplayHeatmapSample(1734, 2484, 0.75f)),
            YoutubeReplayHeatmap.parse(response, VIDEO_ID, 3)
        )
    }

    @Test
    fun invalidSamplesDoNotHideLaterValidSamples() {
        val response = current(
            marker("-1", "1000", "0.5"),
            marker("0.5", "1000", "0.5"),
            marker("\"1.5\"", "1000", "0.5"),
            marker("0", "0", "0.5"),
            marker("0", "-1000", "0.5"),
            marker("0", "1000.5", "0.5"),
            marker("0", "null", "0.5"),
            marker("0", "1000", "-0.01"),
            marker("0", "1000", "1.01"),
            marker("0", "1000", "\"NaN\""),
            marker("0", "1000", "\"Infinity\""),
            marker("0", "1000", "true"),
            marker("0", "1000", "{}"),
            marker("60000", "1000", "0.5"),
            marker("\"9223372036854775808\"", "1000", "0.5"),
            marker("2500", "1000", "0.7")
        )

        assertEquals(listOf(ReplayHeatmapSample(2500, 3500, 0.7f)), YoutubeReplayHeatmap.parse(response, VIDEO_ID, 60))
    }

    @Test
    fun sortingRemovesDuplicateAndOverlappingBinsDeterministically() {
        val response = current(
            marker("2000", "1000", "0.3"),
            marker("0", "1000", "0.1"),
            marker("0", "1000", "0.8"),
            marker("500", "1000", "0.9"),
            marker("1000", "1000", "0.4")
        )

        assertEquals(
            listOf(ReplayHeatmapSample(0, 1000, 0.8f), ReplayHeatmapSample(1000, 2000, 0.4f), ReplayHeatmapSample(2000, 3000, 0.3f)),
            YoutubeReplayHeatmap.parse(response, VIDEO_ID, 3)
        )
    }

    @Test
    fun overlongFinalBinIsClippedBeforeTimestampAdditionCanOverflow() {
        val response = current(marker("5500", "\"9223372036854775807\"", "0.5"))
        assertEquals(listOf(ReplayHeatmapSample(5500, 6000, 0.5f)), YoutubeReplayHeatmap.parse(response, VIDEO_ID, 6))
        listOf(-1L, 0L, Long.MAX_VALUE).forEach { duration ->
            assertTrue(YoutubeReplayHeatmap.parse(response, VIDEO_ID, duration).isEmpty())
        }
        assertTrue(YoutubeReplayHeatmap.parse(response, "", 6).isEmpty())
    }

    @Test
    fun oversizedPayloadIsOmittedInsteadOfShowingATruncatedGraph() {
        val response = current(*Array(5000) { marker((it * 1000).toString(), "1000", "0.5") })
        assertTrue(YoutubeReplayHeatmap.parse(response, VIDEO_ID, 6000).isEmpty())
    }

    @Test
    fun legacyOverlayStillWorksAndCurrentEntityDataTakesPrecedence() {
        val legacy = json(
            """{"playerOverlays":{"playerOverlayRenderer":{"decoratedPlayerBarRenderer":{"decoratedPlayerBarRenderer":{
                "playerBar":{"multiMarkersPlayerBarRenderer":{"markersMap":[{"value":{"heatmap":{"heatmapRenderer":{"heatMarkers":[
                    {"heatMarkerRenderer":{"timeRangeStartMillis":1000,"markerDurationMillis":2000,"heatMarkerIntensityScoreNormalized":0.9}}
                ]}}}}]}}
            }}}}}"""
        )
        assertEquals(listOf(ReplayHeatmapSample(1000, 3000, 0.9f)), YoutubeReplayHeatmap.parse(legacy, VIDEO_ID, 60))

        val current = current(marker("0", "1000", "0.2"))
        current["playerOverlays"] = legacy["playerOverlays"]
        assertEquals(listOf(ReplayHeatmapSample(0, 1000, 0.2f)), YoutubeReplayHeatmap.parse(current, VIDEO_ID, 60))
    }

    private fun current(vararg markers: String, videoId: String = VIDEO_ID): JsonObject = json(
        """{"frameworkUpdates":{"entityBatchUpdate":{"mutations":[{"payload":{"macroMarkersListEntity":{
            "externalVideoId":"$videoId","markersList":{"markerType":"MARKER_TYPE_HEATMAP","markers":[${markers.joinToString(",")}]}
        }}}]}}}"""
    )

    private fun marker(start: String, duration: String, intensity: String): String = """{"startMillis":$start,"durationMillis":$duration,"intensityScoreNormalized":$intensity}"""

    private fun mutations(response: JsonObject): JsonArray = response.getObject("frameworkUpdates").getObject("entityBatchUpdate").getArray("mutations")

    private fun entity(response: JsonObject): JsonObject = mutations(response).getObject(0).getObject("payload").getObject("macroMarkersListEntity")

    private fun json(value: String): JsonObject = JsonParser.`object`().from(value)

    companion object {
        private const val VIDEO_ID = "YE7VzlLtp-4"
    }
}
