package org.schabi.newpipe.extractor.services.youtube.extractors

import com.grack.nanojson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class YoutubePlaylistLockupExtractorTest {
    @Test
    fun entryUploaderTakesPrecedenceOverAlbumUploader() {
        val lockup = JsonParser.`object`().from(
            """
            {
              "metadata": {
                "lockupMetadataViewModel": {
                  "metadata": {
                    "contentMetadataViewModel": {
                      "metadataRows": [
                        {
                          "metadataParts": [
                            {
                              "text": {
                                "content": "Guest Channel",
                                "commandRuns": [
                                  {
                                    "onTap": {
                                      "innertubeCommand": {
                                        "browseEndpoint": {
                                          "browseId": "UC1234567890123456789012"
                                        }
                                      }
                                    }
                                  }
                                ]
                              }
                            }
                          ]
                        }
                      ]
                    }
                  }
                }
              }
            }
            """
        )
        val extractor = YoutubePlaylistLockupExtractor(
            lockup,
            null,
            "Album Owner",
            "https://www.youtube.com/channel/UCalbum"
        )

        assertEquals("Guest Channel", extractor.getUploaderName())
        assertEquals("https://www.youtube.com/channel/UC1234567890123456789012", extractor.getUploaderUrl())
    }
}
