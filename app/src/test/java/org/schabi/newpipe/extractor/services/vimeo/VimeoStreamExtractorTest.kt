/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonWriter
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.jsoup.Jsoup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ServiceTemporaryBlockedException
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

class VimeoStreamExtractorTest {
    private val previousDownloader = NewPipe.getDownloader()
    private val previousLocalization = NewPipe.getPreferredLocalization()
    private val previousCountry = NewPipe.getPreferredContentCountry()
    private val requests = mutableListOf<String>()
    private val responses = mutableMapOf<String, FixtureResponse>()
    private val publicUrl = "https://vimeo.com/35572051"
    private val playerUrl = "https://player.vimeo.com/video/35572051"

    @Before
    fun setup() {
        responses[playerUrl] = FixtureResponse(fixture("clear-progressive.html"))
        responses[oembedUrl(publicUrl)] = FixtureResponse(fixture("clear-progressive-oembed.json"))
        responses["https://player.vimeo.com/video/1236621193"] = FixtureResponse(fixture("clear-adaptive.html"))
        responses[oembedUrl("https://vimeo.com/1236621193")] = FixtureResponse(fixture("clear-adaptive-oembed.json"))
        NewPipe.init(object : Downloader() {
            override fun execute(request: Request): Response {
                assertEquals("GET", request.httpMethod())
                assertFalse(request.headers().keys.any { it.equals("Authorization", true) || it.equals("Cookie", true) || it.equals("Referer", true) })
                val url = request.url()
                requests += url
                val response = responses[url] ?: throw AssertionError("Unexpected Vimeo request: $url")
                return Response(response.status, "fixture", emptyMap(), response.body, response.body.toByteArray(Charsets.UTF_8), response.finalUrl ?: url)
            }

            override fun executeAsync(request: Request, callback: AsyncCallback) = throw AssertionError("Unexpected asynchronous Vimeo request")
        })
    }

    @After
    fun reset() {
        NewPipe.init(previousDownloader, previousLocalization, previousCountry)
    }

    @Test
    fun capturedPublicPlayerSuppliesPlayableProgressiveAndHlsWithMetadata() {
        val info = StreamInfo.getInfo(ServiceList.Vimeo, "$publicUrl#t=1m")
        assertTrue(info.errors.toString(), info.errors.isEmpty())
        assertEquals("BBB loves CC (feat. Big Buck Bunny)", info.name)
        assertEquals(publicUrl, info.url)
        assertEquals("35572051", info.id)
        assertEquals(StreamType.VIDEO_STREAM, info.streamType)
        assertEquals(141L, info.duration)
        assertEquals(60L, info.startPosition)
        assertEquals("Julius Tuomisto", info.uploaderName)
        assertEquals("https://vimeo.com/user10140200", info.uploaderUrl)
        assertTrue(info.description.content.contains("Creative Commons"))
        assertEquals("2012-01-24 09:43:25", info.textualUploadDate)
        assertTrue(requireNotNull(info.uploadDate).isApproximation)
        assertEquals(-1L, info.viewCount)
        assertEquals(-1L, info.likeCount)
        val progressive = info.videoStreams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        assertEquals(listOf("360p", "720p"), progressive.map { it.resolution })
        assertEquals(listOf(640, 1280), progressive.map { it.width })
        assertTrue(progressive.all { it.format == MediaFormat.MPEG_4 && !it.isVideoOnly })
        val adaptive = info.videoStreams.single { it.deliveryMethod == DeliveryMethod.HLS }
        assertEquals(info.hlsUrl, adaptive.content)
        assertEquals(info.hlsUrl, adaptive.manifestUrl)
        assertFalse(adaptive.isVideoOnly)
        assertEquals("", info.dashMpdUrl)
        assertTrue(info.audioStreams.isEmpty())
        assertTrue(info.videoOnlyStreams.isEmpty())
        assertEquals(listOf(playerUrl, oembedUrl(publicUrl)), requests)
    }

