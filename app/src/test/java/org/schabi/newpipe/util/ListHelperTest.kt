package org.schabi.newpipe.util

import org.junit.Assert
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.util.ListHelper.getAudioFormatComparator
import org.schabi.newpipe.util.ListHelper.getAudioIndexByHighestRank
import org.schabi.newpipe.util.ListHelper.getAudioTrackComparator
import org.schabi.newpipe.util.ListHelper.getDefaultResolutionIndex
import org.schabi.newpipe.util.ListHelper.getSortedStreamVideosList
import org.schabi.newpipe.util.ListHelper.getVideoStreamIndex
import java.util.Locale

class ListHelperTest {
    @get:Test
    val sortedStreamVideosListTest: Unit
        get() {
            var result = getSortedStreamVideosList(MediaFormat.MPEG_4, true,
                    VIDEO_STREAMS_TEST_LIST, VIDEO_ONLY_STREAMS_TEST_LIST, true, false)
            var expected: List<String?> = listOf("144p", "240p", "360p", "480p", "720p", "720p60",
                    "1080p", "1080p60", "1440p60", "2160p", "2160p60")
            Assert.assertEquals(expected.size.toLong(), result.size.toLong())
            for (i in result.indices) {
                Assert.assertEquals(result[i]!!.getResolution(), expected[i])
                Assert.assertEquals(expected[i], result[i]!!.getResolution())
            }

            ////////////////////
            // Reverse Order //
            //////////////////
            result = getSortedStreamVideosList(MediaFormat.MPEG_4, true,
                    VIDEO_STREAMS_TEST_LIST, VIDEO_ONLY_STREAMS_TEST_LIST, false, false)
            expected = listOf("2160p60", "2160p", "1440p60", "1080p60", "1080p", "720p60",
                    "720p", "480p", "360p", "240p", "144p")
            Assert.assertEquals(expected.size.toLong(), result.size.toLong())
            for (i in result.indices) {
                Assert.assertEquals(expected[i], result[i]!!.getResolution())
            }
        }

    @get:Test
    val sortedStreamVideosListWithPreferVideoOnlyStreamsTest: Unit
        get() {
            var result = getSortedStreamVideosList(MediaFormat.MPEG_4, true,
                    null, VIDEO_ONLY_STREAMS_TEST_LIST, true, true)
            var expected: List<String?> = listOf("720p", "720p60", "1080p", "1080p60", "1440p60", "2160p", "2160p60")
            Assert.assertEquals(expected.size.toLong(), result.size.toLong())
            for (i in result.indices) {
                Assert.assertEquals(expected[i], result[i]!!.getResolution())
                Assert.assertTrue(result[i]!!.isVideoOnly())
            }

            //////////////////////////////////////////////////////////
            // No video only streams -> should return mixed streams //
            //////////////////////////////////////////////////////////
            result = getSortedStreamVideosList(MediaFormat.MPEG_4, true,
                    VIDEO_STREAMS_TEST_LIST, null, false, true)
            expected = listOf("720p", "480p", "360p", "240p", "144p")
            Assert.assertEquals(expected.size.toLong(), result.size.toLong())
            for (i in result.indices) {
                Assert.assertEquals(expected[i], result[i]!!.getResolution())
                Assert.assertFalse(result[i]!!.isVideoOnly())
            }

            /////////////////////////////////////////////////////////////////
            // Both types of  streams -> should return correct one streams //
            /////////////////////////////////////////////////////////////////
            result = getSortedStreamVideosList(MediaFormat.MPEG_4, true,
                    VIDEO_STREAMS_TEST_LIST, VIDEO_ONLY_STREAMS_TEST_LIST, true, true)
            expected = listOf("144p", "240p", "360p", "480p", "720p", "720p60",
                    "1080p", "1080p60", "1440p60", "2160p", "2160p60")
            val expectedVideoOnly = listOf("720p", "720p60", "1080p", "1080p60", "1440p60", "2160p", "2160p60")
            Assert.assertEquals(expected.size.toLong(), result.size.toLong())
            for (i in result.indices) {
                Assert.assertEquals(expected[i], result[i]!!.getResolution())
                Assert.assertEquals(expectedVideoOnly.contains(result[i]!!.getResolution()),
                        result[i]!!.isVideoOnly())
            }
        }

