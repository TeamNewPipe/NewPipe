package org.schabi.newpipe.extractor.services.youtube.extractors

import com.grack.nanojson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeChannelAboutInfoTest {
    @Test
    fun parsesJoinedDateAndUnwrapsYoutubeRedirect() {
        val response = JsonParser.`object`().from(
            """{"contents":{"aboutChannelViewModel":{
              "joinedDateText":{"content":"Joined Mar 3, 2015"},
              "links":[{"channelExternalLinkViewModel":{
                "title":{"content":"3Blue1Brown website"},
                "commandRuns":[{"onTap":{"innertubeCommand":{
                  "commandMetadata":{"webCommandMetadata":{"url":
                    "/redirect?event=channel_description&q=https%3A%2F%2Fwww.3blue1brown.com%2F"}}
                }}}]
              }}]
            }}}"""
        )

        val result = YoutubeChannelAboutInfo.from(response)

        assertEquals("Joined Mar 3, 2015", result.joinedDateText)
        assertEquals(1, result.externalLinks.size)
        assertEquals("3Blue1Brown website", result.externalLinks[0].title)
        assertEquals("https://www.3blue1brown.com/", result.externalLinks[0].url)
    }

    @Test
    fun rejectsUnsafeLinksAndDeduplicatesSafeLinks() {
        val response = JsonParser.`object`().from(
            """{"aboutChannelViewModel":{"links":[
              {"channelExternalLinkViewModel":{
                "title":{"content":"Unsafe"},
                "navigationEndpoint":{"urlEndpoint":{"url":"javascript:alert(1)"}}
              }},
              {"channelExternalLinkViewModel":{
                "title":{"content":"Website"},
                "navigationEndpoint":{"urlEndpoint":{"url":"https://example.com/path"}}
              }},
              {"channelExternalLinkViewModel":{
                "title":{"content":"Duplicate"},
                "navigationEndpoint":{"urlEndpoint":{"url":"https://example.com/path"}}
              }}
            ]}}"""
        )

        val result = YoutubeChannelAboutInfo.from(response)

        assertEquals(1, result.externalLinks.size)
        assertEquals("https://example.com/path", result.externalLinks[0].url)
    }

    @Test
    fun missingAboutMetadataReturnsEmptyValues() {
        val result = YoutubeChannelAboutInfo.from(
            JsonParser.`object`().from("""{"contents":[]}""")
        )

        assertNull(result.joinedDateText)
        assertTrue(result.externalLinks.isEmpty())
    }
}
