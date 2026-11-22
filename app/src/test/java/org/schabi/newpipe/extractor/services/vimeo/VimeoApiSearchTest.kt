/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.services.vimeo

import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonWriter
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.search.filter.FilterItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class VimeoApiSearchTest : VimeoApiExtractorTestSupport() {
    private val query = "nature & water"
    private val firstUrl = "https://api.vimeo.com/videos?query=nature+%26+water&per_page=25&sort=relevant"
    private val nextUrl = "https://api.vimeo.com/videos?query=nature%20%26%20water&per_page=25&page=2&sort=relevant"

    private fun extractor(): VimeoSearchExtractor = VimeoSearchExtractor(
        ServiceList.Vimeo,
        VimeoSearchQueryHandlerFactory.INSTANCE.fromQuery(query)
    )

    @Test
    fun globalKeywordSearchUsesTheOfficialApiAndFreshExtractorPagination() {
        responses[firstUrl] = fixture("search-page1.json")
        responses[nextUrl] = fixture("search-page2.json")
        val first = extractor().apply { fetchPage() }.initialPage
        assertEquals(2, first.items.size)
        val video = first.items.first() as StreamInfoItem
        assertEquals("Nature and water", video.name)
        assertEquals("https://vimeo.com/100101", video.url)
        assertEquals("Example filmmaker", video.uploaderName)
        assertEquals("https://vimeo.com/examplefilmmaker", video.uploaderUrl)
        assertEquals(42L, video.duration)
        assertEquals(1234L, video.viewCount)
        assertEquals("https://i.vimeocdn.com/video/example_100101_1280.jpg", video.thumbnailUrl)
        assertEquals(OffsetDateTime.parse("2026-01-02T10:30:00Z"), requireNotNull(video.uploadDate).offsetDateTime())
        assertEquals(nextUrl, first.nextPage.url)

        val second = extractor().getPage(first.nextPage)
        assertEquals("Ocean", second.items.single().name)
        val anonymous = second.items.single() as StreamInfoItem
        assertEquals("", anonymous.uploaderName)
        assertEquals("", anonymous.uploaderUrl)
        assertEquals(-1L, anonymous.viewCount)
        assertNull(anonymous.thumbnailUrl)
        assertFalse(second.hasNextPage())
        assertEquals(listOf(firstUrl, nextUrl), requests)
    }

    @Test
    fun queryFactoryRoundTripsUnicodeAndRejectsUnadvertisedFilters() {
        val factory = VimeoSearchQueryHandlerFactory.INSTANCE
        val text = "nature + café & ocean"
        assertEquals(text, factory.getSearchString(factory.fromQuery(text).url))
        assertEquals("", factory.getSearchString("https://example.org/search?q=nature"))
        assertEquals("", factory.getSearchString("https://vimeo.com/search?q=one&q=two"))
        assertThrows(ParsingException::class.java) { factory.fromQuery("   ") }
        assertThrows(ContentNotSupportedException::class.java) {
            factory.fromQuery(text, listOf(FilterItem(1, "channels")), emptyList())
        }
    }

    @Test
    fun continuationsCannotChangeQueryEndpointOrOrigin() {
        for (url in listOf(
            "https://example.org/videos?query=nature%20%26%20water&page=2",
            "https://api.vimeo.com/users/200101/videos?query=nature%20%26%20water&page=2",
            "https://api.vimeo.com/videos?query=different&page=2",
            "https://api.vimeo.com/videos?query=nature%20%26%20water&page=2&sort=date",
            "https://api.vimeo.com/videos?query=nature%20%26%20water&page=2&access_token=unexpected"
        )) {
            assertThrows(url, ParsingException::class.java) { extractor().getPage(Page(url)) }
        }
        assertTrue(requests.isEmpty())
        responses[firstUrl] = FixtureResponse(fixture("search-page1.json").body.replace("nature%20%26%20water", "different"))
        assertThrows(ParsingException::class.java) { extractor().fetchPage() }
        assertEquals(listOf(firstUrl), requests)
    }

    @Test
    fun firstSearchPageRejectsEquivalentSelfLinksAndChangedOrOmittedPageSizes() {
        for (next in listOf(
            firstUrl,
            "/videos?sort=relevant&page=1&per_page=25&query=nature%20%26%20water",
            nextUrl.replace("per_page=25", "per_page=10"),
            nextUrl.replace("per_page=25&", "")
        )) {
            responses[firstUrl] = withNext("search-page1.json", next)
            requests.clear()
            assertThrows(next, ParsingException::class.java) { extractor().fetchPage() }
            assertEquals(listOf(firstUrl), requests)
        }
    }

    @Test
    fun restoredSearchPageRejectsBackwardAndRepeatedContinuations() {
        for (next in listOf(firstUrl, nextUrl)) {
            responses[nextUrl] = withNext("search-page2.json", next)
            requests.clear()
            assertThrows(next, ParsingException::class.java) { extractor().getPage(Page(nextUrl)) }
            assertEquals(listOf(nextUrl), requests)
        }
    }

    @Test
    fun emptyResultsAreRecognizedButMalformedResponsesAreErrors() {
        responses[firstUrl] = fixture("empty-page.json")
        assertTrue(extractor().apply { fetchPage() }.initialPage.items.isEmpty())
        for (body in listOf("{}", "{\"data\":{},\"paging\":{\"next\":null}}", "{\"data\":[],\"paging\":{}}", "{\"data\":[],\"paging\":{\"next\":42}}")) {
            responses[firstUrl] = FixtureResponse(body)
            assertThrows(ParsingException::class.java) { extractor().fetchPage() }
        }
    }

    @Test
    fun absentOrRejectedTokenDoesNotFallBackToWebsiteSearch() {
        VimeoPublicApi.setTokenProvider { null }
        assertThrows(ExtractionException::class.java) { extractor().fetchPage() }
        assertTrue(requests.isEmpty())
        VimeoPublicApi.setTokenProvider { "test-public-token" }
        responses[firstUrl] = FixtureResponse("{\"error\":\"Invalid credentials\",\"error_code\":8003}", 401)
        assertThrows(ExtractionException::class.java) { extractor().fetchPage() }
        assertEquals(listOf(firstUrl), requests)
    }

    @Test
    fun sharedApiItemParserPreservesExplicitShareHashesAndRejectsMismatchedIdentities() {
        val json = JsonParser.`object`().from(fixture("shared-video.json").body)
        val item = VimeoApiStreamInfoItemExtractor(json)
        assertEquals("https://vimeo.com/100104/abcdef1234", item.url)
        json["uri"] = "/videos/999999"
        assertThrows(ParsingException::class.java) { VimeoApiStreamInfoItemExtractor(json) }
        json["uri"] = "/videos/100104"
        json["link"] = "https://vimeo.com/100104"
        assertThrows(ParsingException::class.java) { VimeoApiStreamInfoItemExtractor(json) }
    }

    private fun withNext(name: String, next: String): FixtureResponse {
        val response = JsonParser.`object`().from(fixture(name).body)
        response.getObject("paging")["next"] = next
        return FixtureResponse(JsonWriter.string(response))
    }
}
