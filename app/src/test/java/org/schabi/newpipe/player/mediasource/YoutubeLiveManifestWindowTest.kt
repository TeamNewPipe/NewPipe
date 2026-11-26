/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.schabi.newpipe.player.mediasource

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.exoplayer.dash.DashSegmentIndex
import androidx.media3.exoplayer.dash.manifest.DashManifest
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.schabi.newpipe.player.helper.YoutubeDashLiveManifestParser

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class YoutubeLiveManifestWindowTest {
    @Test
    fun implicitUnboundedSegmentsAdvanceTheDvrWindowWithoutMpdRefreshes() {
        val manifest = parse("""<SegmentTemplate duration="2" media="segment-${'$'}Number${'$'}.m4s"/>""")
        val index = requireNotNull(manifest.getPeriod(0).adaptationSets.single().representations.single().index)
        val periodDurationUs = manifest.getPeriodDurationUs(0)
        assertEquals(C.TIME_UNSET, manifest.minUpdatePeriodMs)
        assertEquals(C.TIME_UNSET, periodDurationUs)
        assertFalse(index.isExplicit)
        assertEquals(DashSegmentIndex.INDEX_UNBOUNDED.toLong(), index.getSegmentCount(periodDurationUs))

        val first = index.getFirstAvailableSegmentNum(periodDurationUs, 20_000_000)
        val count = index.getAvailableSegmentCount(periodDurationUs, 20_000_000)
        val laterFirst = index.getFirstAvailableSegmentNum(periodDurationUs, 24_000_000)
        val laterCount = index.getAvailableSegmentCount(periodDurationUs, 24_000_000)
        assertTrue(count > 1)
        assertTrue(laterCount > 1)
        assertTrue(laterFirst > first)
        assertTrue(laterFirst + laterCount > first + count)
        assertTrue(YoutubeLiveMediaSource.isRefreshable(manifest))
    }

    @Test
    fun finiteExplicitSnapshotWithoutRefreshesStillRequiresFallback() {
        val manifest = parse(
            """
            <SegmentTemplate media="segment-${'$'}Number${'$'}.m4s">
              <SegmentTimeline><S t="0" d="2" r="7"/></SegmentTimeline>
            </SegmentTemplate>
        """
        )
        val index = requireNotNull(manifest.getPeriod(0).adaptationSets.single().representations.single().index)
        assertTrue(index.isExplicit)
        assertEquals(8L, index.getSegmentCount(C.TIME_UNSET))
        assertFalse(YoutubeLiveMediaSource.isRefreshable(manifest))
    }

    @Test
    fun templateWithAFinalSegmentNumberCannotMasqueradeAsAnUnboundedLiveIndex() {
        val manifest = parse("""<SegmentTemplate duration="2" media="segment-${'$'}Number${'$'}.m4s"/>""", lastSegmentNumber = 8)
        val index = requireNotNull(manifest.getPeriod(0).adaptationSets.single().representations.single().index)
        assertFalse(index.isExplicit)
        assertEquals(8L, index.getSegmentCount(C.TIME_UNSET))
        assertFalse(YoutubeLiveMediaSource.isRefreshable(manifest))
    }

    @Test
    fun aFiniteRepresentationCannotBeHiddenByAnImplicitAlternative() {
        val implicit = """<SegmentTemplate duration="2" media="segment-${'$'}Number${'$'}.m4s"/>"""
        val finite = """
            <SegmentTemplate media="other-${'$'}Number${'$'}.m4s">
              <SegmentTimeline><S t="0" d="2" r="7"/></SegmentTimeline>
            </SegmentTemplate>
        """
        val manifest = parse(implicit, alternative = finite)
        assertFalse(YoutubeLiveMediaSource.isRefreshable(manifest))
    }

    @Test
    fun staticTextIndexPreventsMedia3FromSchedulingImplicitWindowUpdates() {
        val manifest = parse(
            """<SegmentTemplate duration="2" media="segment-${'$'}Number${'$'}.m4s"/>""",
            extraAdaptation = """
                <AdaptationSet contentType="text" mimeType="text/vtt">
                  <Representation id="captions" bandwidth="256"><BaseURL>captions.vtt</BaseURL></Representation>
                </AdaptationSet>
            """
        )
        val textIndex = manifest.getPeriod(0).adaptationSets[1].representations.single().index
        assertTrue(textIndex == null || textIndex.isExplicit)
        assertFalse(YoutubeLiveMediaSource.isRefreshable(manifest))
    }

    @Test
    fun explicitlyRefreshedSnapshotRetainsTheExistingAcceptanceRule() {
        val manifest = parse(
            """
            <SegmentTemplate media="segment-${'$'}Number${'$'}.m4s">
              <SegmentTimeline><S t="0" d="2" r="7"/></SegmentTimeline>
            </SegmentTemplate>
        """,
            refresh = "minimumUpdatePeriod=\"PT1S\""
        )
        assertEquals(1_000L, manifest.minUpdatePeriodMs)
        assertTrue(YoutubeLiveMediaSource.isRefreshable(manifest))
    }

    private fun parse(
        segments: String,
        refresh: String = "",
        alternative: String? = null,
        lastSegmentNumber: Int? = null,
        extraAdaptation: String = ""
    ): DashManifest {
        fun representation(id: Int, body: String) = """
            <Representation id="$id" mimeType="video/mp4" codecs="avc1.42c00a"
              bandwidth="2000" width="64" height="64">$body</Representation>
        """
        val xml = """
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="dynamic"
              availabilityStartTime="1970-01-01T00:00:00Z" timeShiftBufferDepth="PT12S"
              minBufferTime="PT2S" $refresh>
              <Period id="0" start="PT0S"><AdaptationSet contentType="video">
                ${lastSegmentNumber?.let { """<SupplementalProperty schemeIdUri="http://dashif.org/guidelines/last-segment-number" value="$it"/>""" }.orEmpty()}
                ${representation(0, segments)}
                ${alternative?.let { representation(1, it) }.orEmpty()}
              </AdaptationSet>$extraAdaptation</Period>
            </MPD>
        """.trimIndent()
        return YoutubeDashLiveManifestParser().parse(
            Uri.parse("https://example.test/live.mpd"),
            ByteArrayInputStream(xml.toByteArray())
        )
    }
}
