/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.extractor.services.youtube.extractors

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonWriter
import java.util.ArrayDeque
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.CancellableCall
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.filter.FilterItem
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeChannelTabLinkHandlerFactory

/** Synthetic cards and tokens using the public Live-tab response shapes checked on 2026-11-24. */
class YoutubeChannelLiveSortTest {
    private val previousDownloader = NewPipe.getDownloader()
    private val previousLocalization = NewPipe.getPreferredLocalization()
    private val previousCountry = NewPipe.getPreferredContentCountry()
    private val clientVersion = YoutubeParsingHelper::class.java.getDeclaredField("clientVersion")
        .apply { isAccessible = true }
    private var previousClientVersion: Any? = null
    private val exchanges = ArrayDeque<Exchange>()
    private val requests = mutableListOf<JsonObject>()

    @Before
    fun setUp() {
        previousClientVersion = clientVersion.get(null)
        clientVersion.set(null, "2.20261123.00.00")
        NewPipe.init(
            object : Downloader() {
                override fun execute(request: Request): Response {
                    val expected = exchanges.pollFirst()
                        ?: throw AssertionError("Unexpected request: ${request.url()}")
                    assertEquals("POST", request.httpMethod())
                    assertTrue(request.url().startsWith("https://www.youtube.com/youtubei/v1/browse?"))
                    val body = parse(requireNotNull(request.dataToSend()).toString(Charsets.UTF_8))
                    requests += body
                    if (expected.continuation == null) {
                        assertEquals(CHANNEL_ID, body.getString("browseId"))
                        assertEquals("EgdzdHJlYW1z8gYECgJ6AA%3D%3D", body.getString("params"))
                        assertNull(body.getString("continuation"))
                    } else {
                        assertEquals(expected.continuation, body.getString("continuation"))
                    }
                    return Response(
                        200,
                        "OK",
                        mapOf("Content-Type" to listOf("application/json")),
                        expected.response,
                        expected.response.toByteArray(),
                        request.url()
                    )
                }

                override fun executeAsync(request: Request, callback: AsyncCallback): CancellableCall = throw AssertionError("Unexpected asynchronous request: ${request.url()}")
            },
            Localization.DEFAULT,
            ContentCountry.DEFAULT
        )
    }

    @After
    fun tearDown() {
        clientVersion.set(null, previousClientVersion)
        NewPipe.init(previousDownloader, previousLocalization, previousCountry)
    }

    @Test
    fun popularFollowsItsServerTokenAndContinuation() {
        assertSortedPages("popular", "bbbbbbbbbbb")
    }

    @Test
    fun oldestFollowsItsServerTokenAndContinuation() {
        assertSortedPages("oldest", "ccccccccccc")
    }

    @Test
    fun latestUsesTheInitialLivePageWithoutRequestingAnotherSort() {
        exchanges.add(Exchange(null, initialResponse()))
        val extractor = extractor(handler("latest"))
        val page = extractor.initialPage

        assertTrue(page.errors.toString(), page.errors.isEmpty())
        assertEquals(listOf("https://www.youtube.com/watch?v=aaaaaaaaaaa"), page.items.map { it.url })
        assertEquals(1, requests.size)
        assertTrue(exchanges.isEmpty())
    }

    @Test
    fun unavailableSortFailsInsteadOfReturningTheLatestPage() {
        val initial = parse(initialResponse())
        initial.getObject("contents").getObject("twoColumnBrowseResultsRenderer")
            .getArray("tabs").getObject(0).getObject("tabRenderer")
            .getObject("content").getObject("richGridRenderer")
            .getObject("header").getObject("chipBarViewModel").getArray("chips")
            .removeAt(2)
        exchanges.add(Exchange(null, JsonWriter.string(initial)))
        val extractor = extractor(handler("oldest"))

        assertThrows(ParsingException::class.java) { extractor.initialPage }
        assertEquals(1, requests.size)
        assertTrue(exchanges.isEmpty())
    }