    @get:Test
    val sortedStreamVideosExceptHighResolutionsTest: Unit
        get() {
            ////////////////////////////////////
            // Don't show Higher resolutions //
            //////////////////////////////////
            val result = getSortedStreamVideosList(MediaFormat.MPEG_4,
                    false, VIDEO_STREAMS_TEST_LIST, VIDEO_ONLY_STREAMS_TEST_LIST, false, false)
            val expected = listOf(
                    "1080p60", "1080p", "720p60", "720p", "480p", "360p", "240p", "144p")
            Assert.assertEquals(expected.size.toLong(), result.size.toLong())
            for (i in result.indices) {
                Assert.assertEquals(expected[i], result[i]!!.getResolution())
            }
        }

    @get:Test
    val defaultResolutionTest: Unit
        get() {
            val testList: List<VideoStream?> = ArrayList(java.util.List.of(
                    generateVideoStream("mpeg_4-720", MediaFormat.MPEG_4, "720p", false),
                    generateVideoStream("v3gpp-240", MediaFormat.v3GPP, "240p", false),
                    generateVideoStream("webm-480", MediaFormat.WEBM, "480p", false),
                    generateVideoStream("webm-240", MediaFormat.WEBM, "240p", false),
                    generateVideoStream("mpeg_4-240", MediaFormat.MPEG_4, "240p", false),
                    generateVideoStream("webm-144", MediaFormat.WEBM, "144p", false),
                    generateVideoStream("mpeg_4-360", MediaFormat.MPEG_4, "360p", false),
                    generateVideoStream("webm-360", MediaFormat.WEBM, "360p", false)))
            var result = testList[getDefaultResolutionIndex(
                    "720p", BEST_RESOLUTION_KEY, MediaFormat.MPEG_4, testList)]
            Assert.assertEquals("720p", result!!.getResolution())
            Assert.assertEquals(MediaFormat.MPEG_4, result.format)

            // Have resolution and the format
            result = testList[getDefaultResolutionIndex(
                    "480p", BEST_RESOLUTION_KEY, MediaFormat.WEBM, testList)]
            Assert.assertEquals("480p", result!!.getResolution())
            Assert.assertEquals(MediaFormat.WEBM, result.format)

            // Have resolution but not the format
            result = testList[getDefaultResolutionIndex(
                    "480p", BEST_RESOLUTION_KEY, MediaFormat.MPEG_4, testList)]
            Assert.assertEquals("480p", result!!.getResolution())
            Assert.assertEquals(MediaFormat.WEBM, result.format)

            // Have resolution and the format
            result = testList[getDefaultResolutionIndex(
                    "240p", BEST_RESOLUTION_KEY, MediaFormat.WEBM, testList)]
            Assert.assertEquals("240p", result!!.getResolution())
            Assert.assertEquals(MediaFormat.WEBM, result.format)

            // The best resolution
            result = testList[getDefaultResolutionIndex(
                    BEST_RESOLUTION_KEY, BEST_RESOLUTION_KEY, MediaFormat.WEBM, testList)]
            Assert.assertEquals("720p", result!!.getResolution())
            Assert.assertEquals(MediaFormat.MPEG_4, result.format)

            // Doesn't have the 60fps variant and format
            result = testList[getDefaultResolutionIndex(
                    "720p60", BEST_RESOLUTION_KEY, MediaFormat.WEBM, testList)]
            Assert.assertEquals("720p", result!!.getResolution())
            Assert.assertEquals(MediaFormat.MPEG_4, result.format)

            // Doesn't have the 60fps variant
            result = testList[getDefaultResolutionIndex(
                    "480p60", BEST_RESOLUTION_KEY, MediaFormat.WEBM, testList)]
            Assert.assertEquals("480p", result!!.getResolution())
            Assert.assertEquals(MediaFormat.WEBM, result.format)

            // Doesn't have the resolution, will return the best one
            result = testList[getDefaultResolutionIndex(
                    "2160p60", BEST_RESOLUTION_KEY, MediaFormat.WEBM, testList)]
            Assert.assertEquals("720p", result!!.getResolution())
            Assert.assertEquals(MediaFormat.MPEG_4, result.format)
        }

