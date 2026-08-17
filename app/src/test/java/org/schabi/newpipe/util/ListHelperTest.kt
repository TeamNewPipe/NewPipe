package org.schabi.newpipe.util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.VideoStream

class ListHelperTest {

    @Test
    fun getSortedStreamVideosListTest() {
        var result = ListHelper.getSortedStreamVideosList(
            MediaFormat.MPEG_4,
            true,
            VIDEO_STREAMS_TEST_LIST,
            VIDEO_ONLY_STREAMS_TEST_LIST,
            true,
            false
        )

        val expected = listOf(
            "144p", "240p", "360p", "480p", "720p", "720p60",
            "1080p", "1080p60", "1440p60", "2160p", "2160p60"
        )

        assertEquals(expected.size, result.size)
        for (i in result.indices) {
            assertEquals(result[i].resolution, expected[i])
            assertEquals(expected[i], result[i].resolution)
        }

        // //////////////////
        // Reverse Order //
        // ////////////////

        result = ListHelper.getSortedStreamVideosList(
            MediaFormat.MPEG_4,
            true,
            VIDEO_STREAMS_TEST_LIST,
            VIDEO_ONLY_STREAMS_TEST_LIST,
            false,
            false
        )
        val expectedReverse = listOf(
            "2160p60", "2160p", "1440p60", "1080p60", "1080p", "720p60",
            "720p", "480p", "360p", "240p", "144p"
        )
        assertEquals(expectedReverse.size, result.size)
        for (i in result.indices) {
            assertEquals(expectedReverse[i], result[i].resolution)
        }
    }

    @Test
    fun getSortedStreamVideosListWithPreferVideoOnlyStreamsTest() {
        var result = ListHelper.getSortedStreamVideosList(
            MediaFormat.MPEG_4,
            true,
            null,
            VIDEO_ONLY_STREAMS_TEST_LIST,
            true,
            true
        )

        val expected = listOf("720p", "720p60", "1080p", "1080p60", "1440p60", "2160p", "2160p60")

        assertEquals(expected.size, result.size)
        for (i in result.indices) {
            assertEquals(expected[i], result[i].resolution)
            assertTrue(result[i].isVideoOnly)
        }

        // ////////////////////////////////////////////////////////
        // No video only streams -> should return mixed streams //
        // ////////////////////////////////////////////////////////

        result = ListHelper.getSortedStreamVideosList(
            MediaFormat.MPEG_4,
            true,
            VIDEO_STREAMS_TEST_LIST,
            null,
            false,
            true
        )
        val expectedMixed = listOf("720p", "480p", "360p", "240p", "144p")
        assertEquals(expectedMixed.size, result.size)
        for (i in result.indices) {
            assertEquals(expectedMixed[i], result[i].resolution)
            assertFalse(result[i].isVideoOnly)
        }

        // ///////////////////////////////////////////////////////////////
        // Both types of  streams -> should return correct one streams //
        // ///////////////////////////////////////////////////////////////

        result = ListHelper.getSortedStreamVideosList(
            MediaFormat.MPEG_4,
            true,
            VIDEO_STREAMS_TEST_LIST,
            VIDEO_ONLY_STREAMS_TEST_LIST,
            true,
            true
        )
        val expectedBoth = listOf(
            "144p", "240p", "360p", "480p", "720p", "720p60",
            "1080p", "1080p60", "1440p60", "2160p", "2160p60"
        )
        val expectedVideoOnly =
            listOf("720p", "720p60", "1080p", "1080p60", "1440p60", "2160p", "2160p60")

        assertEquals(expectedBoth.size, result.size)
        for (i in result.indices) {
            assertEquals(expectedBoth[i], result[i].resolution)
            assertEquals(expectedVideoOnly.contains(result[i].resolution), result[i].isVideoOnly)
        }
    }

    @Test
    fun getSortedStreamVideosExceptHighResolutionsTest() {
        // //////////////////////////////////
        // Don't show Higher resolutions //
        // ////////////////////////////////

        val result = ListHelper.getSortedStreamVideosList(
            MediaFormat.MPEG_4,
            false,
            VIDEO_STREAMS_TEST_LIST,
            VIDEO_ONLY_STREAMS_TEST_LIST,
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
            assertEquals(expected[i], result[i].resolution)
        }
    }

