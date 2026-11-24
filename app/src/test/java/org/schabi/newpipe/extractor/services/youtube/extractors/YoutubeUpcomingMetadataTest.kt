/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.youtube.extractors

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector
import org.schabi.newpipe.extractor.stream.StreamType

class YoutubeUpcomingMetadataTest {
    @Test
    fun scheduledRendererRemainsUpcomingAfterItsStartTimeAndWithoutAParseableDate() {
        for (event in listOf("""{"startTime":"1700000000"}""", "{}", """{"startTime":"unavailable"}""")) {
            val video = renderer().apply { put("upcomingEventData", json(event)) }
            val collector = StreamInfoItemsCollector(0)
            collector.commit(YoutubeStreamInfoItemExtractor(video, null))
            val item = collector.items.single()
            assertEquals(true, item.upcomingStatus)
            if (event.contains("1700000000")) {
                assertEquals(1_700_000_000L, item.uploadDate!!.offsetDateTime().toEpochSecond())
            } else {
                assertNull(item.uploadDate)
                assertTrue(collector.errors.isNotEmpty())
            }
        }
    }

    @Test
    fun structuredUpcomingOverlayDoesNotRequireATimestampOrAnEnglishLabel() {
        val video = renderer().apply {
            put("thumbnailOverlays", json("""{"items":[{"thumbnailOverlayTimeStatusRenderer":{"style":"UPCOMING","text":{"simpleText":"provider-localized label"}}}]}""").getArray("items"))
        }
        assertEquals(true, YoutubeStreamInfoItemExtractor(video, null).upcomingStatus)
        val modern = lockup("thumbnailOverlayBadgeViewModel", "thumbnailBadges", """{"badgeStyle":"THUMBNAIL_OVERLAY_BADGE_STYLE_UPCOMING","text":"provider-localized label"}""")
        assertEquals(true, YoutubeLockupStreamInfoItemExtractor(modern, null).upcomingStatus)
    }

    @Test
    fun bothLockupBadgeShapesReachCollectedItemsThroughBothExtractorRoutes() {
        for (overlay in listOf("thumbnailBottomOverlayViewModel" to "badges", "thumbnailOverlayBadgeViewModel" to "thumbnailBadges")) {
            val video = lockup(overlay.first, overlay.second, """{"badgeStyle":"THUMBNAIL_OVERLAY_BADGE_STYLE_DEFAULT","text":"Upcoming"}""")
            for (extractor in listOf<StreamInfoItemExtractor>(YoutubeLockupStreamInfoItemExtractor(video, null), YoutubeStreamInfoItemExtractor(video, null))) {
                val collector = StreamInfoItemsCollector(0)
                collector.commit(extractor)
                val item = collector.items.single()
                assertEquals(true, item.upcomingStatus)
                assertNull(item.uploadDate)
            }
        }
    }

    @Test
    fun liveAndDurationMetadataExplicitlyClearScheduledStatus() {
        val live = lockup("thumbnailOverlayBadgeViewModel", "thumbnailBadges", """{"badgeStyle":"THUMBNAIL_OVERLAY_BADGE_STYLE_LIVE","text":"provider-localized label"}""")
        val recorded = lockup("thumbnailBottomOverlayViewModel", "badges", """{"text":"1:02:03"}""")
        assertEquals(false, YoutubeLockupStreamInfoItemExtractor(live, null).upcomingStatus)
        assertEquals(false, YoutubeLockupStreamInfoItemExtractor(recorded, null).upcomingStatus)

        val video = renderer().apply { put("lengthText", json("""{"simpleText":"12:34"}""")) }
        assertEquals(false, YoutubeStreamInfoItemExtractor(video, null).upcomingStatus)
        assertEquals(false, YoutubeUpcomingMetadata.status(JsonObject(), StreamType.POST_LIVE_STREAM))
    }

    @Test
    fun sparseCardsAndRssRemainUnknownAndTitlesAreNotStatusMetadata() {
        val video = renderer().apply { put("title", json("""{"simpleText":"Upcoming films and live concerts"}""")) }
        assertNull(YoutubeStreamInfoItemExtractor(video, null).upcomingStatus)
        assertNull(YoutubeUpcomingMetadata.status(JsonObject(), StreamType.VIDEO_STREAM))
        val entry = Jsoup.parse("<entry><title>Upcoming broadcast</title><published>2026-01-01T12:00:00Z</published></entry>", "", Parser.xmlParser()).getElementsByTag("entry").first()!!
        val rss = YoutubeFeedInfoItemExtractor(entry)
        assertNull(rss.upcomingStatus)
        assertEquals(StreamType.VIDEO_STREAM, rss.streamType)
    }

    // Constructed, minimal provider-shaped fixtures. No network or clock-dependent responses.
    // Upstream captured premiere uses Upcoming + DEFAULT style:
    // TeamNewPipe/NewPipeExtractor ebc0b8c, youtubestreaminfoitem/lockupviewmodelpremiere.json.
    private fun renderer() = json(
        """{"videoId":"abcdefghijk","title":{"simpleText":"Scheduled broadcast"},"shortBylineText":{"runs":[{"text":"Channel","navigationEndpoint":{"browseEndpoint":{"browseId":"UCabcdefghijklmnopqrstuv"}}}]},"thumbnail":{"thumbnails":[{"url":"https://example.com/thumbnail.jpg"}]}}"""
    )

    private fun lockup(overlay: String, badges: String, badge: String) = json(
        """{"contentId":"abcdefghijk","contentImage":{"thumbnailViewModel":{"image":{"sources":[{"url":"https://example.com/thumbnail.jpg"}]},"overlays":[{"$overlay":{"$badges":[{"thumbnailBadgeViewModel":$badge}]}}]}},"metadata":{"lockupMetadataViewModel":{"title":{"content":"Scheduled broadcast"},"metadata":{"contentMetadataViewModel":{"metadataRows":[{"metadataParts":[{"text":{"content":"Channel","commandRuns":[{"onTap":{"innertubeCommand":{"browseEndpoint":{"browseId":"UCabcdefghijklmnopqrstuv"}}}}]}}]}]}}}}}"""
    )

    private fun json(value: String): JsonObject = JsonParser.`object`().from(value)
}