    @get:Test
    val highestQualityAudioFormatTest: Unit
        get() {
            var cmp = getAudioFormatComparator(MediaFormat.M4A, false)
            var stream = AUDIO_STREAMS_TEST_LIST[getAudioIndexByHighestRank(
                    AUDIO_STREAMS_TEST_LIST, cmp)]
            Assert.assertEquals(320, stream!!.averageBitrate.toLong())
            Assert.assertEquals(MediaFormat.M4A, stream.format)
            cmp = getAudioFormatComparator(MediaFormat.WEBMA, false)
            stream = AUDIO_STREAMS_TEST_LIST[getAudioIndexByHighestRank(
                    AUDIO_STREAMS_TEST_LIST, cmp)]
            Assert.assertEquals(320, stream!!.averageBitrate.toLong())
            Assert.assertEquals(MediaFormat.WEBMA, stream.format)
            cmp = getAudioFormatComparator(MediaFormat.MP3, false)
            stream = AUDIO_STREAMS_TEST_LIST[getAudioIndexByHighestRank(
                    AUDIO_STREAMS_TEST_LIST, cmp)]
            Assert.assertEquals(192, stream!!.averageBitrate.toLong())
            Assert.assertEquals(MediaFormat.MP3, stream.format)
        }

    @get:Test
    val highestQualityAudioFormatPreferredAbsent: Unit
        get() {
            val cmp = getAudioFormatComparator(MediaFormat.MP3, false)

            //////////////////////////////////////////
            // Doesn't contain the preferred format //
            ////////////////////////////////////////
            var testList = java.util.List.of(
                    generateAudioStream("m4a-128", MediaFormat.M4A, 128),
                    generateAudioStream("webma-192", MediaFormat.WEBMA, 192))
            // List doesn't contains this format
            // It should fallback to the highest bitrate audio no matter what format it is
            var stream = testList[getAudioIndexByHighestRank(testList, cmp)]
            Assert.assertEquals(192, stream!!.averageBitrate.toLong())
            Assert.assertEquals(MediaFormat.WEBMA, stream.format)

            ////////////////////////////////////////////////////////
            // Multiple not-preferred-formats and equal bitrates //
            //////////////////////////////////////////////////////
            testList = ArrayList(java.util.List.of(
                    generateAudioStream("webma-192-1", MediaFormat.WEBMA, 192),
                    generateAudioStream("m4a-192-1", MediaFormat.M4A, 192),
                    generateAudioStream("webma-192-2", MediaFormat.WEBMA, 192),
                    generateAudioStream("m4a-192-2", MediaFormat.M4A, 192),
                    generateAudioStream("webma-192-3", MediaFormat.WEBMA, 192),
                    generateAudioStream("m4a-192-3", MediaFormat.M4A, 192),
                    generateAudioStream("webma-192-4", MediaFormat.WEBMA, 192)))
            // List doesn't contains this format, it should fallback to the highest bitrate audio and
            // the highest quality format.
            stream = testList[getAudioIndexByHighestRank(testList, cmp)]
            Assert.assertEquals(192, stream!!.averageBitrate.toLong())
            Assert.assertEquals(MediaFormat.M4A, stream.format)

            // Adding a new format and bitrate. Adding another stream will have no impact since
            // it's not a preferred format.
            testList.add(generateAudioStream("webma-192-5", MediaFormat.WEBMA, 192))
            stream = testList[getAudioIndexByHighestRank(testList, cmp)]
            Assert.assertEquals(192, stream!!.averageBitrate.toLong())
            Assert.assertEquals(MediaFormat.M4A, stream.format)
        }

    @get:Test
    val highestQualityAudioNull: Unit
        get() {
            val cmp = getAudioFormatComparator(null, false)
            Assert.assertEquals(-1, getAudioIndexByHighestRank(null, cmp).toLong())
            Assert.assertEquals(-1, getAudioIndexByHighestRank(ArrayList(), cmp).toLong())
        }