    @Test
    fun getDefaultResolutionTest() {
        val testList = mutableListOf(
            generateVideoStream("mpeg_4-720", MediaFormat.MPEG_4, "720p", false),
            generateVideoStream("v3gpp-240", MediaFormat.v3GPP, "240p", false),
            generateVideoStream("webm-480", MediaFormat.WEBM, "480p", false),
            generateVideoStream("webm-240", MediaFormat.WEBM, "240p", false),
            generateVideoStream("mpeg_4-240", MediaFormat.MPEG_4, "240p", false),
            generateVideoStream("webm-144", MediaFormat.WEBM, "144p", false),
            generateVideoStream("mpeg_4-360", MediaFormat.MPEG_4, "360p", false),
            generateVideoStream("webm-360", MediaFormat.WEBM, "360p", false)
        )
        var result = testList[
            ListHelper.getDefaultResolutionIndex(
                "720p",
                BEST_RESOLUTION_KEY,
                MediaFormat.MPEG_4,
                testList
            )
        ]
        assertEquals("720p", result.resolution)
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
        assertEquals("480p", result.resolution)
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
        assertEquals("480p", result.resolution)
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
        assertEquals("240p", result.resolution)
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
        assertEquals("720p", result.resolution)
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
        assertEquals("720p", result.resolution)
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
        assertEquals("480p", result.resolution)
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
        assertEquals("720p", result.resolution)
        assertEquals(MediaFormat.MPEG_4, result.format)
    }

    @Test
    fun getHighestQualityAudioFormatTest() {
        var cmp = ListHelper.getAudioFormatComparator(MediaFormat.M4A, false)
        var stream = AUDIO_STREAMS_TEST_LIST[
            ListHelper.getAudioIndexByHighestRank(
                AUDIO_STREAMS_TEST_LIST,
                cmp
            )
        ]
        assertEquals(320, stream.averageBitrate)
        assertEquals(MediaFormat.M4A, stream.format)

        cmp = ListHelper.getAudioFormatComparator(MediaFormat.WEBMA, false)
        stream = AUDIO_STREAMS_TEST_LIST[
            ListHelper.getAudioIndexByHighestRank(
                AUDIO_STREAMS_TEST_LIST,
                cmp
            )
        ]
        assertEquals(320, stream.averageBitrate)
        assertEquals(MediaFormat.WEBMA, stream.format)

        cmp = ListHelper.getAudioFormatComparator(MediaFormat.MP3, false)
        stream = AUDIO_STREAMS_TEST_LIST[
            ListHelper.getAudioIndexByHighestRank(
                AUDIO_STREAMS_TEST_LIST,
                cmp
            )
        ]
        assertEquals(192, stream.averageBitrate)
        assertEquals(MediaFormat.MP3, stream.format)
    }

    @Test
    fun getHighestQualityAudioFormatPreferredAbsent() {
        val cmp = ListHelper.getAudioFormatComparator(MediaFormat.MP3, false)

        // ////////////////////////////////////////
        // Doesn't contain the preferred format //
        // //////////////////////////////////////

        val testList = listOf(
            generateAudioStream("m4a-128", MediaFormat.M4A, 128),
            generateAudioStream("webma-192", MediaFormat.WEBMA, 192)
        )
        // List doesn't contains this format
        // It should fallback to the highest bitrate audio no matter what format it is
        var stream = testList[ListHelper.getAudioIndexByHighestRank(testList, cmp)]
        assertEquals(192, stream.averageBitrate)
        assertEquals(MediaFormat.WEBMA, stream.format)

        // //////////////////////////////////////////////////////
        // Multiple not-preferred-formats and equal bitrates //
        // ////////////////////////////////////////////////////

        val mutableTestList = mutableListOf(
            generateAudioStream("webma-192-1", MediaFormat.WEBMA, 192),
            generateAudioStream("m4a-192-1", MediaFormat.M4A, 192),
            generateAudioStream("webma-192-2", MediaFormat.WEBMA, 192),
            generateAudioStream("m4a-192-2", MediaFormat.M4A, 192),
            generateAudioStream("webma-192-3", MediaFormat.WEBMA, 192),
            generateAudioStream("m4a-192-3", MediaFormat.M4A, 192),
            generateAudioStream("webma-192-4", MediaFormat.WEBMA, 192)
        )
        // List doesn't contains this format, it should fallback to the highest bitrate audio and
        // the highest quality format.
        stream = mutableTestList[ListHelper.getAudioIndexByHighestRank(mutableTestList, cmp)]
        assertEquals(192, stream.averageBitrate)
        assertEquals(MediaFormat.M4A, stream.format)

        // Adding a new format and bitrate. Adding another stream will have no impact since
        // it's not a preferred format.
        mutableTestList.add(generateAudioStream("webma-192-5", MediaFormat.WEBMA, 192))
        stream = mutableTestList[ListHelper.getAudioIndexByHighestRank(mutableTestList, cmp)]
        assertEquals(192, stream.averageBitrate)
        assertEquals(MediaFormat.M4A, stream.format)
    }

