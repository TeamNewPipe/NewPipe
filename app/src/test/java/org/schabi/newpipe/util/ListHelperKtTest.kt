package org.schabi.newpipe.util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.VideoStream

class ListHelperKtTest {

    private val audioStreamsList = listOf(
        audioStream("m4a-128-1", MediaFormat.M4A, 128),
        audioStream("webma-192", MediaFormat.WEBMA, 192),
        audioStream("mp3-64", MediaFormat.MP3, 64),
        audioStream("webma-192-2", MediaFormat.WEBMA, 192),
        audioStream("m4a-128-2", MediaFormat.M4A, 128),
        audioStream("mp3-128", MediaFormat.MP3, 128),
        audioStream("webma-64", MediaFormat.WEBMA, 64),
        audioStream("m4a-320", MediaFormat.M4A, 320),
        audioStream("mp3-192", MediaFormat.MP3, 192),
        audioStream("webma-320", MediaFormat.WEBMA, 320)
    )

    private val audioTracksList = listOf(
        audioTrack("en.or", "en.or", Locale.ENGLISH, AudioTrackType.ORIGINAL),
        audioTrack("en.du", "en.du", Locale.ENGLISH, AudioTrackType.DUBBED),
        audioTrack("en.ds", "en.ds", Locale.ENGLISH, AudioTrackType.DESCRIPTIVE),
        audioTrack("unknown", null, null, null),
        audioTrack("de.du", "de.du", Locale.GERMAN, AudioTrackType.DUBBED),
        audioTrack("de.ds", "de.ds", Locale.GERMAN, AudioTrackType.DESCRIPTIVE)
    )

    private val videoStreamsList = listOf(
        videoStream("mpeg4-1080", MediaFormat.MPEG_4, "1080p"),
        videoStream("mpeg4-720-60", MediaFormat.MPEG_4, "720p60"),
        videoStream("mpeg4-720", MediaFormat.MPEG_4, "720p"),
        videoStream("webm-480", MediaFormat.WEBM, "480p"),
        videoStream("mpeg4-360", MediaFormat.MPEG_4, "360p"),
        videoStream("webm-360", MediaFormat.WEBM, "360p"),
        videoStream("v3gpp-240-60", MediaFormat.v3GPP, "240p60"),
        videoStream("webm-144", MediaFormat.WEBM, "144p")
    )

    private val videoStreamsTestList = listOf(
        videoStream("mpeg_4-720", MediaFormat.MPEG_4, "720p"),
        videoStream("v3gpp-240", MediaFormat.v3GPP, "240p"),
        videoStream("webm-480", MediaFormat.WEBM, "480p"),
        videoStream("v3gpp-144", MediaFormat.v3GPP, "144p"),
        videoStream("mpeg_4-360", MediaFormat.MPEG_4, "360p"),
        videoStream("webm-360", MediaFormat.WEBM, "360p")
    )

    private val videoOnlyStreamsTestList = listOf(
        videoStream("mpeg_4-720-1", MediaFormat.MPEG_4, "720p", isVideoOnly = true),
        videoStream("mpeg_4-720-2", MediaFormat.MPEG_4, "720p", isVideoOnly = true),
        videoStream("mpeg_4-2160", MediaFormat.MPEG_4, "2160p", isVideoOnly = true),
        videoStream("mpeg_4-1440_60", MediaFormat.MPEG_4, "1440p60", isVideoOnly = true),
        videoStream("webm-720_60", MediaFormat.WEBM, "720p60", isVideoOnly = true),
        videoStream("mpeg_4-2160_60", MediaFormat.MPEG_4, "2160p60", isVideoOnly = true),
        videoStream("mpeg_4-720_60", MediaFormat.MPEG_4, "720p60", isVideoOnly = true),
        videoStream("mpeg_4-1080", MediaFormat.MPEG_4, "1080p", isVideoOnly = true),
        videoStream("mpeg_4-1080_60", MediaFormat.MPEG_4, "1080p60", isVideoOnly = true)
    )

    // getVideoStreamIndex – standard quality strings

    @Test
    fun `getVideoStreamIndex() must match exact resolution`() {
        assertEquals(
            2,
            ListHelper.getVideoStreamIndex("720p", MediaFormat.MPEG_4, videoStreamsList)
        )
    }

    @Test
    fun `getVideoStreamIndex() must match resolution with fps`() {
        assertEquals(
            1,
            ListHelper.getVideoStreamIndex("720p60", MediaFormat.MPEG_4, videoStreamsList)
        )
    }

    // getVideoStreamIndex – bitrate-qualified resolution strings

    @Test
    fun `getVideoStreamIndex() must ignore bitrate suffix with k unit`() {
        assertEquals(
            1,
            ListHelper.getVideoStreamIndex("720p60@1500k", MediaFormat.MPEG_4, videoStreamsList)
        )
    }

