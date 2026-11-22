/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamType

class VimeoPlaylistTest {
    private val previousDownloader = NewPipe.getDownloader()
    private val previousLocalization = NewPipe.getPreferredLocalization()
    private val previousCountry = NewPipe.getPreferredContentCountry()
    private lateinit var previousTokenProvider: () -> String?
    private val requests = mutableListOf<String>()
    private val responses = mutableMapOf<String, FixtureResponse>()
    private val showcase = "https://vimeo.com/showcase/2632481"
    private val apiPath = "/albums/2632481/videos"
    private val apiFirst = "https://api.vimeo.com$apiPath?per_page=100"
    private val apiNext = "https://api.vimeo.com$apiPath?page=2&per_page=100"
    private val firstApiVideoIds = listOf("123456789/abcdef1234", "223456789") + (1..98).map { (400000000L + it).toString() }
    private val factory = VimeoPlaylistLinkHandlerFactory.INSTANCE

    @Before
    fun setup() {
        previousTokenProvider = VimeoPublicApi.setTokenProvider { null }
        responses["$showcase/embed"] = fixture("showcase-2632481-embed.html")
        responses["https://vimeo.com/showcase/7683386/embed"] = fixture("showcase-7683386-embed.html")
        responses["https://vimeo.com/showcase/lean"] = fixture("showcase-lean.html")
        responses["https://vimeo.com/showcase/7999344/embed"] = fixture("showcase-7999344-embed.html")
        NewPipe.init(object : Downloader() {
            override fun execute(request: Request): Response {
                assertEquals("GET", request.httpMethod())
                assertFalse(request.headers().keys.any { it.equals("Cookie", true) })
                val authorization = request.headers().entries.firstOrNull { it.key.equals("Authorization", true) }?.value
                if (request.url().startsWith("https://api.vimeo.com/")) {
                    assertEquals(listOf("Bearer $TEST_TOKEN"), authorization)
                } else {
                    assertNull(authorization)
                }
                requests += request.url()
                val response = responses[request.url()] ?: throw AssertionError("Unexpected Vimeo request: ${request.url()}")
                return Response(
                    response.status,
                    "fixture",
                    emptyMap(),
                    response.body,
                    response.body.toByteArray(Charsets.UTF_8),
                    response.finalUrl ?: request.url()
                )
            }

            override fun executeAsync(request: Request, callback: AsyncCallback) = throw UnsupportedOperationException()
        })
    }

    @After
    fun reset() {
        VimeoPublicApi.setTokenProvider(previousTokenProvider)
        NewPipe.init(previousDownloader, previousLocalization, previousCountry)
    }

    @Test
    fun showcaseAlbumAndEmbedUrlsRoundTripWithoutClaimingMemberVideos() {
        for (url in listOf(
            showcase,
            "$showcase/",
            "$showcase/embed",
            "$showcase/embed2/?autoplay=1",
            "http://www.vimeo.com/album/2632481?share=copy"
        )) {
            val handler = factory.fromUrl(url)
            assertEquals("2632481", handler.id)
            assertEquals(showcase, handler.url)
            assertEquals(showcase, factory.fromId(handler.id).url)
            assertEquals(StreamingService.LinkType.PLAYLIST, ServiceList.Vimeo.getLinkTypeByUrl(url))
        }
        val custom = factory.fromUrl("https://vimeo.com/showcase/lean")
        assertEquals("slug:lean", custom.id)
        assertEquals(custom.url, factory.fromId(custom.id).url)
        for (member in listOf("$showcase/video/79010983", "https://vimeo.com/album/2632481/video/79010983")) {
            assertFalse(factory.onAcceptUrl(member))
            assertEquals(StreamingService.LinkType.STREAM, ServiceList.Vimeo.getLinkTypeByUrl(member))
        }
    }