    @get:Test
    val lowestQualityAudioFormatTest: Unit
        get() {
            var cmp = getAudioFormatComparator(MediaFormat.M4A, true)
            var stream = AUDIO_STREAMS_TEST_LIST[getAudioIndexByHighestRank(
                    AUDIO_STREAMS_TEST_LIST, cmp)]
            Assert.assertEquals(128, stream!!.averageBitrate.toLong())
            Assert.assertEquals(MediaFormat.M4A, stream.format)
            cmp = getAudioFormatComparator(MediaFormat.WEBMA, true)
            stream = AUDIO_STREAMS_TEST_LIST[getAudioIndexByHighestRank(
                    AUDIO_STREAMS_TEST_LIST, cmp)]
            Assert.assertEquals(64, stream!!.averageBitrate.toLong())
            Assert.assertEquals(MediaFormat.WEBMA, stream.format)
            cmp = getAudioFormatComparator(MediaFormat.MP3, true)
            stream = AUDIO_STREAMS_TEST_LIST[getAudioIndexByHighestRank(
                    AUDIO_STREAMS_TEST_LIST, cmp)]
            Assert.assertEquals(64, stream!!.averageBitrate.toLong())
            Assert.assertEquals(MediaFormat.MP3, stream.format)
        }

    @get:Test
    val lowestQualityAudioFormatPreferredAbsent: Unit
        get() {
            var cmp = getAudioFormatComparator(MediaFormat.MP3, true)

            //////////////////////////////////////////
            // Doesn't contain the preferred format //
            ////////////////////////////////////////
            var testList: MutableList<AudioStream?> = ArrayList(java.util.List.of(
                    generateAudioStream("m4a-128", MediaFormat.M4A, 128),
                    generateAudioStream("webma-192-1", MediaFormat.WEBMA, 192)))
            // List doesn't contains this format
            // It should fallback to the most compact audio no matter what format it is.
            var stream = testList[getAudioIndexByHighestRank(testList, cmp)]
            Assert.assertEquals(128, stream!!.averageBitrate.toLong())
            Assert.assertEquals(MediaFormat.M4A, stream.format)

            // WEBMA is more compact than M4A
            testList.add(generateAudioStream("webma-192-2", MediaFormat.WEBMA, 128))
            stream = testList[getAudioIndexByHighestRank(testList, cmp)]
            Assert.assertEquals(128, stream!!.averageBitrate.toLong())
            Assert.assertEquals(MediaFormat.WEBMA, stream.format)

            ////////////////////////////////////////////////////////
            // Multiple not-preferred-formats and equal bitrates //
            //////////////////////////////////////////////////////
            testList = ArrayList(java.util.List.of(
                    generateAudioStream("webma-192-1", MediaFormat.WEBMA, 192),
                    generateAudioStream("m4a-192-1", MediaFormat.M4A, 192),
                    generateAudioStream("webma-256", MediaFormat.WEBMA, 256),
                    generateAudioStream("m4a-192-2", MediaFormat.M4A, 192),
                    generateAudioStream("webma-192-2", MediaFormat.WEBMA, 192),
                    generateAudioStream("m4a-192-3", MediaFormat.M4A, 192)))
            // List doesn't contain this format
            // It should fallback to the most compact audio no matter what format it is.
            stream = testList[getAudioIndexByHighestRank(testList, cmp)]
            Assert.assertEquals(192, stream!!.averageBitrate.toLong())
            Assert.assertEquals(MediaFormat.WEBMA, stream.format)

            // Should be same as above
            cmp = getAudioFormatComparator(null, true)
            stream = testList[getAudioIndexByHighestRank(testList, cmp)]
            Assert.assertEquals(192, stream!!.averageBitrate.toLong())
            Assert.assertEquals(MediaFormat.WEBMA, stream.format)
        }

    @get:Test
    val lowestQualityAudioNull: Unit
        get() {
            val cmp = getAudioFormatComparator(null, false)
            Assert.assertEquals(-1, getAudioIndexByHighestRank(null, cmp).toLong())
            Assert.assertEquals(-1, getAudioIndexByHighestRank(ArrayList(), cmp).toLong())
        }

