/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonWriter
import java.time.OffsetDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType

class VimeoEventExtractorTest {
    private val previousDownloader = NewPipe.getDownloader()
    private val previousLocalization = NewPipe.getPreferredLocalization()
    private val previousCountry = NewPipe.getPreferredContentCountry()
    private val responses = mutableMapOf<String, FixtureResponse>()
    private val requests = mutableListOf<String>()

    @Before
    fun setup() {
        for ((event, name) in listOf("4768062" to "live", "5116195" to "pending", "4753126" to "archive", "5034253" to "restricted", "5120811" to "not-embeddable")) {
            responses[embed(event)] = FixtureResponse(fixture("$name-event.html"))
        }
        for ((video, name) in listOf("1240101097" to "live", "1085320934" to "pending", "1082194134" to "archive-recording", "1247036786" to "selected-archive")) {
            responses[configUrl(video)] = FixtureResponse(fixture("$name-config.json"))
        }
        NewPipe.init(object : Downloader() {
            override fun execute(request: Request): Response {
                assertEquals("GET", request.httpMethod())
                assertFalse(request.headers().keys.any { it.equals("Authorization", true) || it.equals("Cookie", true) || it.equals("Referer", true) })
                requests += request.url()
                val response = responses[request.url()] ?: throw AssertionError("Unexpected event request: ${request.url()}")
                return Response(response.status, "fixture", emptyMap(), response.body, response.body.toByteArray(Charsets.UTF_8), response.finalUrl ?: request.url())
            }

            override fun executeAsync(request: Request, callback: AsyncCallback) = throw AssertionError("Unexpected asynchronous event request")
        })
    }

    @After
    fun reset() {
        NewPipe.init(previousDownloader, previousLocalization, previousCountry)
    }

    @Test
    fun capturedActiveEventResolvesItsAdvertisedSessionWithoutChangingTheEventIdentity() {
        val original = "https://vimeo.com/event/4768062/embed"
        val info = StreamInfo.getInfo(ServiceList.Vimeo, original)
        assertTrue(info.errors.toString(), info.errors.isEmpty())
        assertEquals("event/4768062", info.id)
        assertEquals("https://vimeo.com/event/4768062", info.url)
        assertEquals(original, info.originalUrl)
        assertEquals("Graceland Cam", info.name)
        assertEquals(StreamType.LIVE_STREAM, info.streamType)
        assertFalse(info.isUpcoming)
        assertEquals(0L, info.duration)
        assertEquals(-1L, info.viewCount)
        assertEquals(StreamExtractor.Privacy.PUBLIC, info.privacy)
        assertTrue(info.hlsUrl.startsWith("https://live-api.vimeocdn.com/"))
        assertEquals(DeliveryMethod.HLS, info.videoStreams.single().deliveryMethod)
        assertEquals(info.hlsUrl, info.videoStreams.single().content)
        assertTrue(info.videoStreams.single().id.startsWith("1240101097/"))
        assertEquals(listOf(embed("4768062"), configUrl("1240101097")), requests)
    }

    @Test
    fun capturedPendingEventRemainsWaitingInsteadOfPlayingItsOlderRecording() {
        val info = StreamInfo.getInfo(ServiceList.Vimeo, "https://vimeo.com/event/5116195")
        assertEquals(StreamType.LIVE_STREAM, info.streamType)
        assertTrue(info.isUpcoming)
        assertEquals("Skidmore College Commencement 2025", info.name)
        assertTrue(info.videoStreams.isEmpty())
        assertEquals("", info.hlsUrl)
        assertEquals("", info.dashMpdUrl)
        // The captured event has an old, hidden schedule, not a published future start time.
        assertNull(info.uploadDate)
        assertNull(info.textualUploadDate)
        assertEquals(listOf(embed("5116195"), configUrl("1085320934")), requests)
    }

    @Test
    fun capturedEventUsesItsSelectedArchiveRatherThanTheFirstPendingClip() {
        val info = StreamInfo.getInfo(ServiceList.Vimeo, "https://vimeo.com/event/4753126")
        assertEquals("event/4753126", info.id)
        assertEquals(StreamType.VIDEO_STREAM, info.streamType)
        assertFalse(info.isUpcoming)
        assertEquals(2777L, info.duration)
        assertTrue(info.videoStreams.single().id.startsWith("1247036786/"))
        assertEquals(listOf(embed("4753126"), configUrl("1247036786")), requests)
    }