    @Test
    fun capturedAdaptiveOnlyStaffPickExposesAHlsVideoStreamAndRealCaptionLanguages() {
        val extractor = extractor("https://vimeo.com/1236621193").apply { fetchPage() }
        assertEquals("Ibuka, Justice", extractor.name)
        assertEquals(1372L, extractor.length)
        assertEquals(StreamType.VIDEO_STREAM, extractor.streamType)
        val stream = extractor.videoStreams.single()
        assertEquals(DeliveryMethod.HLS, stream.deliveryMethod)
        assertEquals(VideoStream.RESOLUTION_UNKNOWN, stream.resolution)
        assertFalse(stream.isVideoOnly)
        assertEquals(extractor.hlsUrl, stream.content)
        // The captured 'dash' resource is Vimeo's JSON format, not an MPD usable by Media3.
        assertEquals("", extractor.dashMpdUrl)
        assertFalse(extractor.videoStreams.any { it.content.contains("playlist.json") })
        assertEquals(listOf("ar", "en-US"), extractor.subtitlesDefault.map { it.languageTag })
        assertTrue(extractor.subtitlesDefault.all { it.format == MediaFormat.VTT && !it.isAutoGenerated })
        assertTrue(extractor.subtitlesDefault.all { it.content.startsWith("https://captions.vimeo.com/") })
        assertEquals(extractor.subtitlesDefault, extractor.getSubtitles(MediaFormat.VTT))
        assertTrue(extractor.getSubtitles(MediaFormat.SRT).isEmpty())
        assertEquals(2, requests.size)
    }

    @Test
    fun sharedHashIsKeptInPlayerMetadataAndStoredIdentityWithoutProbingTheNumericUrl() {
        val hash = "8272103f6e"
        val canonical = "$publicUrl/$hash"
        val config = publicConfig().apply {
            getObject("video").put("privacy", "unlisted")
            getObject("video").put("unlisted_hash", hash)
        }
        responses["$playerUrl?h=$hash"] = FixtureResponse(syntheticPage(config))
        responses[oembedUrl(canonical)] = FixtureResponse(fixture("clear-progressive-oembed.json"))
        val original = "$playerUrl?h=$hash#t=1m30s"
        val extractor = extractor(original).apply { fetchPage() }
        assertEquals("35572051/$hash", extractor.id)
        assertEquals(canonical, extractor.url)
        assertEquals(original, extractor.originalUrl)
        assertEquals(StreamExtractor.Privacy.UNLISTED, extractor.privacy)
        assertEquals(90L, extractor.timeStamp)
        assertEquals(listOf("$playerUrl?h=$hash", oembedUrl(canonical)), requests)

        responses[playerUrl] = FixtureResponse(syntheticPage(config))
        requests.clear()
        assertThrows(PrivateContentException::class.java) { extractor().fetchPage() }
        assertEquals(listOf(playerUrl), requests)
    }

    @Test
    fun capturedPrivateAndDrmOnlyPlayersAreTerminalWithoutMetadataOrAlternateRequests() {
        for ((id, fixtureName, exception) in listOf(
            Triple("59777392", "private.html", PrivateContentException::class.java),
            Triple("76979871", "drm-only.html", ContentNotSupportedException::class.java)
        )) {
            val url = "https://player.vimeo.com/video/$id"
            responses[url] = FixtureResponse(fixture(fixtureName))
            requests.clear()
            assertThrows(exception) { extractor("https://vimeo.com/$id").fetchPage() }
            assertEquals(listOf(url), requests)
        }
    }

    @Test
    fun passwordPrivateAndDomainRestrictionsAreNotOverriddenByAdvertisedFiles() {
        val restrictions = listOf<(JsonObject) -> Unit>(
            { it.put("view", 4) },
            { it.getObject("video").put("privacy", "password") },
            { it.getObject("video").put("privacy", "nobody") },
            { it.getObject("video").put("privacy", "users") },
            { it.getObject("video").put("embed_permission", "whitelist") },
            { it.put("domain_status_code", 403) }
        )
        for (restrict in restrictions) {
            responses[playerUrl] = FixtureResponse(syntheticPage(publicConfig().apply(restrict)))
            requests.clear()
            assertThrows(PrivateContentException::class.java) { extractor().fetchPage() }
            assertEquals(listOf(playerUrl), requests)
        }
    }

