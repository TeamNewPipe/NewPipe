/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

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
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs

class VimeoDiscoveryTest {
    private val previousDownloader = NewPipe.getDownloader()
    private val previousLocalization = NewPipe.getPreferredLocalization()
    private val previousCountry = NewPipe.getPreferredContentCountry()
    private val requests = mutableListOf<String>()
    private val responses = mutableMapOf<String, FixtureResponse>()
    private val creator = "https://vimeo.com/nkistudio"
    private val creatorPage1 = "$creator/videos/page:1/"
    private val creatorPage2 = "$creator/videos/page:2/"
    private val staffPicks = "https://vimeo.com/channels/staffpicks"
    private val staffPage1 = "$staffPicks/videos/page:1/"
    private val staffPage2 = "$staffPicks/videos/page:2/"

    @Before
    fun setup() {
        responses[creatorPage1] = fixture("creator-page1.html")
        responses[creatorPage2] = fixture("creator-page2.html")
        responses["$creator/videos/page:14/"] = fixture("creator-page14.html")
        responses[staffPage1] = fixture("staff-picks-page1.html")
        responses[staffPage2] = fixture("staff-picks-page2.html")
        NewPipe.init(object : Downloader() {
            override fun execute(request: Request): Response {
                assertEquals("GET", request.httpMethod())
                assertFalse(request.headers().keys.any { it.equals("Authorization", true) || it.equals("Cookie", true) })
                val url = request.url()
                requests += url
                val response = responses[url] ?: throw AssertionError("Unexpected Vimeo request: $url")
                return Response(
                    response.status,
                    "fixture",
                    mapOf("Content-Type" to listOf("text/html")),
                    response.body,
                    response.body.toByteArray(Charsets.UTF_8),
                    response.finalUrl ?: url
                )
            }

            override fun executeAsync(request: Request, callback: AsyncCallback) = throw UnsupportedOperationException()
        })
    }

    @After
    fun reset() {
        NewPipe.init(previousDownloader, previousLocalization, previousCountry)
    }

    @Test
    fun capturedCreatorPageProvidesRealUploadsAndMetadataWithoutPerVideoRequests() {
        val extractor = ServiceList.Vimeo.getChannelExtractor(creator)
        extractor.fetchPage()
        assertEquals("AKAMA", extractor.name)
        assertEquals(creator, extractor.url)
        assertEquals(-1L, extractor.subscriberCount)
        assertEquals("", extractor.description)
        assertTrue(extractor.avatars.single().url.startsWith("https://i.vimeocdn.com/portrait/100439397_"))
        val first = extractor.initialPage
        assertTrue(first.errors.toString(), first.errors.isEmpty())
        assertEquals(12, first.items.size)
        val video = first.items.first()
        assertEquals("MAJJAM", video.name)
        assertEquals("https://vimeo.com/1099698712", video.url)
        assertEquals("AKAMA", video.uploaderName)
        assertEquals(creator, video.uploaderUrl)
        assertEquals(OffsetDateTime.parse("2025-07-08T15:19:02Z"), requireNotNull(video.uploadDate).offsetDateTime())
        assertEquals(-1L, video.duration)
        assertEquals(-1L, video.viewCount)
        assertTrue(video.thumbnailUrl.startsWith("https://i.vimeocdn.com/video/"))
        assertEquals(creatorPage2, first.nextPage.url)
        assertEquals(ChannelTabs.VIDEOS, extractor.tabs.single().contentFilters.single().name)
        assertNull(extractor.feedUrl)
        assertEquals(listOf(creatorPage1), requests)
    }

    @Test
    fun freshChannelAndTabExtractorsContinueRealPagesAndStopAtTheCapturedEnd() {
        val initial = ServiceList.Vimeo.getChannelExtractor(creator).apply { fetchPage() }
        val next = ServiceList.Vimeo.getChannelExtractor(creator).getPage(initial.initialPage.nextPage)
        assertEquals(12, next.items.size)
        assertEquals("https://vimeo.com/1050228331", next.items.first().url)
        assertEquals("$creator/videos/page:3/", next.nextPage.url)
        val tab = ServiceList.Vimeo.getChannelTabExtractor(initial.tabs.single())
        val tabPage = tab.getPage(Page(creatorPage2))
        assertEquals(next.items.map { it.url }, tabPage.items.map { it.url })
        val last = ServiceList.Vimeo.getChannelExtractor(creator).getPage(Page("$creator/videos/page:14/"))
        assertEquals(10, last.items.size)
        assertFalse(last.hasNextPage())
        assertEquals(listOf(creatorPage1, creatorPage2, creatorPage2, "$creator/videos/page:14/"), requests)
    }