    @Test
    fun malformedAndUnrelatedCollectionLinksAreRejected() {
        for (url in listOf(
            "https://vimeo.com.evil.test/showcase/2632481",
            "https://vimeo.com@evil.test/showcase/2632481",
            "https://user@vimeo.com/showcase/2632481",
            "https://player.vimeo.com/showcase/2632481",
            "https://vimeo.com:444/showcase/2632481",
            "https://vimeo.com/showcase/0",
            "https://vimeo.com/showcase/01",
            "https://vimeo.com/showcase/99999999999999999999999",
            "https://vimeo.com/showcase/%32%36%33%32%34%38%31",
            "https://vimeo.com/showcase/2632481/unknown",
            "https://vimeo.com/album/lean",
            "https://vimeo.com/manage/showcases/2632481",
            "https://vimeo.com/channels/staffpicks",
            "https://vimeo.com/79010983"
        )) {
            assertFalse(url, factory.onAcceptUrl(url))
        }
        for (id in listOf("0", "01", "2632481/anything", "slug:", "slug:2632481", "slug:../lean")) {
            assertNull(id, VimeoPlaylistTarget.fromId(id))
        }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun capturedPublicEmbedIncludesAllFourteenVideosAndTheirRealOwnersWithoutPerVideoRequests() {
        val extractor = ServiceList.Vimeo.getPlaylistExtractor(showcase).apply { fetchPage() }
        val page = extractor.initialPage
        assertEquals("Staff Favorites: November 2013", extractor.name)
        assertEquals("Vimeo", extractor.uploaderName)
        assertEquals("", extractor.uploaderUrl)
        assertEquals(14L, extractor.streamCount)
        assertEquals(14, page.items.size)
        assertTrue(page.errors.toString(), page.errors.isEmpty())
        assertFalse(page.hasNextPage())
        val first = page.items.first()
        assertEquals("The Record Breaker", first.name)
        assertEquals("https://vimeo.com/79010983/0340968867", first.url)
        assertEquals("Brian McGinn", first.uploaderName)
        assertEquals("", first.uploaderUrl)
        assertEquals(1480L, first.duration)
        assertEquals(545666L, first.viewCount)
        assertTrue(first.thumbnailUrl.startsWith("https://i.vimeocdn.com/video/454674243-"))
        assertEquals(first.thumbnailUrl, extractor.thumbnailUrl)
        assertEquals("https://vimeo.com/66617573/0befedb46a", page.items.last().url)
        assertEquals(listOf("$showcase/embed"), requests)
    }

    @Test
    fun liveAndPendingShowcaseClipsDoNotLabelLifetimePlaysAsCurrentViewers() {
        val captured = fixture("showcase-2632481-embed.html").body
        for (flag in listOf("is_live", "is_pending")) {
            responses["$showcase/embed"] = FixtureResponse(captured.replaceFirst("\"$flag\":false", "\"$flag\":true"))
            val items = ServiceList.Vimeo.getPlaylistExtractor(showcase).apply { fetchPage() }.initialPage.items
            assertEquals(StreamType.LIVE_STREAM, items.first().streamType)
            assertEquals(-1L, items.first().viewCount)
            assertEquals(-1L, items.first().duration)
            assertEquals(StreamType.VIDEO_STREAM, items[1].streamType)
            assertTrue(items[1].viewCount >= 0L)
        }
    }

    @Test
    fun capturedLargeShowcaseContainsAll224VideosIncludingItsLastEntry() {
        val url = "https://vimeo.com/showcase/7683386"
        val extractor = ServiceList.Vimeo.getPlaylistExtractor(url).apply { fetchPage() }
        val page = extractor.initialPage
        assertEquals("CPESN PPP Success Videos", extractor.name)
        assertEquals(224L, extractor.streamCount)
        assertEquals(224, page.items.size)
        assertEquals(224, page.items.map { it.url }.toSet().size)
        assertTrue(page.errors.toString(), page.errors.isEmpty())
        assertFalse(page.hasNextPage())
        assertEquals("https://vimeo.com/1200279420/32fc394f0e", page.items.first().url)
        assertEquals("https://vimeo.com/359872298/a68b14c7b2", page.items.last().url)
        assertEquals(listOf("$url/embed"), requests)
    }

    @Test
    fun customNamesResolveOnlyTheProviderDeclaredCanonicalAndPersistNumericIdentity() {
        val custom = "https://vimeo.com/showcase/lean"
        val extractor = ServiceList.Vimeo.getPlaylistExtractor(custom).apply { fetchPage() }
        val info = PlaylistInfo.getInfo(extractor)
        assertEquals("vPlanner Webinars", info.name)
        assertEquals(17L, info.streamCount)
        assertEquals(17, info.relatedItems.size)
        assertEquals("7999344", extractor.id)
        assertEquals("7999344", info.id)
        assertEquals("https://vimeo.com/showcase/7999344", extractor.url)
        assertEquals(extractor.url, info.url)
        assertEquals(custom, info.originalUrl)
        assertEquals(listOf(custom, "https://vimeo.com/showcase/7999344/embed"), requests)
        requests.clear()
        val restored = ServiceList.Vimeo.getPlaylistExtractor(info.url).apply { fetchPage() }
        assertEquals(info.relatedItems.map { it.url }, restored.initialPage.items.map { it.url })
        assertEquals(listOf("https://vimeo.com/showcase/7999344/embed"), requests)
    }

    @Test
    fun missingOrForeignCustomCanonicalIsNotInventedOrFollowed() {
        val custom = "https://vimeo.com/showcase/lean"
        for (body in listOf(
            "<html><head></head></html>",
            "<link rel=\"canonical\" href=\"https://example.org/showcase/7999344\">",
            "<link rel=\"canonical\" href=\"https://vimeo.com/showcase/another-alias\">"
        )) {
            responses[custom] = FixtureResponse(body)
            requests.clear()
            assertThrows(ParsingException::class.java) { ServiceList.Vimeo.getPlaylistExtractor(custom).fetchPage() }
            assertEquals(listOf(custom), requests)
        }
        responses[custom] = fixture("showcase-lean.html").copy(finalUrl = "https://vimeo.com/showcase/unrelated")
        assertThrows(ParsingException::class.java) { ServiceList.Vimeo.getPlaylistExtractor(custom).fetchPage() }
    }

    @Test
    fun knownEmptyCollectionsAreEmptyButUnknownAndMalformedEnvelopesFail() {
        responses["$showcase/embed"] = FixtureResponse(
            "<title>Private investigators and password security</title>" + syntheticEmbed()
        )
        val empty = ServiceList.Vimeo.getPlaylistExtractor(showcase).apply { fetchPage() }
        assertEquals(0L, empty.streamCount)
        assertTrue(empty.initialPage.items.isEmpty())
        assertFalse(empty.initialPage.hasNextPage())
        for (body in listOf(
            "<html><body>Application shell</body></html>",
            syntheticEmbed().replace("\"entity_id\":\"2632481\"", "\"entity_id\":\"2632482\""),
            syntheticEmbed().replace("\"total\":0", "\"total\":-1"),
            syntheticEmbed().replace("\"has_registration_enabled\":false", "\"unknown_registration\":false"),
            syntheticEmbed().replace("\"clips\":[]", "\"unknown\":[]"),
            syntheticEmbed().replace("\"clips\":[]", "\"clips\":[false]")
        )) {
            responses["$showcase/embed"] = FixtureResponse(body)
            assertThrows(ParsingException::class.java) { ServiceList.Vimeo.getPlaylistExtractor(showcase).fetchPage() }
        }
    }

    @Test
    fun explicitlyPartialEmbedNeedsConfiguredPublicApiAndNeverPretendsToBeComplete() {
        responses["$showcase/embed"] = FixtureResponse(syntheticEmbed(total = 3))
        assertThrows(ContentNotSupportedException::class.java) { ServiceList.Vimeo.getPlaylistExtractor(showcase).fetchPage() }
        assertEquals(listOf("$showcase/embed"), requests)
    }

    @Test
    fun optionalPublicApiFallbackKeepsMetadataAndContinuesFromAFreshSavedExtractor() {
        configureApi()
        val extractor = ServiceList.Vimeo.getPlaylistExtractor(showcase).apply { fetchPage() }
        val info = PlaylistInfo.getInfo(extractor)
        assertEquals("Representative public collection", info.name)
        assertEquals("Public curator", info.uploaderName)
        assertEquals("https://vimeo.com/staff", info.uploaderUrl)
        assertEquals(101L, info.streamCount)
        assertEquals(100, info.relatedItems.size)
        assertEquals(listOf("https://vimeo.com/123456789/abcdef1234", "https://vimeo.com/223456789"), info.relatedItems.take(2).map { it.url })
        assertEquals(apiNext, info.nextPage.url)
        val saved = persisted(info.nextPage)
        assertEquals("showcase:2632481:101", saved.id)
        val next = PlaylistInfo.getMoreItems(ServiceList.Vimeo, info.url, saved)
        assertEquals("https://vimeo.com/323456789", next.items.single().url)
        assertFalse(next.hasNextPage())
        assertEquals(101, (info.relatedItems + next.items).map { it.url }.toSet().size)
        assertEquals(listOf("$showcase/embed", "https://api.vimeo.com/albums/2632481", apiFirst, apiNext), requests)
    }

    @Test
    fun apiFallbackCannotReplaceTheAdvertisedEmbedCountWithADifferentCompleteSubset() {
        configureApi()
        responses["$showcase/embed"] = FixtureResponse(syntheticEmbed(total = 3))
        for (total in listOf(2, 4)) {
            responses[apiFirst] = FixtureResponse(apiPage(1, "null", firstApiVideoIds.take(total), total = total))
            val extractor = ServiceList.Vimeo.getPlaylistExtractor(showcase)
            assertThrows(ParsingException::class.java) { extractor.fetchPage() }
            assertEquals(3L, extractor.streamCount)
        }
    }

    @Test
    fun persistedContinuationRejectsChangedTotalsOnAFreshExtractor() {
        configureApi()
        val original = ServiceList.Vimeo.getPlaylistExtractor(showcase).apply { fetchPage() }
        val saved = persisted(original.initialPage.nextPage)
        for ((total, ids) in listOf(
            100 to emptyList(),
            102 to listOf("323456789", "523456789")
        )) {
            responses[apiNext] = FixtureResponse(apiPage(2, "null", ids, total = total))
            requests.clear()
            assertThrows(ParsingException::class.java) { PlaylistInfo.getMoreItems(ServiceList.Vimeo, showcase, saved) }
            assertEquals(listOf(apiNext), requests)
        }
        requests.clear()
        for (id in listOf<String?>(null, "showcase:2632482:101", "showcase:2632481:unknown")) {
            assertThrows(ParsingException::class.java) {
                ServiceList.Vimeo.getPlaylistExtractor(showcase).getPage(Page(apiNext, id))
            }
        }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun apiResponsePageSizeMustMatchTheRequestedOffsetBeforeDeclaringTheEnd() {
        configureApi()
        val smallPage = "https://api.vimeo.com$apiPath?page=2&per_page=2"
        responses[smallPage] = FixtureResponse(apiPage(2, "null", listOf("523456789"), total = 4, perPage = 3))
        assertThrows(ParsingException::class.java) {
            ServiceList.Vimeo.getPlaylistExtractor(showcase).getPage(Page(smallPage, "showcase:2632481:4"))
        }
        assertEquals(listOf(smallPage), requests)

        // The first fallback page must also match its explicitly requested size of 100.
        responses["$showcase/embed"] = FixtureResponse(syntheticEmbed(total = 2))
        responses[apiFirst] = FixtureResponse(apiPage(1, "null", firstApiVideoIds.take(2), total = 2, perPage = 2))
        assertThrows(ParsingException::class.java) { ServiceList.Vimeo.getPlaylistExtractor(showcase).fetchPage() }
    }

    @Test
    fun customContinuationRevalidatesCanonicalAndCannotSwitchCollections() {
        configureApi()
        val custom = "https://vimeo.com/showcase/lean"
        val continuation = ServiceList.Vimeo.getPlaylistExtractor(showcase).apply { fetchPage() }.initialPage.nextPage
        requests.clear()
        responses[custom] = FixtureResponse("<link rel=\"canonical\" href=\"$showcase\">")
        val next = ServiceList.Vimeo.getPlaylistExtractor(custom).getPage(continuation)
        assertEquals("https://vimeo.com/323456789", next.items.single().url)
        assertEquals(listOf(custom, apiNext), requests)
        responses[custom] = fixture("showcase-lean.html")
        requests.clear()
        assertThrows(ParsingException::class.java) { ServiceList.Vimeo.getPlaylistExtractor(custom).getPage(continuation) }
        assertEquals(listOf(custom), requests)
    }

    @Test
    fun continuationRejectsForeignHostsOtherCollectionsFiltersAndMalformedPagesBeforeRequests() {
        configureApi()
        for (url in listOf(
            "https://example.org$apiPath?page=2",
            "https://api.vimeo.com/albums/2632482/videos?page=2",
            "https://api.vimeo.com/videos?page=2",
            "https://api.vimeo.com$apiPath?page=0",
            "https://api.vimeo.com$apiPath?page=not-a-page",
            "https://api.vimeo.com$apiPath?page=2&query=filtered",
            "https://api.vimeo.com$apiPath?page=2&access_token=forbidden",
            "https://api.vimeo.com$apiPath?page=2#fragment"
        )) {
            assertThrows(url, ParsingException::class.java) {
                ServiceList.Vimeo.getPlaylistExtractor(showcase).getPage(Page(url, "showcase:2632481:101"))
            }
        }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun apiCannotSilentlyTruncateRepeatOrChangeTheShowcaseOnItsNextPage() {
        configureApi()
        for (response in listOf(
            apiPage(1, "null", firstApiVideoIds),
            apiPage(1, "\"$apiPath?page=1&per_page=100\"", firstApiVideoIds),
            apiPage(1, "\"$apiPath?page=2&per_page=1\"", firstApiVideoIds),
            apiPage(1, "\"$apiPath?page=2&per_page=100\"", firstApiVideoIds.take(1)),
            apiPage(1, "\"/albums/2632482/videos?page=2&per_page=100\"", firstApiVideoIds),
            "{\"error\":\"Unknown response\"}"
        )) {
            responses[apiFirst] = FixtureResponse(response)
            assertThrows(ParsingException::class.java) { ServiceList.Vimeo.getPlaylistExtractor(showcase).fetchPage() }
        }
    }

    @Test
    fun passwordRegistrationSsoAndUnavailableEmbedsDoNotTriggerApiFallback() {
        configureApi()
        for (body in listOf(
            "<main><input type=\"password\"></main>",
            syntheticEmbed(total = 3, registered = true),
            syntheticEmbed(total = 3, sso = true),
            syntheticEmbed(total = 3, error = "password_required")
        )) {
            responses["$showcase/embed"] = FixtureResponse(body)
            requests.clear()
            assertThrows(PrivateContentException::class.java) { ServiceList.Vimeo.getPlaylistExtractor(showcase).fetchPage() }
            assertEquals(listOf("$showcase/embed"), requests)
        }
        responses["$showcase/embed"] = FixtureResponse(syntheticEmbed(total = 3, error = "Unavailable"))
        requests.clear()
        assertThrows(ContentNotAvailableException::class.java) { ServiceList.Vimeo.getPlaylistExtractor(showcase).fetchPage() }
        assertEquals(listOf("$showcase/embed"), requests)
        responses["$showcase/embed"] = FixtureResponse("Unavailable", status = 403)
        assertThrows(PrivateContentException::class.java) { ServiceList.Vimeo.getPlaylistExtractor(showcase).fetchPage() }
    }

    @Test
    fun malformedOrMismatchedMemberConfigUrlsFailWithoutFollowingThem() {
        val captured = fixture("showcase-2632481-embed.html").body
        for (replacement in listOf(
            "https://example.org/video/79010983/config?h=0340968867",
            "https://player.vimeo.com/video/999/config?h=0340968867",
            "https://player.vimeo.com/video/79010983/config?h=invalid",
            "https://player.vimeo.com/video/79010983/config?h=0340968867&h=aaaaaaaaaa"
        )) {
            responses["$showcase/embed"] = FixtureResponse(
                captured.replace("https://player.vimeo.com/video/79010983/config?h=0340968867", replacement)
            )
            requests.clear()
            assertThrows(ParsingException::class.java) { ServiceList.Vimeo.getPlaylistExtractor(showcase).fetchPage() }
            assertEquals(listOf("$showcase/embed"), requests)
        }
    }

    private fun configureApi() {
        VimeoPublicApi.setTokenProvider { TEST_TOKEN }
        responses["$showcase/embed"] = FixtureResponse(syntheticEmbed(total = 101))
        responses["https://api.vimeo.com/albums/2632481"] = FixtureResponse(
            """{"uri":"/users/123/albums/2632481","name":"Representative public collection","privacy":{"view":"anybody"},"user":{"name":"Public curator","link":"https://vimeo.com/staff"}}"""
        )
        responses[apiFirst] = FixtureResponse(apiPage(1, "\"$apiPath?page=2&per_page=100\"", firstApiVideoIds))
        responses[apiNext] = FixtureResponse(apiPage(2, "null", listOf("323456789")))
    }

    private fun apiPage(number: Int, next: String, ids: List<String>, total: Int = 101, perPage: Int = 100): String {
        val items = ids.map { id ->
            """{"uri":"/videos/${id.substringBefore('/')}","link":"https://vimeo.com/$id","name":"Representative API video","duration":60,"user":{"name":"Video creator","link":"https://vimeo.com/creator"}}"""
        }.joinToString(",")
        return """{"total":$total,"page":$number,"per_page":$perPage,"paging":{"next":$next},"data":[$items]}"""
    }

    private fun syntheticEmbed(total: Int = 0, registered: Boolean = false, sso: Boolean = false, error: String? = null): String {
        val data = JsonParser.`object`().from(
            """{"playlist_data":{"id":2632481,"type":"Album","title":"Representative showcase","user":"Public curator","total":$total,"sso":$sso},"clips":[],"error":null}"""
        ).apply { put("error", error) }
        val entity = JsonObject().apply {
            put("entity_id", "2632481")
            put("entity_type", "album")
            put("has_registration_enabled", registered)
        }
        return "<script>var dataForPlayer = ${JsonWriter.string(data)}; var entityData = ${JsonWriter.string(entity)};</script>"
    }

    private fun fixture(name: String): FixtureResponse = FixtureResponse(
        requireNotNull(javaClass.getResourceAsStream("/vimeo/playlists/$name")).bufferedReader().use { it.readText() }
    )

    private fun persisted(page: Page): Page {
        val bytes = ByteArrayOutputStream().use { output ->
            ObjectOutputStream(output).use { it.writeObject(page) }
            output.toByteArray()
        }
        return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as Page }
    }

    private data class FixtureResponse(val body: String, val status: Int = 200, val finalUrl: String? = null)

    companion object {
        private const val TEST_TOKEN = "representative-public-token-not-a-credential"
    }
}