    @Test
    fun `getVideoStreamIndex() must ignore bitrate suffix with m unit`() {
        assertEquals(
            0,
            ListHelper.getVideoStreamIndex("1080p@2m", MediaFormat.MPEG_4, videoStreamsList)
        )
    }

    // getVideoStreamIndex – comprehensive match combinations

    @Test
    fun `getVideoStreamIndex() must match format, resolution and fps exactly`() {
        assertEquals(
            1,
            ListHelper.getVideoStreamIndex("720p60", MediaFormat.MPEG_4, videoStreamsList)
        )
    }

    @Test
    fun `getVideoStreamIndex() must fall back when fps does not match`() {
        assertEquals(
            0,
            ListHelper.getVideoStreamIndex("1080p60", MediaFormat.MPEG_4, videoStreamsList)
        )
    }

    @Test
    fun `getVideoStreamIndex() must fall back to 240p60 when 240p is not available`() {
        assertEquals(
            6,
            ListHelper.getVideoStreamIndex("240p", MediaFormat.v3GPP, videoStreamsList)
        )
    }

    @Test
    fun `getVideoStreamIndex() must fall back to other format when WEBM 720p60 is missing`() {
        assertEquals(
            1,
            ListHelper.getVideoStreamIndex("720p60", MediaFormat.WEBM, videoStreamsList)
        )
    }

    @Test
    fun `getVideoStreamIndex() must match 720p ignoring format mismatch`() {
        assertEquals(
            2,
            ListHelper.getVideoStreamIndex("720p", MediaFormat.WEBM, videoStreamsList)
        )
    }

    @Test
    fun `getVideoStreamIndex() must match 720p60 with null format`() {
        assertEquals(1, ListHelper.getVideoStreamIndex("720p60", null, videoStreamsList))
    }

    @Test
    fun `getVideoStreamIndex() must match 720p with null format`() {
        assertEquals(2, ListHelper.getVideoStreamIndex("720p", null, videoStreamsList))
    }

    @Test
    fun `getVideoStreamIndex() must fall back to 1080p when 1080p60 WEBM is missing`() {
        assertEquals(
            0,
            ListHelper.getVideoStreamIndex("1080p60", MediaFormat.WEBM, videoStreamsList)
        )
    }

    @Test
    fun `getVideoStreamIndex() must fall back to 240p60 when 240p WEBM is missing`() {
        assertEquals(
            6,
            ListHelper.getVideoStreamIndex("240p", MediaFormat.WEBM, videoStreamsList)
        )
    }

    @Test
    fun `getVideoStreamIndex() must fall back to 1080p when 1080p60 with null format is missing`() {
        assertEquals(0, ListHelper.getVideoStreamIndex("1080p60", null, videoStreamsList))
    }

    @Test
    fun `getVideoStreamIndex() must fall back to 240p60 when 240p with null format is missing`() {
        assertEquals(6, ListHelper.getVideoStreamIndex("240p", null, videoStreamsList))
    }

    @Test
    fun `getVideoStreamIndex() must fall back to 144p for 200p WEBM`() {
        assertEquals(
            7,
            ListHelper.getVideoStreamIndex("200p", MediaFormat.WEBM, videoStreamsList)
        )
    }

    @Test
    fun `getVideoStreamIndex() must fall back to 144p for 200p60 WEBM`() {
        assertEquals(
            7,
            ListHelper.getVideoStreamIndex("200p60", MediaFormat.WEBM, videoStreamsList)
        )
    }

    @Test
    fun `getVideoStreamIndex() must fall back to 144p for 200p MPEG4`() {
        assertEquals(
            7,
            ListHelper.getVideoStreamIndex("200p", MediaFormat.MPEG_4, videoStreamsList)
        )
    }

    @Test
    fun `getVideoStreamIndex() must fall back to 144p for 200p60 MPEG4`() {
        assertEquals(
            7,
            ListHelper.getVideoStreamIndex("200p60", MediaFormat.MPEG_4, videoStreamsList)
        )
    }

    @Test
    fun `getVideoStreamIndex() must fall back to 144p for 200p with null format`() {
        assertEquals(7, ListHelper.getVideoStreamIndex("200p", null, videoStreamsList))
    }

    @Test
    fun `getVideoStreamIndex() must fall back to 144p for 200p60 with null format`() {
        assertEquals(7, ListHelper.getVideoStreamIndex("200p60", null, videoStreamsList))
    }

    @Test
    fun `getVideoStreamIndex() must return -1 when no match exists`() {
        assertEquals(-1, ListHelper.getVideoStreamIndex("100p", null, videoStreamsList))
    }

    @Test
    fun `getVideoStreamIndex() must match resolution when format is null`() {
        assertEquals(2, ListHelper.getVideoStreamIndex("720p", null, videoStreamsList))
    }