    @Test
    fun explicitArchiveLinksSelectOnlyTheRequestedPublicRecording() {
        val url = "https://vimeo.com/event/5116195/videos/1082194134"
        val info = StreamInfo.getInfo(ServiceList.Vimeo, url)
        assertEquals(url, info.url)
        assertEquals("event/5116195/videos/1082194134", info.id)
        assertEquals(StreamType.VIDEO_STREAM, info.streamType)
        assertEquals(9810L, info.duration)
        assertEquals(listOf(embed("5116195"), configUrl("1082194134")), requests)
        requests.clear()
        assertThrows(ContentNotAvailableException::class.java) {
            ServiceList.Vimeo.getStreamExtractor("https://vimeo.com/event/5116195/videos/999999").fetchPage()
        }
        assertEquals(listOf(embed("5116195")), requests)
    }

    @Test
    fun capturedAccessAndEmbedDenialsNeverTryARecordingOrAlternatePage() {
        for (event in listOf("5034253", "5120811")) {
            requests.clear()
            assertThrows(PrivateContentException::class.java) {
                ServiceList.Vimeo.getStreamExtractor("https://vimeo.com/event/$event").fetchPage()
            }
            assertEquals(listOf(embed(event)), requests)
        }
        responses[embed("4768062")] = FixtureResponse(fixture("live-event.html").replace("\"sso\": false", "\"sso\": true"))
        assertThrows(PrivateContentException::class.java) { liveExtractor().fetchPage() }
        responses[embed("4768062")] = FixtureResponse(fixture("live-event.html").replace("\"has_registration_enabled\": false", "\"has_registration_enabled\": true"))
        assertThrows(ContentNotSupportedException::class.java) { liveExtractor().fetchPage() }
        responses[embed("4768062")] = FixtureResponse(fixture("live-event.html"))
        val protectedConfig = config("live-config.json").apply {
            getObject("request").put("drm", JsonObject().apply { put("widevine", "synthetic-protection-marker") })
        }
        responses[configUrl("1240101097")] = FixtureResponse(JsonWriter.string(protectedConfig))
        requests.clear()
        assertThrows(ContentNotSupportedException::class.java) { liveExtractor().fetchPage() }
        assertEquals(listOf(embed("4768062"), configUrl("1240101097")), requests)
    }

    @Test
    fun sharedEventHashIsPreservedAndCannotBeRecoveredFromAnAnonymousErrorPage() {
        val hash = "abcdef0123"
        val body = fixture("live-event.html").replace("\"unlisted_hash\": null", "\"unlisted_hash\": \"$hash\"")
        responses["${embed("4768062")}/$hash"] = FixtureResponse(body)
        val info = StreamInfo.getInfo(ServiceList.Vimeo, "https://vimeo.com/event/4768062/$hash")
        assertEquals("event/4768062/$hash", info.id)
        assertEquals(StreamExtractor.Privacy.UNLISTED, info.privacy)
        assertEquals("${embed("4768062")}/$hash", requests.first())
        requests.clear()
        responses[embed("4768062")] = FixtureResponse(body)
        assertThrows(PrivateContentException::class.java) { liveExtractor().fetchPage() }
        assertEquals(listOf(embed("4768062")), requests)
    }

    @Test
    fun embedIdentitySelectedClipAndConfigOriginCannotBeSubstituted() {
        for (response in listOf(
            FixtureResponse(fixture("live-event.html").replace("\"entity_id\": \"4768062\"", "\"entity_id\": \"123\"")),
            FixtureResponse(fixture("live-event.html").replace("player.vimeo.com", "example.org")),
            FixtureResponse(fixture("live-event.html").replace("data-clip-id=\"1240101097\"", "data-clip-id=\"1116119000\"")),
            FixtureResponse(fixture("live-event.html"), finalUrl = embed("5116195"))
        )) {
            responses[embed("4768062")] = response
            requests.clear()
            assertThrows(ParsingException::class.java) { liveExtractor().fetchPage() }
            assertEquals(listOf(embed("4768062")), requests)
        }
        responses[embed("4768062")] = FixtureResponse(fixture("live-event.html"))
        responses[configUrl("1240101097")] = FixtureResponse(fixture("selected-archive-config.json"))
        assertThrows(ParsingException::class.java) { liveExtractor().fetchPage() }
        responses[configUrl("1240101097")] = FixtureResponse("Restricted", 403)
        requests.clear()
        assertThrows(PrivateContentException::class.java) { liveExtractor().fetchPage() }
        assertEquals(listOf(embed("4768062"), configUrl("1240101097")), requests)
    }