    @Test
    fun numericCreatorAliasesRetainTheirContinuationButUseThePublicCanonicalUploader() {
        val alias = "https://vimeo.com/user1939265"
        responses["$alias/videos/page:1/"] = fixture("creator-alias-page1.html")
        responses["$alias/videos/page:2/"] = fixture("creator-alias-page2.html")
        val first = ServiceList.Vimeo.getChannelExtractor(alias).apply { fetchPage() }.initialPage
        assertEquals("AKAMA", first.items.first().uploaderName)
        assertEquals(creator, first.items.first().uploaderUrl)
        assertEquals("$alias/videos/page:2/", first.nextPage.url)
        val second = ServiceList.Vimeo.getChannelExtractor(alias).getPage(first.nextPage)
        assertEquals("https://vimeo.com/1050228331", second.items.first().url)
        assertEquals("$alias/videos/page:3/", second.nextPage.url)
        assertEquals(listOf("$alias/videos/page:1/", "$alias/videos/page:2/"), requests)

        responses["$alias/videos/page:1/"] = FixtureResponse(
            fixture("creator-alias-page1.html").body.replace(
                "href=\"https://vimeo.com/nkistudio/videos/sort:date\"",
                "href=\"https://vimeo.com/othercreator/videos/sort:date\""
            )
        )
        assertThrows(ParsingException::class.java) { ServiceList.Vimeo.getChannelExtractor(alias).fetchPage() }
    }

    @Test
    fun tabFactoryUrlAndIdConstructionFetchTheAdvertisedVideos() {
        val factory = VimeoChannelTabLinkHandlerFactory.INSTANCE
        for (handler in listOf(factory.fromUrl("$creator/videos"), factory.fromId("user:nkistudio"))) {
            assertEquals(ChannelTabs.VIDEOS, handler.contentFilters.single().name)
            val tab = ServiceList.Vimeo.getChannelTabExtractor(handler).apply { fetchPage() }
            assertEquals(12, tab.initialPage.items.size)
            assertEquals("https://vimeo.com/1099698712", tab.initialPage.items.first().url)
        }
        assertEquals(listOf(creatorPage1, creatorPage1), requests)
    }

    @Test
    fun staffPicksKioskUsesRealChannelPaginationWithoutMislabelingTheCuratorAsUploader() {
        fun kiosk() = VimeoStaffPicksKioskExtractor(
            ServiceList.Vimeo,
            VimeoKioskLinkHandlerFactory.INSTANCE.fromId(VimeoKioskLinkHandlerFactory.STAFF_PICKS),
            VimeoKioskLinkHandlerFactory.STAFF_PICKS
        )
        val extractor = kiosk().apply { fetchPage() }
        val first = extractor.initialPage
        assertTrue(first.errors.toString(), first.errors.isEmpty())
        assertEquals("Staff Picks", extractor.name)
        assertEquals(12, first.items.size)
        assertEquals("https://vimeo.com/1236621193", first.items.first().url)
        assertEquals("Ibuka, Justice", first.items.first().name)
        assertEquals("", first.items.first().uploaderName)
        assertEquals("", first.items.first().uploaderUrl)
        assertEquals(staffPage2, first.nextPage.url)
        val next = kiosk().getPage(first.nextPage)
        assertEquals("PARADAÏZ", next.items.first().name)
        assertEquals("https://vimeo.com/1242836280", next.items.first().url)
        assertEquals(listOf(staffPage1, staffPage2), requests)

        val channel = ServiceList.Vimeo.getChannelExtractor(staffPicks).apply { fetchPage() }
        assertEquals("Vimeo Staff Picks", channel.name)
        assertEquals("The best short films on the internet, handpicked by Vimeo staff", channel.description)
        assertTrue(channel.avatars.single().url.contains("/channels_uploaded_thumb/"))
    }