    // getVideoStreamIndex – bitrate tiebreaking

    @Test
    fun `getVideoStreamIndex() must pick a valid index when streams have identical quality`() {
        val streams = listOf(
            videoStream("a-720-60-low", MediaFormat.MPEG_4, "720p60"),
            videoStream("b-720-60-high", MediaFormat.MPEG_4, "720p60")
        )
        val idx = ListHelper.getVideoStreamIndex("720p60", MediaFormat.MPEG_4, streams)
        assertTrue(idx == 0 || idx == 1)
    }

    // getDefaultResolutionIndex

    @Test
    fun `getDefaultResolutionIndex() must return -1 for null list`() {
        assertEquals(
            -1,
            ListHelper.getDefaultResolutionIndex(
                "720p",
                BEST_RESOLUTION_KEY,
                MediaFormat.MPEG_4,
                null
            )
        )
    }

    @Test
    fun `getDefaultResolutionIndex() must return -1 for empty list`() {
        assertEquals(
            -1,
            ListHelper.getDefaultResolutionIndex(
                "720p",
                BEST_RESOLUTION_KEY,
                MediaFormat.MPEG_4,
                mutableListOf()
            )
        )
    }

    @Test
    fun `getDefaultResolutionIndex() must return best resolution when key is best_resolution`() {
        val list = mutableListOf(
            videoStream("a-360", MediaFormat.MPEG_4, "360p"),
            videoStream("b-720", MediaFormat.MPEG_4, "720p")
        )
        assertEquals(
            0,
            ListHelper.getDefaultResolutionIndex(
                BEST_RESOLUTION_KEY,
                BEST_RESOLUTION_KEY,
                MediaFormat.MPEG_4,
                list
            )
        )
        assertEquals("720p", list[0].getResolution())
    }

    @Test
    fun `getDefaultResolutionIndex() must match resolution from bitrate-qualified string`() {
        val list = mutableListOf(
            videoStream("a-720", MediaFormat.MPEG_4, "720p"),
            videoStream("b-480", MediaFormat.WEBM, "480p")
        )
        val idx = ListHelper.getDefaultResolutionIndex(
            "720p@2m",
            BEST_RESOLUTION_KEY,
            MediaFormat.MPEG_4,
            list
        )
        assertEquals("720p", list[idx].getResolution())
    }

    @Test
    fun `getDefaultResolutionIndex() must fall back to first when no match exists`() {
        val list = mutableListOf(
            videoStream("a-360", MediaFormat.MPEG_4, "360p"),
            videoStream("b-240", MediaFormat.WEBM, "240p")
        )
        val idx = ListHelper.getDefaultResolutionIndex(
            "1080p",
            BEST_RESOLUTION_KEY,
            MediaFormat.MPEG_4,
            list
        )
        assertEquals(0, idx)
    }

    // getDefaultResolutionIndex – comprehensive

    @Test
    fun `getDefaultResolutionIndex() must handle all resolution and format combinations`() {
        val testList = mutableListOf(
            videoStream("mpeg_4-720", MediaFormat.MPEG_4, "720p"),
            videoStream("v3gpp-240", MediaFormat.v3GPP, "240p"),
            videoStream("webm-480", MediaFormat.WEBM, "480p"),
            videoStream("webm-240", MediaFormat.WEBM, "240p"),
            videoStream("mpeg_4-240", MediaFormat.MPEG_4, "240p"),
            videoStream("webm-144", MediaFormat.WEBM, "144p"),
            videoStream("mpeg_4-360", MediaFormat.MPEG_4, "360p"),
            videoStream("webm-360", MediaFormat.WEBM, "360p")
        )

        // Have resolution and the format
        var result = testList[
            ListHelper.getDefaultResolutionIndex(
                "720p",
                BEST_RESOLUTION_KEY,
                MediaFormat.MPEG_4,
                testList
            )
        ]
        assertEquals("720p", result.getResolution())
        assertEquals(MediaFormat.MPEG_4, result.format)

        // Have resolution and the format
        result = testList[
            ListHelper.getDefaultResolutionIndex(
                "480p",
                BEST_RESOLUTION_KEY,
                MediaFormat.WEBM,
                testList
            )
        ]
        assertEquals("480p", result.getResolution())
        assertEquals(MediaFormat.WEBM, result.format)

        // Have resolution but not the format
        result = testList[
            ListHelper.getDefaultResolutionIndex(
                "480p",
                BEST_RESOLUTION_KEY,
                MediaFormat.MPEG_4,
                testList
            )
        ]
        assertEquals("480p", result.getResolution())
        assertEquals(MediaFormat.WEBM, result.format)

        // Have resolution and the format
        result = testList[
            ListHelper.getDefaultResolutionIndex(
                "240p",
                BEST_RESOLUTION_KEY,
                MediaFormat.WEBM,
                testList
            )
        ]
        assertEquals("240p", result.getResolution())
        assertEquals(MediaFormat.WEBM, result.format)

        // The best resolution
        result = testList[
            ListHelper.getDefaultResolutionIndex(
                BEST_RESOLUTION_KEY,
                BEST_RESOLUTION_KEY,
                MediaFormat.WEBM,
                testList
            )
        ]
        assertEquals("720p", result.getResolution())
        assertEquals(MediaFormat.MPEG_4, result.format)

        // Doesn't have the 60fps variant and format
        result = testList[
            ListHelper.getDefaultResolutionIndex(
                "720p60",
                BEST_RESOLUTION_KEY,
                MediaFormat.WEBM,
                testList
            )
        ]
        assertEquals("720p", result.getResolution())
        assertEquals(MediaFormat.MPEG_4, result.format)

        // Doesn't have the 60fps variant
        result = testList[
            ListHelper.getDefaultResolutionIndex(
                "480p60",
                BEST_RESOLUTION_KEY,
                MediaFormat.WEBM,
                testList
            )
        ]
        assertEquals("480p", result.getResolution())
        assertEquals(MediaFormat.WEBM, result.format)

        // Doesn't have the resolution, will return the best one
        result = testList[
            ListHelper.getDefaultResolutionIndex(
                "2160p60",
                BEST_RESOLUTION_KEY,
                MediaFormat.WEBM,
                testList
            )
        ]
        assertEquals("720p", result.getResolution())
        assertEquals(MediaFormat.MPEG_4, result.format)
    }