    @Test
    fun getHighestQualityAudioNull() {
        val cmp = ListHelper.getAudioFormatComparator(null, false)
        assertEquals(-1, ListHelper.getAudioIndexByHighestRank(null, cmp))
        assertEquals(-1, ListHelper.getAudioIndexByHighestRank(emptyList(), cmp))
    }

    @Test
    fun getLowestQualityAudioFormatTest() {
        var cmp = ListHelper.getAudioFormatComparator(MediaFormat.M4A, true)
        var stream = AUDIO_STREAMS_TEST_LIST[
            ListHelper.getAudioIndexByHighestRank(
                AUDIO_STREAMS_TEST_LIST,
                cmp
            )
        ]
        assertEquals(128, stream.averageBitrate)
        assertEquals(MediaFormat.M4A, stream.format)

        cmp = ListHelper.getAudioFormatComparator(MediaFormat.WEBMA, true)
        stream = AUDIO_STREAMS_TEST_LIST[
            ListHelper.getAudioIndexByHighestRank(
                AUDIO_STREAMS_TEST_LIST,
                cmp
            )
        ]
        assertEquals(64, stream.averageBitrate)
        assertEquals(MediaFormat.WEBMA, stream.format)

        cmp = ListHelper.getAudioFormatComparator(MediaFormat.MP3, true)
        stream = AUDIO_STREAMS_TEST_LIST[
            ListHelper.getAudioIndexByHighestRank(
                AUDIO_STREAMS_TEST_LIST,
                cmp
            )
        ]
        assertEquals(64, stream.averageBitrate)
        assertEquals(MediaFormat.MP3, stream.format)
    }

    @Test
    fun getLowestQualityAudioFormatPreferredAbsent() {
        var cmp = ListHelper.getAudioFormatComparator(MediaFormat.MP3, true)

        // ////////////////////////////////////////
        // Doesn't contain the preferred format //
        // //////////////////////////////////////

        val testList = mutableListOf(
            generateAudioStream("m4a-128", MediaFormat.M4A, 128),
            generateAudioStream("webma-192-1", MediaFormat.WEBMA, 192)
        )
        // List doesn't contains this format
        // It should fallback to the most compact audio no matter what format it is.
        var stream = testList[ListHelper.getAudioIndexByHighestRank(testList, cmp)]
        assertEquals(128, stream.averageBitrate)
        assertEquals(MediaFormat.M4A, stream.format)

        // WEBMA is more compact than M4A
        testList.add(generateAudioStream("webma-192-2", MediaFormat.WEBMA, 128))
        stream = testList[ListHelper.getAudioIndexByHighestRank(testList, cmp)]
        assertEquals(128, stream.averageBitrate)
        assertEquals(MediaFormat.WEBMA, stream.format)

        // //////////////////////////////////////////////////////
        // Multiple not-preferred-formats and equal bitrates //
        // ////////////////////////////////////////////////////

        val testList2 = mutableListOf(
            generateAudioStream("webma-192-1", MediaFormat.WEBMA, 192),
            generateAudioStream("m4a-192-1", MediaFormat.M4A, 192),
            generateAudioStream("webma-256", MediaFormat.WEBMA, 256),
            generateAudioStream("m4a-192-2", MediaFormat.M4A, 192),
            generateAudioStream("webma-192-2", MediaFormat.WEBMA, 192),
            generateAudioStream("m4a-192-3", MediaFormat.M4A, 192)
        )
        // List doesn't contain this format
        // It should fallback to the most compact audio no matter what format it is.
        stream = testList2[ListHelper.getAudioIndexByHighestRank(testList2, cmp)]
        assertEquals(192, stream.averageBitrate)
        assertEquals(MediaFormat.WEBMA, stream.format)

        // Should be same as above
        cmp = ListHelper.getAudioFormatComparator(null, true)
        stream = testList2[ListHelper.getAudioIndexByHighestRank(testList2, cmp)]
        assertEquals(192, stream.averageBitrate)
        assertEquals(MediaFormat.WEBMA, stream.format)
    }

    @Test
    fun getLowestQualityAudioNull() {
        val cmp = ListHelper.getAudioFormatComparator(null, false)
        assertEquals(-1, ListHelper.getAudioIndexByHighestRank(null, cmp))
        assertEquals(-1, ListHelper.getAudioIndexByHighestRank(emptyList(), cmp))
    }