    private fun assertSortedPages(sort: String, firstId: String) {
        exchanges.add(Exchange(null, initialResponse()))
        exchanges.add(Exchange("sort-$sort", continuationResponse("reloadContinuationItemsCommand", firstId, "$sort-next")))
        exchanges.add(Exchange("$sort-next", continuationResponse("appendContinuationItemsAction", "ddddddddddd", null)))
        val handler = handler(sort)
        val first = extractor(handler).initialPage

        assertTrue(first.errors.toString(), first.errors.isEmpty())
        assertEquals(listOf("https://www.youtube.com/watch?v=$firstId"), first.items.map { it.url })
        val next = requireNotNull(first.nextPage)
        assertEquals("$sort-next", parse(requireNotNull(next.body).toString(Charsets.UTF_8)).getString("continuation"))

        // Pagination and the play queue create a fresh extractor with the same sorted handler.
        val second = YoutubeChannelTabExtractor(ServiceList.YouTube, handler).getPage(next)
        assertTrue(second.errors.toString(), second.errors.isEmpty())
        assertEquals(listOf("https://www.youtube.com/watch?v=ddddddddddd"), second.items.map { it.url })
        assertNull(second.nextPage)
        assertEquals(3, requests.size)
        assertTrue(exchanges.isEmpty())
    }

    private fun handler(sort: String): ListLinkHandler = YoutubeChannelTabLinkHandlerFactory.getInstance()
        .fromQuery("channel/$CHANNEL_ID", listOf(FilterItem(-1, ChannelTabs.LIVESTREAMS)), listOf(FilterItem(-1, sort)))

    private fun extractor(handler: ListLinkHandler): YoutubeChannelTabExtractor = YoutubeChannelTabExtractor(ServiceList.YouTube, handler).apply { fetchPage() }

    private fun parse(text: String): JsonObject = JsonParser.`object`().from(text)

    private fun initialResponse(): String = """
        {
          "header": {"c4TabbedHeaderRenderer": {
            "channelId": "$CHANNEL_ID", "title": "Fixture channel",
            "avatar": {"thumbnails": [{"url": "https://example.invalid/avatar.jpg", "width": 48, "height": 48}]}
          }},
          "contents": {"twoColumnBrowseResultsRenderer": {"tabs": [{"tabRenderer": {
            "endpoint": {"commandMetadata": {"webCommandMetadata": {"url": "/channel/$CHANNEL_ID/streams"}}},
            "content": {"richGridRenderer": {
              "header": {"chipBarViewModel": {"chips": [${listOf("latest", "popular", "oldest").joinToString(",", transform = ::chip)}]}},
              "contents": [${card("aaaaaaaaaaa")}]
            }}
          }}]}}
        }
    """.trimIndent()

    private fun chip(sort: String): String = """
        {"chipViewModel": {
          "text": "$sort",
          "tapCommand": {"innertubeCommand": {"continuationCommand": {"token": "sort-$sort"}}}
        }}
    """.trimIndent()

    private fun continuationResponse(command: String, id: String, next: String?): String {
        val header = if (command == "reloadContinuationItemsCommand") {
            """{"reloadContinuationItemsCommand":{"continuationItems":[{"chipBarViewModel":{"chips":[]}}]}},"""
        } else {
            ""
        }
        val continuation = if (next == null) {
            ""
        } else {
            """,
            {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"$next"}}}}
            """.trimIndent()
        }
        return """{"onResponseReceivedActions":[$header{"$command":{"continuationItems":[${card(id)}$continuation]}}]}"""
    }

    private fun card(id: String): String = """
        {"richItemRenderer":{"content":{"lockupViewModel":{
          "contentId":"$id", "contentType":"LOCKUP_CONTENT_TYPE_VIDEO",
          "contentImage":{"thumbnailViewModel":{"image":{"sources":[{"url":"https://example.invalid/$id.jpg","width":320,"height":180}]}}},
          "metadata":{"lockupMetadataViewModel":{
            "title":{"content":"Fixture broadcast $id"},
            "metadata":{"contentMetadataViewModel":{"metadataRows":[{"metadataParts":[{"text":{"content":"100 views"}}]}]}}
          }}
        }}}}
    """.trimIndent()

    private data class Exchange(val continuation: String?, val response: String)

    companion object {
        private const val CHANNEL_ID = "UC0000000000000000000000"
    }
}