    // getSortedStreamVideosList

    @Test
    fun `getSortedStreamVideosList() must sort in ascending order`() {
        val result = ListHelper.getSortedStreamVideosList(
            MediaFormat.MPEG_4,
            true,
            videoStreamsTestList,
            videoOnlyStreamsTestList,
            true,
            false
        )
        val expected = listOf(
            "144p", "240p", "360p", "480p", "720p", "720p60",
            "1080p", "1080p60", "1440p60", "2160p", "2160p60"
        )
        assertEquals(expected.size, result.size)
        for (i in result.indices) {
            assertEquals(expected[i], result[i].getResolution())
        }
    }

    @Test
    fun `getSortedStreamVideosList() must sort in descending order`() {
        val result = ListHelper.getSortedStreamVideosList(
            MediaFormat.MPEG_4,
            true,
            videoStreamsTestList,
            videoOnlyStreamsTestList,
            false,
            false
        )
        val expected = listOf(
            "2160p60", "2160p", "1440p60", "1080p60", "1080p",
            "720p60", "720p", "480p", "360p", "240p", "144p"
        )
        assertEquals(expected.size, result.size)
        for (i in result.indices) {
            assertEquals(expected[i], result[i].getResolution())
        }
    }

    @Test
    fun `getSortedStreamVideosList() must prefer video-only streams when requested`() {
        val result = ListHelper.getSortedStreamVideosList(
            MediaFormat.MPEG_4,
            true,
            null,
            videoOnlyStreamsTestList,
            true,
            true
        )
        val expected = listOf(
            "720p",
            "720p60",
            "1080p",
            "1080p60",
            "1440p60",
            "2160p",
            "2160p60"
        )
        assertEquals(expected.size, result.size)
        for (i in result.indices) {
            assertEquals(expected[i], result[i].getResolution())
            assertTrue(result[i].isVideoOnly)
        }
    }

    @Test
    fun `getSortedStreamVideosList() must return mixed streams when no video-only are available`() {
        val result = ListHelper.getSortedStreamVideosList(
            MediaFormat.MPEG_4,
            true,
            videoStreamsTestList,
            null,
            false,
            true
        )
        val expected = listOf("720p", "480p", "360p", "240p", "144p")
        assertEquals(expected.size, result.size)
        for (i in result.indices) {
            assertEquals(expected[i], result[i].getResolution())
            assertFalse(result[i].isVideoOnly)
        }
    }

    @Test
    fun `getSortedStreamVideosList() must set correct video-only flag for both types`() {
        val result = ListHelper.getSortedStreamVideosList(
            MediaFormat.MPEG_4,
            true,
            videoStreamsTestList,
            videoOnlyStreamsTestList,
            true,
            true
        )
        val expected = listOf(
            "144p", "240p", "360p", "480p", "720p", "720p60",
            "1080p", "1080p60", "1440p60", "2160p", "2160p60"
        )
        val expectedVideoOnly = listOf(
            "720p",
            "720p60",
            "1080p",
            "1080p60",
            "1440p60",
            "2160p",
            "2160p60"
        )

        assertEquals(expected.size, result.size)
        for (i in result.indices) {
            assertEquals(expected[i], result[i].getResolution())
            assertEquals(
                expectedVideoOnly.contains(result[i].getResolution()),
                result[i].isVideoOnly
            )
        }
    }