    @Test
    fun pendingStatesSuppressPlaceholderStreamsAndPublishOnlyAnExplicitSchedule() {
        for (status in listOf("pending", "active")) {
            val config = config("live-config.json").apply {
                getObject("video").getObject("live_event").put("status", status)
                getObject("video").getObject("live_event").getObject("ingest").put("scheduled_start_time", "2099-12-01T10:30:00+02:00")
            }
            responses[configUrl("1240101097")] = FixtureResponse(JsonWriter.string(config))
            val hiddenSchedule = StreamInfo.getInfo(liveExtractor())
            assertTrue(hiddenSchedule.isUpcoming)
            assertNull(hiddenSchedule.uploadDate)
            config.getObject("video").getObject("live_event").getObject("settings").put("event_schedule", true)
            responses[configUrl("1240101097")] = FixtureResponse(JsonWriter.string(config))
            val info = StreamInfo.getInfo(liveExtractor())
            assertTrue(info.isUpcoming)
            assertTrue(info.videoStreams.isEmpty())
            assertEquals("", info.hlsUrl)
            assertEquals(OffsetDateTime.parse("2099-12-01T10:30:00+02:00").toInstant(), requireNotNull(info.uploadDate).offsetDateTime().toInstant())
        }
    }

    @Test
    fun endedBroadcastsUsePostLiveUntilAnOrdinaryArchiveIsAdvertised() {
        val ended = config("live-config.json").apply { getObject("video").getObject("live_event").put("status", "ended") }
        responses[configUrl("1240101097")] = FixtureResponse(JsonWriter.string(ended))
        val postLive = StreamInfo.getInfo(liveExtractor())
        assertEquals(StreamType.POST_LIVE_STREAM, postLive.streamType)
        assertFalse(postLive.isUpcoming)
        assertEquals(DeliveryMethod.HLS, postLive.videoStreams.single().deliveryMethod)
        ended.getObject("request").put("files", JsonObject())
        responses[configUrl("1240101097")] = FixtureResponse(JsonWriter.string(ended))
        assertThrows(ContentNotAvailableException::class.java) { liveExtractor().fetchPage() }
    }

    @Test
    fun ordinaryPublicVideoLinksAlsoRecognizeTheirLivePlayerState() {
        val video = "1240101097"
        responses["https://player.vimeo.com/video/$video"] = FixtureResponse("<script>window.playerConfig = ${fixture("live-config.json")};</script>")
        responses["https://vimeo.com/api/oembed.json?url=https%3A%2F%2Fvimeo.com%2F$video"] = FixtureResponse("{\"video_id\":$video}")
        val info = StreamInfo.getInfo(ServiceList.Vimeo, "https://vimeo.com/$video")
        assertEquals(video, info.id)
        assertEquals(StreamType.LIVE_STREAM, info.streamType)
        assertFalse(info.isUpcoming)
        assertTrue(info.hlsUrl.isNotEmpty())
    }

    private fun liveExtractor() = ServiceList.Vimeo.getStreamExtractor("https://vimeo.com/event/4768062")

    private fun fixture(name: String): String = requireNotNull(javaClass.getResource("/vimeo/events/$name")).readText()

    private fun config(name: String): JsonObject = JsonParser.`object`().from(fixture(name))

    private fun embed(eventId: String): String = "https://vimeo.com/event/$eventId/embed"

    private fun configUrl(videoId: String): String = "https://player.vimeo.com/video/$videoId/config?h=0000000000&s=fixture"

    private data class FixtureResponse(val body: String, val status: Int = 200, val finalUrl: String? = null)
}