    @Test
    fun declaredClearProgressiveRemainsAvailableWhenAdaptivePlaybackUsesDrm() {
        val config = publicConfig().apply {
            getObject("request").put("drm", JsonObject().apply { put("widevine", "synthetic-protection-marker") })
        }
        responses[playerUrl] = FixtureResponse(syntheticPage(config))
        val extractor = extractor().apply { fetchPage() }
        assertEquals(2, extractor.videoStreams.size)
        assertTrue(extractor.videoStreams.all { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP })
        assertEquals("", extractor.hlsUrl)
        assertEquals("", extractor.dashMpdUrl)
    }

    @Test
    fun anAdvertisedMpdIsUsableButJsonAndForeignOrProtectedResourcesAreNotSubstituted() {
        val config = publicConfig()
        val files = config.getObject("request").getObject("files")
        files.remove("progressive")
        files.remove("hls")
        responses[playerUrl] = FixtureResponse(syntheticPage(config))
        assertThrows(ContentNotAvailableException::class.java) { extractor().fetchPage() }

        // Synthetic supported MPD case; no production URL conversion or guessed endpoint exists.
        val cdns = files.getObject("dash").getObject("cdns")
        for (cdn in cdns.values.filterIsInstance<JsonObject>()) {
            cdn.put("url", "https://vod-adaptive-ak.vimeocdn.com/fixture/playlist.mpd?signature=unchanged")
            cdn.remove("avc_url")
        }
        responses[playerUrl] = FixtureResponse(syntheticPage(config))
        val mpd = extractor().apply { fetchPage() }
        assertEquals(DeliveryMethod.DASH, mpd.videoStreams.single().deliveryMethod)
        assertEquals("https://vod-adaptive-ak.vimeocdn.com/fixture/playlist.mpd?signature=unchanged", mpd.videoStreams.single().content)

        for (url in listOf("https://example.org/playlist.mpd", "https://vimeocdn.com.example.org/playlist.mpd", "http://vod-adaptive-ak.vimeocdn.com/playlist.mpd")) {
            for (cdn in cdns.values.filterIsInstance<JsonObject>()) cdn.put("url", url)
            responses[playerUrl] = FixtureResponse(syntheticPage(config))
            assertThrows(ContentNotAvailableException::class.java) { extractor().fetchPage() }
        }
        for (cdn in cdns.values.filterIsInstance<JsonObject>()) cdn.put("url", "https://vod-adaptive-ak.vimeocdn.com/fixture/drm/playlist.mpd")
        responses[playerUrl] = FixtureResponse(syntheticPage(config))
        assertThrows(ContentNotSupportedException::class.java) { extractor().fetchPage() }
    }

    @Test
    fun mismatchedVideoOrRedirectAndMalformedPlayerDataFailBeforeMetadata() {
        for (response in listOf(
            FixtureResponse(syntheticPage(publicConfig().apply { getObject("video").put("id", 123) })),
            FixtureResponse("<script>window.playerConfig = {\"video\":</script>"),
            FixtureResponse("<main>No player configuration</main>"),
            FixtureResponse(fixture("clear-progressive.html"), finalUrl = "https://player.vimeo.com/video/123"),
            FixtureResponse(fixture("clear-progressive.html"), finalUrl = "https://example.org/video/35572051")
        )) {
            responses[playerUrl] = response
            requests.clear()
            assertThrows(ParsingException::class.java) { extractor().fetchPage() }
            assertEquals(listOf(playerUrl), requests)
        }
    }

    @Test
    fun playerJsonParsingRespectsQuotedBracesEscapesAndTrailingJavaScript() {
        val name = "A }; { title with \\\"quoted\\\" text"
        val config = publicConfig().apply { getObject("video").put("title", name) }
        responses[playerUrl] = FixtureResponse(syntheticPage(config).replace(";</script>", "; window.unrelated = {value: 1};</script>"))
        val extractor = extractor().apply { fetchPage() }
        assertEquals(name, extractor.name)
    }