    @Test
    fun getAudioTrack() {
        // English language
        var cmp = ListHelper.getAudioTrackComparator(Locale.ENGLISH, false, false)
        var stream = AUDIO_TRACKS_TEST_LIST[
            ListHelper.getAudioIndexByHighestRank(
                AUDIO_TRACKS_TEST_LIST,
                cmp
            )
        ]
        assertEquals("en.or", stream.id)

        // German language
        cmp = ListHelper.getAudioTrackComparator(Locale.GERMAN, false, false)
        stream = AUDIO_TRACKS_TEST_LIST[
            ListHelper.getAudioIndexByHighestRank(
                AUDIO_TRACKS_TEST_LIST,
                cmp
            )
        ]
        assertEquals("de.du", stream.id)

        // German language, but prefer original
        cmp = ListHelper.getAudioTrackComparator(Locale.GERMAN, true, false)
        stream = AUDIO_TRACKS_TEST_LIST[
            ListHelper.getAudioIndexByHighestRank(
                AUDIO_TRACKS_TEST_LIST,
                cmp
            )
        ]
        assertEquals("en.or", stream.id)

        // Prefer descriptive audio
        cmp = ListHelper.getAudioTrackComparator(Locale.ENGLISH, false, true)
        stream = AUDIO_TRACKS_TEST_LIST[
            ListHelper.getAudioIndexByHighestRank(
                AUDIO_TRACKS_TEST_LIST,
                cmp
            )
        ]
        assertEquals("en.ds", stream.id)

        // Japanese language, fall back to original
        cmp = ListHelper.getAudioTrackComparator(Locale.JAPANESE, true, false)
        stream = AUDIO_TRACKS_TEST_LIST[
            ListHelper.getAudioIndexByHighestRank(
                AUDIO_TRACKS_TEST_LIST,
                cmp
            )
        ]
        assertEquals("en.or", stream.id)
    }

    @Test
    fun getVideoDefaultStreamIndexCombinations() {
        val testList = listOf(
            generateVideoStream("mpeg_4-1080", MediaFormat.MPEG_4, "1080p", false),
            generateVideoStream("mpeg_4-720_60", MediaFormat.MPEG_4, "720p60", false),
            generateVideoStream("mpeg_4-720", MediaFormat.MPEG_4, "720p", false),
            generateVideoStream("webm-480", MediaFormat.WEBM, "480p", false),
            generateVideoStream("mpeg_4-360", MediaFormat.MPEG_4, "360p", false),
            generateVideoStream("webm-360", MediaFormat.WEBM, "360p", false),
            generateVideoStream("v3gpp-240_60", MediaFormat.v3GPP, "240p60", false),
            generateVideoStream("webm-144", MediaFormat.WEBM, "144p", false)
        )

        // exact matches
        assertEquals(1, ListHelper.getVideoStreamIndex("720p60", MediaFormat.MPEG_4, testList))
        assertEquals(2, ListHelper.getVideoStreamIndex("720p", MediaFormat.MPEG_4, testList))

        // match but not refresh
        assertEquals(0, ListHelper.getVideoStreamIndex("1080p60", MediaFormat.MPEG_4, testList))
        assertEquals(6, ListHelper.getVideoStreamIndex("240p", MediaFormat.v3GPP, testList))

        // match but not format
        assertEquals(1, ListHelper.getVideoStreamIndex("720p60", MediaFormat.WEBM, testList))
        assertEquals(2, ListHelper.getVideoStreamIndex("720p", MediaFormat.WEBM, testList))
        assertEquals(1, ListHelper.getVideoStreamIndex("720p60", null, testList))
        assertEquals(2, ListHelper.getVideoStreamIndex("720p", null, testList))

        // match but not format and not refresh
        assertEquals(0, ListHelper.getVideoStreamIndex("1080p60", MediaFormat.WEBM, testList))
        assertEquals(6, ListHelper.getVideoStreamIndex("240p", MediaFormat.WEBM, testList))
        assertEquals(0, ListHelper.getVideoStreamIndex("1080p60", null, testList))
        assertEquals(6, ListHelper.getVideoStreamIndex("240p", null, testList))

        // match closest lower resolution
        assertEquals(7, ListHelper.getVideoStreamIndex("200p", MediaFormat.WEBM, testList))
        assertEquals(7, ListHelper.getVideoStreamIndex("200p60", MediaFormat.WEBM, testList))
        assertEquals(7, ListHelper.getVideoStreamIndex("200p", MediaFormat.MPEG_4, testList))
        assertEquals(7, ListHelper.getVideoStreamIndex("200p60", MediaFormat.MPEG_4, testList))
        assertEquals(7, ListHelper.getVideoStreamIndex("200p", null, testList))
        assertEquals(7, ListHelper.getVideoStreamIndex("200p60", null, testList))

        // Can't find a match
        assertEquals(-1, ListHelper.getVideoStreamIndex("100p", null, testList))
    }