    @get:Test
    val audioTrack: Unit
        get() {
            // English language
            var cmp = getAudioTrackComparator(Locale.ENGLISH, false, false)
            var stream = AUDIO_TRACKS_TEST_LIST[getAudioIndexByHighestRank(
                    AUDIO_TRACKS_TEST_LIST, cmp)]
            Assert.assertEquals("en.or", stream!!.id)

            // German language
            cmp = getAudioTrackComparator(Locale.GERMAN, false, false)
            stream = AUDIO_TRACKS_TEST_LIST[getAudioIndexByHighestRank(
                    AUDIO_TRACKS_TEST_LIST, cmp)]
            Assert.assertEquals("de.du", stream!!.id)

            // German language, but prefer original
            cmp = getAudioTrackComparator(Locale.GERMAN, true, false)
            stream = AUDIO_TRACKS_TEST_LIST[getAudioIndexByHighestRank(
                    AUDIO_TRACKS_TEST_LIST, cmp)]
            Assert.assertEquals("en.or", stream!!.id)

            // Prefer descriptive audio
            cmp = getAudioTrackComparator(Locale.ENGLISH, false, true)
            stream = AUDIO_TRACKS_TEST_LIST[getAudioIndexByHighestRank(
                    AUDIO_TRACKS_TEST_LIST, cmp)]
            Assert.assertEquals("en.ds", stream!!.id)

            // Japanese language, fall back to original
            cmp = getAudioTrackComparator(Locale.JAPANESE, true, false)
            stream = AUDIO_TRACKS_TEST_LIST[getAudioIndexByHighestRank(
                    AUDIO_TRACKS_TEST_LIST, cmp)]
            Assert.assertEquals("en.or", stream!!.id)
        }

    @get:Test
    val videoDefaultStreamIndexCombinations: Unit
        get() {
            val testList = java.util.List.of(
                    generateVideoStream("mpeg_4-1080", MediaFormat.MPEG_4, "1080p", false),
                    generateVideoStream("mpeg_4-720_60", MediaFormat.MPEG_4, "720p60", false),
                    generateVideoStream("mpeg_4-720", MediaFormat.MPEG_4, "720p", false),
                    generateVideoStream("webm-480", MediaFormat.WEBM, "480p", false),
                    generateVideoStream("mpeg_4-360", MediaFormat.MPEG_4, "360p", false),
                    generateVideoStream("webm-360", MediaFormat.WEBM, "360p", false),
                    generateVideoStream("v3gpp-240_60", MediaFormat.v3GPP, "240p60", false),
                    generateVideoStream("webm-144", MediaFormat.WEBM, "144p", false))

            // exact matches
            Assert.assertEquals(1, getVideoStreamIndex("720p60", MediaFormat.MPEG_4, testList).toLong())
            Assert.assertEquals(2, getVideoStreamIndex("720p", MediaFormat.MPEG_4, testList).toLong())

            // match but not refresh
            Assert.assertEquals(0, getVideoStreamIndex("1080p60", MediaFormat.MPEG_4, testList).toLong())
            Assert.assertEquals(6, getVideoStreamIndex("240p", MediaFormat.v3GPP, testList).toLong())

            // match but not format
            Assert.assertEquals(1, getVideoStreamIndex("720p60", MediaFormat.WEBM, testList).toLong())
            Assert.assertEquals(2, getVideoStreamIndex("720p", MediaFormat.WEBM, testList).toLong())
            Assert.assertEquals(1, getVideoStreamIndex("720p60", null, testList).toLong())
            Assert.assertEquals(2, getVideoStreamIndex("720p", null, testList).toLong())

            // match but not format and not refresh
            Assert.assertEquals(0, getVideoStreamIndex("1080p60", MediaFormat.WEBM, testList).toLong())
            Assert.assertEquals(6, getVideoStreamIndex("240p", MediaFormat.WEBM, testList).toLong())
            Assert.assertEquals(0, getVideoStreamIndex("1080p60", null, testList).toLong())
            Assert.assertEquals(6, getVideoStreamIndex("240p", null, testList).toLong())

            // match closest lower resolution
            Assert.assertEquals(7, getVideoStreamIndex("200p", MediaFormat.WEBM, testList).toLong())
            Assert.assertEquals(7, getVideoStreamIndex("200p60", MediaFormat.WEBM, testList).toLong())
            Assert.assertEquals(7, getVideoStreamIndex("200p", MediaFormat.MPEG_4, testList).toLong())
            Assert.assertEquals(7, getVideoStreamIndex("200p60", MediaFormat.MPEG_4, testList).toLong())
            Assert.assertEquals(7, getVideoStreamIndex("200p", null, testList).toLong())
            Assert.assertEquals(7, getVideoStreamIndex("200p60", null, testList).toLong())

            // Can't find a match
            Assert.assertEquals(-1, getVideoStreamIndex("100p", null, testList).toLong())
        }