    @Test
    fun unavailableOptionalMetadataDoesNotDiscardValidPlaybackButDenialsAndWrongIdsRemainErrors() {
        for (response in listOf(FixtureResponse("Not found", 404), FixtureResponse("not json"))) {
            responses[oembedUrl(publicUrl)] = response
            val extractor = extractor().apply { fetchPage() }
            assertEquals("BBB loves CC (feat. Big Buck Bunny)", extractor.name)
            assertTrue(extractor.videoStreams.isNotEmpty())
            assertEquals("", extractor.description.content)
        }
        responses[oembedUrl(publicUrl)] = FixtureResponse("{\"video_id\": 35572051, \"domain_status_code\": 403}")
        assertThrows(PrivateContentException::class.java) { extractor().fetchPage() }
        responses[oembedUrl(publicUrl)] = FixtureResponse("{\"video_id\": 123}")
        assertThrows(ParsingException::class.java) { extractor().fetchPage() }
        responses[oembedUrl(publicUrl)] = FixtureResponse("Restricted", 403)
        assertThrows(PrivateContentException::class.java) { extractor().fetchPage() }
    }

    @Test
    fun httpRestrictionsRemainErrorsWithOneRequestAndNoAlternateEndpoint() {
        for ((status, exception) in listOf(
            401 to PrivateContentException::class.java,
            403 to PrivateContentException::class.java,
            402 to PaidContentException::class.java,
            404 to ContentNotAvailableException::class.java,
            410 to ContentNotAvailableException::class.java,
            429 to ServiceTemporaryBlockedException::class.java,
            451 to GeographicRestrictionException::class.java
        )) {
            responses[playerUrl] = FixtureResponse("Unavailable", status)
            requests.clear()
            assertThrows(exception) { extractor().fetchPage() }
            assertEquals(listOf(playerUrl), requests)
        }
    }

    @Test
    fun timestampParsingKeepsEveryUnitAndRejectsMalformedOrOverflowedValuesWithoutFetching() {
        for ((value, seconds) in listOf(
            "1m" to 60L,
            "1h30s" to 3630L,
            "1h2m30s" to 3750L,
            "30s" to 30L,
            "90" to 90L,
            "0" to 0L,
            "1h" to 3600L,
            "1m2h" to 0L,
            "-30" to 0L,
            "1.5" to 0L,
            "abc" to 0L,
            "" to 0L,
            "9223372036854775808" to 0L,
            "9223372036854775807h" to 0L,
            "9223372036854775807" to 0L
        )) {
            assertEquals(value, seconds, extractor("$publicUrl#t=$value").timeStamp)
            assertEquals(value, seconds, extractor("$publicUrl?share=copy&t=$value").timeStamp)
        }
        assertEquals(60L, extractor("$publicUrl#t=1%6D").timeStamp)
        assertEquals(0L, extractor("$publicUrl?t=30&t=60").timeStamp)
        assertEquals(0L, extractor().timeStamp)
        assertTrue(requests.isEmpty())
    }

    private fun extractor(url: String = publicUrl): VimeoStreamExtractor = VimeoStreamExtractor(ServiceList.Vimeo, VimeoStreamLinkHandlerFactory.INSTANCE.fromUrl(url))

    private fun publicConfig(): JsonObject = VimeoPlayerConfig.fromDocument(
        Jsoup.parse(fixture("clear-progressive.html"), playerUrl),
        requireNotNull(VimeoVideoTarget.fromUrl(publicUrl))
    )

    /** Restriction, malformed, shared-hash and MPD edge cases are synthetic fixture mutations. */
    private fun syntheticPage(config: JsonObject): String = "<script>window.playerConfig = ${JsonWriter.string(config)};</script>"

    private fun fixture(name: String): String = requireNotNull(javaClass.getResource("/vimeo/playback/$name")).readText()

    private fun oembedUrl(url: String): String = "https://vimeo.com/api/oembed.json?url=" + URLEncoder.encode(url, StandardCharsets.UTF_8.name())

    private data class FixtureResponse(val body: String, val status: Int = 200, val finalUrl: String? = null)
}