    @Test
    fun aRecognizedEmptyListIsEmptyButUnknownHtmlAndWrongIdentityAreFailures() {
        responses[creatorPage1] = FixtureResponse(syntheticPage(""))
        val empty = ServiceList.Vimeo.getChannelExtractor(creator).apply { fetchPage() }.initialPage
        assertTrue(empty.items.isEmpty())
        assertFalse(empty.hasNextPage())

        for (body in listOf(
            "<html><body><main>Application shell without videos</main></body></html>",
            syntheticPage("").replace("href=\"/nkistudio\"", "href=\"/othercreator\""),
            syntheticPage("<li id=\"clip_1\"><a href=\"/2\" title=\"Mismatched ID\"></a></li>"),
            syntheticPage("<li>Unknown renderer</li>")
        )) {
            responses[creatorPage1] = FixtureResponse(body)
            assertThrows(ParsingException::class.java) { ServiceList.Vimeo.getChannelExtractor(creator).fetchPage() }
        }
    }

    @Test
    fun pagesCannotSwitchOriginOrCreatorAndMalformedNextLinksAreNotFollowed() {
        for (url in listOf(
            "https://example.org/nkistudio/videos/page:2/",
            "https://vimeo.com/othercreator/videos/page:2/",
            "https://player.vimeo.com/nkistudio/videos/page:2/",
            "$creator/videos/page:0/",
            "$creator/videos/page:99999999999999999999999/",
            "$creator/videos/page:2/?access_token=unexpected",
            "$creator/videos/page:2/#fragment"
        )) {
            assertThrows(url, ParsingException::class.java) {
                ServiceList.Vimeo.getChannelExtractor(creator).getPage(Page(url))
            }
        }
        assertTrue(requests.isEmpty())
        val captured = fixture("creator-page1.html").body
        for (replacement in listOf("https://example.org/list", "/othercreator/videos/page:2", "/nkistudio/videos/page:1")) {
            responses[creatorPage1] = FixtureResponse(captured.replace("href=\"/nkistudio/videos/page:2\"", "href=\"$replacement\""))
            requests.clear()
            assertThrows(ParsingException::class.java) { ServiceList.Vimeo.getChannelExtractor(creator).fetchPage() }
            assertEquals(listOf(creatorPage1), requests)
        }
    }

    @Test
    fun unavailablePrivateAndRegionalPagesRemainErrorsWithoutAlternateEndpoints() {
        for ((status, error) in listOf(
            403 to PrivateContentException::class.java,
            404 to ContentNotAvailableException::class.java,
            451 to GeographicRestrictionException::class.java
        )) {
            responses[creatorPage1] = FixtureResponse("Unavailable", status)
            requests.clear()
            assertThrows(error) { ServiceList.Vimeo.getChannelExtractor(creator).fetchPage() }
            assertEquals(listOf(creatorPage1), requests)
        }
        responses[creatorPage1] = FixtureResponse("<main><form><input type=\"password\"></form></main>")
        assertThrows(PrivateContentException::class.java) { ServiceList.Vimeo.getChannelExtractor(creator).fetchPage() }
        responses[creatorPage1] = FixtureResponse("<main>This content is not available in your region.</main>")
        assertThrows(GeographicRestrictionException::class.java) { ServiceList.Vimeo.getChannelExtractor(creator).fetchPage() }
        responses[creatorPage1] = FixtureResponse("<main>Please sign in</main>", finalUrl = "https://vimeo.com/log_in")
        assertThrows(PrivateContentException::class.java) { ServiceList.Vimeo.getChannelExtractor(creator).fetchPage() }
    }

    @Test
    fun cardShareHashesSurviveAndAnchorOnlyPaginationKeepsTheNativeSort() {
        responses[creatorPage1] = FixtureResponse(
            syntheticPage(
                "<li id=\"clip_123456789\"><a href=\"/123456789/abcdef1234\" title=\"Shared video\"></a></li>",
                "<a rel=\"next\" href=\"/nkistudio/videos/page:2/sort:date\">Next</a>"
            )
        )
        val page = ServiceList.Vimeo.getChannelExtractor(creator).apply { fetchPage() }.initialPage
        assertEquals("https://vimeo.com/123456789/abcdef1234", page.items.single().url)
        assertEquals(creatorPage2, page.nextPage.url)
        assertEquals(listOf(creatorPage1), requests)
    }

    private fun syntheticPage(cards: String, next: String = ""): String = """<html><body><main id="main"><header id="page_header"><h1><a href="/nkistudio">AKAMA</a></h1></header><div id="browse_content"><ol class="browse_videos">$cards</ol></div>$next</main></body></html>"""

    private fun fixture(name: String): FixtureResponse {
        val body = requireNotNull(javaClass.getResourceAsStream("/vimeo/discovery/$name"))
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        return FixtureResponse(body)
    }

    private data class FixtureResponse(val body: String, val status: Int = 200, val finalUrl: String? = null)
}