    @Test
    fun `getSortedStreamVideosList() must exclude high resolutions when disabled`() {
        val result = ListHelper.getSortedStreamVideosList(
            MediaFormat.MPEG_4,
            false,
            videoStreamsTestList,
            videoOnlyStreamsTestList,
            false,
            false
        )
        val expected = listOf(
            "1080p60",
            "1080p",
            "720p60",
            "720p",
            "480p",
            "360p",
            "240p",
            "144p"
        )
        assertEquals(expected.size, result.size)
        for (i in result.indices) {
            assertEquals(expected[i], result[i].getResolution())
        }
    }

    @Test
    fun `getSortedStreamVideosList() must deduplicate and prefer the default format`() {
        val videoStreams = listOf(
            videoStream("webm-360", MediaFormat.WEBM, "360p"),
            videoStream("mpeg4-360", MediaFormat.MPEG_4, "360p")
        )
        val result = ListHelper.getSortedStreamVideosList(
            MediaFormat.MPEG_4,
            false,
            videoStreams,
            null,
            true,
            false
        )
        assertEquals(1, result.size)
        assertEquals(MediaFormat.MPEG_4, result[0].format)
    }

    @Test
    fun `getSortedStreamVideosList() must return empty list for null inputs`() {
        val result = ListHelper.getSortedStreamVideosList(
            MediaFormat.MPEG_4,
            false,
            null,
            null,
            true,
            false
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getSortedStreamVideosList() must return empty list for empty inputs`() {
        val result = ListHelper.getSortedStreamVideosList(
            MediaFormat.MPEG_4,
            false,
            emptyList(),
            emptyList(),
            true,
            false
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getSortedStreamVideosList() must filter out 1440p and 2160p when high res is disabled`() {
        val streams = listOf(
            videoStream("a-720", MediaFormat.MPEG_4, "720p"),
            videoStream("b-2160", MediaFormat.MPEG_4, "2160p"),
            videoStream("c-1440", MediaFormat.MPEG_4, "1440p")
        )
        val result = ListHelper.getSortedStreamVideosList(
            MediaFormat.MPEG_4,
            false,
            streams,
            null,
            true,
            false
        )
        assertEquals(1, result.size)
        assertEquals("720p", result[0].getResolution())
    }

    @Test
    fun `getSortedStreamVideosList() must include high resolutions when enabled`() {
        val streams = listOf(
            videoStream("a-720", MediaFormat.MPEG_4, "720p"),
            videoStream("b-2160", MediaFormat.MPEG_4, "2160p")
        )
        val result = ListHelper.getSortedStreamVideosList(
            MediaFormat.MPEG_4,
            true,
            streams,
            null,
            true,
            false
        )
        assertEquals(2, result.size)
    }

    // Playable streams filtering

    @Test
    fun `getPlayableStreams() must exclude torrent streams`() {
        val streams = listOf(
            videoStream(
                "a",
                MediaFormat.MPEG_4,
                "720p",
                deliveryMethod = DeliveryMethod.PROGRESSIVE_HTTP
            ),
            videoStream(
                "b",
                MediaFormat.MPEG_4,
                "360p",
                deliveryMethod = DeliveryMethod.TORRENT
            )
        )
        val result = ListHelper.getPlayableStreams(streams, 0)
        assertEquals(1, result.size)
        assertEquals("720p", result[0].getResolution())
    }

    @Test
    fun `getPlayableStreams() must exclude HLS OPUS streams`() {
        val streams = listOf(
            videoStream(
                "a",
                MediaFormat.MPEG_4,
                "720p",
                deliveryMethod = DeliveryMethod.HLS
            ),
            videoStream(
                "b",
                MediaFormat.OPUS,
                "360p",
                deliveryMethod = DeliveryMethod.HLS
            )
        )
        val result = ListHelper.getPlayableStreams(streams, 0)
        assertEquals(1, result.size)
        assertEquals("720p", result[0].getResolution())
    }

    @Test
    fun `getPlayableStreams() must pass all streams for non-YouTube services`() {
        val streams = listOf(
            videoStream(
                "a",
                MediaFormat.MPEG_4,
                "720p",
                deliveryMethod = DeliveryMethod.PROGRESSIVE_HTTP
            )
        )
        val result = ListHelper.getPlayableStreams(streams, 999)
        assertEquals(1, result.size)
    }

    @Test
    fun `getPlayableStreams() must filter by supported itag for YouTube`() {
        val youtubeServiceId = ServiceList.YouTube.serviceId
        val supportedItag = ItagItem.getItag(18)
        val streams = listOf(
            videoStream(
                "a-supported",
                MediaFormat.MPEG_4,
                "360p",
                itagItem = supportedItag
            )
        )
        val result = ListHelper.getPlayableStreams(streams, youtubeServiceId)
        assertEquals(1, result.size)
    }

    @Test
    fun `getPlayableStreams() must return empty list for null input`() {
        val result = ListHelper.getPlayableStreams<VideoStream>(null, 0)
        assertTrue(result.isEmpty())
    }

    // Stream delivery filtering

    @Test
    fun `getStreamsOfSpecifiedDelivery() must return only streams with matching delivery`() {
        val streams = listOf(
            videoStream(
                "a-dash",
                MediaFormat.MPEG_4,
                "720p",
                deliveryMethod = DeliveryMethod.DASH
            ),
            videoStream(
                "b-hls",
                MediaFormat.MPEG_4,
                "480p",
                deliveryMethod = DeliveryMethod.HLS
            ),
            videoStream(
                "c-dash",
                MediaFormat.WEBM,
                "360p",
                deliveryMethod = DeliveryMethod.DASH
            )
        )
        val result = ListHelper.getStreamsOfSpecifiedDelivery(streams, DeliveryMethod.DASH)
        assertEquals(2, result.size)
        assertTrue(result.all { it.deliveryMethod == DeliveryMethod.DASH })
    }

    @Test
    fun `getStreamsOfSpecifiedDelivery() must return empty list for null input`() {
        val result =
            ListHelper.getStreamsOfSpecifiedDelivery<VideoStream>(null, DeliveryMethod.DASH)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getUrlAndNonTorrentStreams() must exclude torrent streams`() {
        val streams = listOf(
            videoStream(
                "a",
                MediaFormat.MPEG_4,
                "720p",
                deliveryMethod = DeliveryMethod.PROGRESSIVE_HTTP
            ),
            videoStream(
                "b",
                MediaFormat.MPEG_4,
                "360p",
                deliveryMethod = DeliveryMethod.TORRENT
            ),
            videoStream(
                "c",
                MediaFormat.MPEG_4,
                "480p",
                deliveryMethod = DeliveryMethod.HLS
            )
        )
        val result = ListHelper.getUrlAndNonTorrentStreams(streams)
        assertEquals(2, result.size)
        assertTrue(result.none { it.deliveryMethod == DeliveryMethod.TORRENT })
    }

    @Test
    fun `getUrlAndNonTorrentStreams() must exclude non-URL streams`() {
        val streams = listOf(
            videoStream("a", MediaFormat.MPEG_4, "720p", isUrl = true),
            videoStream("b", MediaFormat.MPEG_4, "360p", isUrl = false)
        )
        val result = ListHelper.getUrlAndNonTorrentStreams(streams)
        assertEquals(1, result.size)
        assertEquals("720p", result[0].getResolution())
    }

    @Test
    fun `getUrlAndNonTorrentStreams() must return empty list for null input`() {
        val result = ListHelper.getUrlAndNonTorrentStreams<VideoStream>(null)
        assertTrue(result.isEmpty())
    }

    // Audio – highest quality format selection

    @Test
    fun `getAudioIndexByHighestRank() must select highest quality M4A stream`() {
        val cmp = ListHelper.getAudioFormatComparator(MediaFormat.M4A, false)
        val stream =
            audioStreamsList[ListHelper.getAudioIndexByHighestRank(audioStreamsList, cmp)]
        assertEquals(320, stream.averageBitrate)
        assertEquals(MediaFormat.M4A, stream.format)
    }

    @Test
    fun `getAudioIndexByHighestRank() must select highest quality WEBMA stream`() {
        val cmp = ListHelper.getAudioFormatComparator(MediaFormat.WEBMA, false)
        val stream =
            audioStreamsList[ListHelper.getAudioIndexByHighestRank(audioStreamsList, cmp)]
        assertEquals(320, stream.averageBitrate)
        assertEquals(MediaFormat.WEBMA, stream.format)
    }

    @Test
    fun `getAudioIndexByHighestRank() must select highest quality MP3 stream`() {
        val cmp = ListHelper.getAudioFormatComparator(MediaFormat.MP3, false)
        val stream =
            audioStreamsList[ListHelper.getAudioIndexByHighestRank(audioStreamsList, cmp)]
        assertEquals(192, stream.averageBitrate)
        assertEquals(MediaFormat.MP3, stream.format)
    }

    @Test
    fun `getAudioIndexByHighestRank() must fall back to highest bitrate when preferred format is absent`() {
        val cmp = ListHelper.getAudioFormatComparator(MediaFormat.MP3, false)
        val testList = listOf(
            audioStream("m4a-128", MediaFormat.M4A, 128),
            audioStream("webma-192", MediaFormat.WEBMA, 192)
        )
        val stream = testList[ListHelper.getAudioIndexByHighestRank(testList, cmp)]
        assertEquals(192, stream.averageBitrate)
        assertEquals(MediaFormat.WEBMA, stream.format)
    }

    @Test
    fun `getAudioIndexByHighestRank() must prefer highest quality format at equal bitrates`() {
        val cmp = ListHelper.getAudioFormatComparator(MediaFormat.MP3, false)
        val testList = mutableListOf(
            audioStream("webma-192-1", MediaFormat.WEBMA, 192),
            audioStream("m4a-192-1", MediaFormat.M4A, 192),
            audioStream("webma-192-2", MediaFormat.WEBMA, 192),
            audioStream("m4a-192-2", MediaFormat.M4A, 192),
            audioStream("webma-192-3", MediaFormat.WEBMA, 192),
            audioStream("m4a-192-3", MediaFormat.M4A, 192),
            audioStream("webma-192-4", MediaFormat.WEBMA, 192)
        )
        // No MP3; should fallback to highest bitrate + highest quality format (M4A)
        var stream = testList[ListHelper.getAudioIndexByHighestRank(testList, cmp)]
        assertEquals(192, stream.averageBitrate)
        assertEquals(MediaFormat.M4A, stream.format)

        // Adding another WEBMA stream should have no impact
        testList.add(audioStream("webma-192-5", MediaFormat.WEBMA, 192))
        stream = testList[ListHelper.getAudioIndexByHighestRank(testList, cmp)]
        assertEquals(192, stream.averageBitrate)
        assertEquals(MediaFormat.M4A, stream.format)
    }

    @Test
    fun `getAudioIndexByHighestRank() must return -1 for null or empty list`() {
        val cmp = ListHelper.getAudioFormatComparator(null, false)
        assertEquals(-1, ListHelper.getAudioIndexByHighestRank(null, cmp))
        assertEquals(-1, ListHelper.getAudioIndexByHighestRank(emptyList(), cmp))
    }

    // Audio – lowest quality (limit data usage) format selection

    @Test
    fun `getAudioIndexByHighestRank() must select lowest quality M4A when limiting data`() {
        val cmp = ListHelper.getAudioFormatComparator(MediaFormat.M4A, true)
        val stream =
            audioStreamsList[ListHelper.getAudioIndexByHighestRank(audioStreamsList, cmp)]
        assertEquals(128, stream.averageBitrate)
        assertEquals(MediaFormat.M4A, stream.format)
    }

    @Test
    fun `getAudioIndexByHighestRank() must select lowest quality WEBMA when limiting data`() {
        val cmp = ListHelper.getAudioFormatComparator(MediaFormat.WEBMA, true)
        val stream =
            audioStreamsList[ListHelper.getAudioIndexByHighestRank(audioStreamsList, cmp)]
        assertEquals(64, stream.averageBitrate)
        assertEquals(MediaFormat.WEBMA, stream.format)
    }

    @Test
    fun `getAudioIndexByHighestRank() must select lowest quality MP3 when limiting data`() {
        val cmp = ListHelper.getAudioFormatComparator(MediaFormat.MP3, true)
        val stream =
            audioStreamsList[ListHelper.getAudioIndexByHighestRank(audioStreamsList, cmp)]
        assertEquals(64, stream.averageBitrate)
        assertEquals(MediaFormat.MP3, stream.format)
    }

    @Test
    fun `getAudioIndexByHighestRank() must fall back to lowest bitrate when preferred format is absent and limiting data`() {
        val cmp = ListHelper.getAudioFormatComparator(MediaFormat.MP3, true)
        val testList = mutableListOf(
            audioStream("m4a-128", MediaFormat.M4A, 128),
            audioStream("webma-192-1", MediaFormat.WEBMA, 192)
        )
        // No MP3, fallback to most compact (lowest bitrate)
        var stream = testList[ListHelper.getAudioIndexByHighestRank(testList, cmp)]
        assertEquals(128, stream.averageBitrate)
        assertEquals(MediaFormat.M4A, stream.format)

        // WEBMA is more efficient than M4A at same bitrate
        testList.add(audioStream("webma-128", MediaFormat.WEBMA, 128))
        stream = testList[ListHelper.getAudioIndexByHighestRank(testList, cmp)]
        assertEquals(128, stream.averageBitrate)
        assertEquals(MediaFormat.WEBMA, stream.format)
    }

    @Test
    fun `getAudioIndexByHighestRank() must prefer most efficient format at equal bitrates when limiting data`() {
        val cmp = ListHelper.getAudioFormatComparator(MediaFormat.MP3, true)
        val testList = listOf(
            audioStream("webma-192-1", MediaFormat.WEBMA, 192),
            audioStream("m4a-192-1", MediaFormat.M4A, 192),
            audioStream("webma-256", MediaFormat.WEBMA, 256),
            audioStream("m4a-192-2", MediaFormat.M4A, 192),
            audioStream("webma-192-2", MediaFormat.WEBMA, 192),
            audioStream("m4a-192-3", MediaFormat.M4A, 192)
        )
        // No MP3, fallback to most compact: lowest bitrate + most efficient format (WEBMA)
        var stream = testList[ListHelper.getAudioIndexByHighestRank(testList, cmp)]
        assertEquals(192, stream.averageBitrate)
        assertEquals(MediaFormat.WEBMA, stream.format)

        // Same with null preferred format
        val cmpNull = ListHelper.getAudioFormatComparator(null, true)
        stream = testList[ListHelper.getAudioIndexByHighestRank(testList, cmpNull)]
        assertEquals(192, stream.averageBitrate)
        assertEquals(MediaFormat.WEBMA, stream.format)
    }

    @Test
    fun `getAudioIndexByHighestRank() must return -1 for null or empty list when limiting data`() {
        val cmp = ListHelper.getAudioFormatComparator(null, false)
        assertEquals(-1, ListHelper.getAudioIndexByHighestRank(null, cmp))
        assertEquals(-1, ListHelper.getAudioIndexByHighestRank(emptyList(), cmp))
    }

    // Audio – single element edge case

    @Test
    fun `getAudioIndexByHighestRank() must return 0 for a single-element list`() {
        val streams = listOf(audioStream("only", MediaFormat.M4A, 128))
        val cmp = ListHelper.getAudioFormatComparator(MediaFormat.M4A, false)
        assertEquals(0, ListHelper.getAudioIndexByHighestRank(streams, cmp))
    }

    // Audio track selection

    @Test
    fun `getAudioTrackComparator() must select original English track for English locale`() {
        val cmp = ListHelper.getAudioTrackComparator(Locale.ENGLISH, false, false)
        val stream =
            audioTracksList[ListHelper.getAudioIndexByHighestRank(audioTracksList, cmp)]
        assertEquals("en.or", stream.id)
    }

    @Test
    fun `getAudioTrackComparator() must select dubbed German track for German locale`() {
        val cmp = ListHelper.getAudioTrackComparator(Locale.GERMAN, false, false)
        val stream =
            audioTracksList[ListHelper.getAudioIndexByHighestRank(audioTracksList, cmp)]
        assertEquals("de.du", stream.id)
    }

    @Test
    fun `getAudioTrackComparator() must prefer original over German dubbed when preferOriginal is set`() {
        val cmp = ListHelper.getAudioTrackComparator(Locale.GERMAN, true, false)
        val stream =
            audioTracksList[ListHelper.getAudioIndexByHighestRank(audioTracksList, cmp)]
        assertEquals("en.or", stream.id)
    }

    @Test
    fun `getAudioTrackComparator() must select descriptive track when preferDescriptive is set`() {
        val cmp = ListHelper.getAudioTrackComparator(Locale.ENGLISH, false, true)
        val stream =
            audioTracksList[ListHelper.getAudioIndexByHighestRank(audioTracksList, cmp)]
        assertEquals("en.ds", stream.id)
    }

    @Test
    fun `getAudioTrackComparator() must fall back to original for unavailable language`() {
        val cmp = ListHelper.getAudioTrackComparator(Locale.JAPANESE, true, false)
        val stream =
            audioTracksList[ListHelper.getAudioIndexByHighestRank(audioTracksList, cmp)]
        assertEquals("en.or", stream.id)
    }

    companion object {
        private const val BEST_RESOLUTION_KEY = "best_resolution"

        private fun videoStream(
            id: String,
            format: MediaFormat?,
            resolution: String,
            isVideoOnly: Boolean = false,
            deliveryMethod: DeliveryMethod = DeliveryMethod.PROGRESSIVE_HTTP,
            itagItem: ItagItem? = null,
            isUrl: Boolean = true
        ): VideoStream {
            return VideoStream.Builder()
                .setId(id)
                .setContent("", isUrl)
                .setIsVideoOnly(isVideoOnly)
                .setResolution(resolution)
                .setMediaFormat(format)
                .setDeliveryMethod(deliveryMethod)
                .apply { if (itagItem != null) setItagItem(itagItem) }
                .build()
        }

        private fun audioStream(
            id: String,
            format: MediaFormat?,
            averageBitrate: Int
        ): AudioStream {
            return AudioStream.Builder()
                .setId(id)
                .setContent("", true)
                .setMediaFormat(format)
                .setAverageBitrate(averageBitrate)
                .build()
        }

        private fun audioTrack(
            id: String,
            trackId: String?,
            locale: Locale?,
            trackType: AudioTrackType?
        ): AudioStream {
            return AudioStream.Builder()
                .setId(id)
                .setContent("", true)
                .setMediaFormat(MediaFormat.M4A)
                .setAverageBitrate(128)
                .setAudioTrackId(trackId)
                .setAudioLocale(locale)
                .setAudioTrackType(trackType)
                .build()
        }
    }
}