    companion object {
        private const val BEST_RESOLUTION_KEY = "best_resolution"
        private val AUDIO_STREAMS_TEST_LIST = listOf(
            generateAudioStream("m4a-128-1", MediaFormat.M4A, 128),
            generateAudioStream("webma-192", MediaFormat.WEBMA, 192),
            generateAudioStream("mp3-64", MediaFormat.MP3, 64),
            generateAudioStream("webma-192", MediaFormat.WEBMA, 192),
            generateAudioStream("m4a-128-2", MediaFormat.M4A, 128),
            generateAudioStream("mp3-128", MediaFormat.MP3, 128),
            generateAudioStream("webma-64", MediaFormat.WEBMA, 64),
            generateAudioStream("m4a-320", MediaFormat.M4A, 320),
            generateAudioStream("mp3-192", MediaFormat.MP3, 192),
            generateAudioStream("webma-320", MediaFormat.WEBMA, 320)
        )

        private val AUDIO_TRACKS_TEST_LIST = listOf(
            generateAudioTrack("en.or", "en.or", Locale.ENGLISH, AudioTrackType.ORIGINAL),
            generateAudioTrack("en.du", "en.du", Locale.ENGLISH, AudioTrackType.DUBBED),
            generateAudioTrack("en.ds", "en.ds", Locale.ENGLISH, AudioTrackType.DESCRIPTIVE),
            generateAudioTrack("unknown", null, null, null),
            generateAudioTrack("de.du", "de.du", Locale.GERMAN, AudioTrackType.DUBBED),
            generateAudioTrack("de.ds", "de.ds", Locale.GERMAN, AudioTrackType.DESCRIPTIVE)
        )

        private val VIDEO_STREAMS_TEST_LIST = listOf(
            generateVideoStream("mpeg_4-720", MediaFormat.MPEG_4, "720p", false),
            generateVideoStream("v3gpp-240", MediaFormat.v3GPP, "240p", false),
            generateVideoStream("webm-480", MediaFormat.WEBM, "480p", false),
            generateVideoStream("v3gpp-144", MediaFormat.v3GPP, "144p", false),
            generateVideoStream("mpeg_4-360", MediaFormat.MPEG_4, "360p", false),
            generateVideoStream("webm-360", MediaFormat.WEBM, "360p", false)
        )

        private val VIDEO_ONLY_STREAMS_TEST_LIST = listOf(
            generateVideoStream("mpeg_4-720-1", MediaFormat.MPEG_4, "720p", true),
            generateVideoStream("mpeg_4-720-2", MediaFormat.MPEG_4, "720p", true),
            generateVideoStream("mpeg_4-2160", MediaFormat.MPEG_4, "2160p", true),
            generateVideoStream("mpeg_4-1440_60", MediaFormat.MPEG_4, "1440p60", true),
            generateVideoStream("webm-720_60", MediaFormat.WEBM, "720p60", true),
            generateVideoStream("mpeg_4-2160_60", MediaFormat.MPEG_4, "2160p60", true),
            generateVideoStream("mpeg_4-720_60", MediaFormat.MPEG_4, "720p60", true),
            generateVideoStream("mpeg_4-1080", MediaFormat.MPEG_4, "1080p", true),
            generateVideoStream("mpeg_4-1080_60", MediaFormat.MPEG_4, "1080p60", true)
        )

        private fun generateAudioStream(
            id: String,
            mediaFormat: MediaFormat?,
            averageBitrate: Int
        ): AudioStream {
            return AudioStream.Builder()
                .setId(id)
                .setContent("", true)
                .setMediaFormat(mediaFormat)
                .setAverageBitrate(averageBitrate)
                .build()
        }

        private fun generateAudioTrack(
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

        private fun generateVideoStream(
            id: String,
            mediaFormat: MediaFormat?,
            resolution: String,
            isVideoOnly: Boolean
        ): VideoStream {
            return VideoStream.Builder()
                .setId(id)
                .setContent("", true)
                .setIsVideoOnly(isVideoOnly)
                .setResolution(resolution)
                .setMediaFormat(mediaFormat)
                .build()
        }
    }
}