    companion object {
        private const val BEST_RESOLUTION_KEY = "best_resolution"
        private val AUDIO_STREAMS_TEST_LIST = java.util.List.of(
                generateAudioStream("m4a-128-1", MediaFormat.M4A, 128),
                generateAudioStream("webma-192", MediaFormat.WEBMA, 192),
                generateAudioStream("mp3-64", MediaFormat.MP3, 64),
                generateAudioStream("webma-192", MediaFormat.WEBMA, 192),
                generateAudioStream("m4a-128-2", MediaFormat.M4A, 128),
                generateAudioStream("mp3-128", MediaFormat.MP3, 128),
                generateAudioStream("webma-64", MediaFormat.WEBMA, 64),
                generateAudioStream("m4a-320", MediaFormat.M4A, 320),
                generateAudioStream("mp3-192", MediaFormat.MP3, 192),
                generateAudioStream("webma-320", MediaFormat.WEBMA, 320))
        private val AUDIO_TRACKS_TEST_LIST = java.util.List.of(
                generateAudioTrack("en.or", "en.or", Locale.ENGLISH, AudioTrackType.ORIGINAL),
                generateAudioTrack("en.du", "en.du", Locale.ENGLISH, AudioTrackType.DUBBED),
                generateAudioTrack("en.ds", "en.ds", Locale.ENGLISH, AudioTrackType.DESCRIPTIVE),
                generateAudioTrack("unknown", null, null, null),
                generateAudioTrack("de.du", "de.du", Locale.GERMAN, AudioTrackType.DUBBED),
                generateAudioTrack("de.ds", "de.ds", Locale.GERMAN, AudioTrackType.DESCRIPTIVE)
        )
        private val VIDEO_STREAMS_TEST_LIST = java.util.List.of(
                generateVideoStream("mpeg_4-720", MediaFormat.MPEG_4, "720p", false),
                generateVideoStream("v3gpp-240", MediaFormat.v3GPP, "240p", false),
                generateVideoStream("webm-480", MediaFormat.WEBM, "480p", false),
                generateVideoStream("v3gpp-144", MediaFormat.v3GPP, "144p", false),
                generateVideoStream("mpeg_4-360", MediaFormat.MPEG_4, "360p", false),
                generateVideoStream("webm-360", MediaFormat.WEBM, "360p", false))
        private val VIDEO_ONLY_STREAMS_TEST_LIST = java.util.List.of(
                generateVideoStream("mpeg_4-720-1", MediaFormat.MPEG_4, "720p", true),
                generateVideoStream("mpeg_4-720-2", MediaFormat.MPEG_4, "720p", true),
                generateVideoStream("mpeg_4-2160", MediaFormat.MPEG_4, "2160p", true),
                generateVideoStream("mpeg_4-1440_60", MediaFormat.MPEG_4, "1440p60", true),
                generateVideoStream("webm-720_60", MediaFormat.WEBM, "720p60", true),
                generateVideoStream("mpeg_4-2160_60", MediaFormat.MPEG_4, "2160p60", true),
                generateVideoStream("mpeg_4-720_60", MediaFormat.MPEG_4, "720p60", true),
                generateVideoStream("mpeg_4-1080", MediaFormat.MPEG_4, "1080p", true),
                generateVideoStream("mpeg_4-1080_60", MediaFormat.MPEG_4, "1080p60", true))

        private fun generateAudioStream(id: String,
                                        mediaFormat: MediaFormat?,
                                        averageBitrate: Int): AudioStream {
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
                trackType: AudioTrackType?): AudioStream {
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

        private fun generateVideoStream(id: String,
                                        mediaFormat: MediaFormat?,
                                        resolution: String,
                                        isVideoOnly: Boolean): VideoStream {
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
