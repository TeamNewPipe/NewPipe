package org.schabi.newpipe.util;

import org.junit.Test;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.AudioTrackType;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ListHelperTest {
    private static final String BEST_RESOLUTION_KEY = "best_resolution";
    private static final List<AudioStream> AUDIO_STREAMS_TEST_LIST = List.of(
            generateAudioStream("m4a-128-1", MediaFormat.M4A, 128),
            generateAudioStream("webma-192", MediaFormat.WEBMA, 192),
            generateAudioStream("mp3-64", MediaFormat.MP3, 64),
            generateAudioStream("webma-192", MediaFormat.WEBMA, 192),
            generateAudioStream("m4a-128-2", MediaFormat.M4A, 128),
            generateAudioStream("mp3-128", MediaFormat.MP3, 128),
            generateAudioStream("webma-64", MediaFormat.WEBMA, 64),
            generateAudioStream("m4a-320", MediaFormat.M4A, 320),
            generateAudioStream("mp3-192", MediaFormat.MP3, 192),
            generateAudioStream("webma-320", MediaFormat.WEBMA, 320));

    private static final List<AudioStream> AUDIO_TRACKS_TEST_LIST = List.of(
            generateAudioTrack("en.or", "en.or", Locale.ENGLISH, AudioTrackType.ORIGINAL),
            generateAudioTrack("en.du", "en.du", Locale.ENGLISH, AudioTrackType.DUBBED),
            generateAudioTrack("en.ds", "en.ds", Locale.ENGLISH, AudioTrackType.DESCRIPTIVE),
            generateAudioTrack("unknown", null, null, null),
            generateAudioTrack("de.du", "de.du", Locale.GERMAN, AudioTrackType.DUBBED),
            generateAudioTrack("de.ds", "de.ds", Locale.GERMAN, AudioTrackType.DESCRIPTIVE)
    );

    private static final List<VideoStream> VIDEO_STREAMS_TEST_LIST = List.of(
            generateVideoStream("mpeg_4-720", MediaFormat.MPEG_4, "720p", false),
            generateVideoStream("v3gpp-240", MediaFormat.v3GPP, "240p", false),
            generateVideoStream("webm-480", MediaFormat.WEBM, "480p", false),
            generateVideoStream("v3gpp-144", MediaFormat.v3GPP, "144p", false),
            generateVideoStream("mpeg_4-360", MediaFormat.MPEG_4, "360p", false),
            generateVideoStream("webm-360", MediaFormat.WEBM, "360p", false));

    private static final List<VideoStream> VIDEO_ONLY_STREAMS_TEST_LIST = List.of(
            generateVideoStream("mpeg_4-720-1", MediaFormat.MPEG_4, "720p", true),
            generateVideoStream("mpeg_4-720-2", MediaFormat.MPEG_4, "720p", true),
            generateVideoStream("mpeg_4-2160", MediaFormat.MPEG_4, "2160p", true),
            generateVideoStream("mpeg_4-1440_60", MediaFormat.MPEG_4, "1440p60", true),
            generateVideoStream("webm-720_60", MediaFormat.WEBM, "720p60", true),
            generateVideoStream("mpeg_4-2160_60", MediaFormat.MPEG_4, "2160p60", true),
            generateVideoStream("mpeg_4-720_60", MediaFormat.MPEG_4, "720p60", true),
            generateVideoStream("mpeg_4-1080", MediaFormat.MPEG_4, "1080p", true),
            generateVideoStream("mpeg_4-1080_60", MediaFormat.MPEG_4, "1080p60", true));

    @Test
    public void getSortedStreamVideosListTest() {
        List<VideoStream> result = ListHelper.getSortedStreamVideosList(MediaFormat.MPEG_4, true,
                VIDEO_STREAMS_TEST_LIST, VIDEO_ONLY_STREAMS_TEST_LIST, true, false);

        List<String> expected = List.of("144p", "240p", "360p", "480p", "720p", "720p60",
                "1080p", "1080p60", "1440p60", "2160p", "2160p60");

        assertEquals(expected.size(), result.size());
        for (int i = 0; i < result.size(); i++) {
            assertEquals(result.get(i).getResolution(), expected.get(i));
            assertEquals(expected.get(i), result.get(i).getResolution());
        }

        ////////////////////
        // Reverse Order //
        //////////////////

        result = ListHelper.getSortedStreamVideosList(MediaFormat.MPEG_4, true,
                VIDEO_STREAMS_TEST_LIST, VIDEO_ONLY_STREAMS_TEST_LIST, false, false);
        expected = List.of("2160p60", "2160p", "1440p60", "1080p60", "1080p", "720p60",
                "720p", "480p", "360p", "240p", "144p");
        assertEquals(expected.size(), result.size());
        for (int i = 0; i < result.size(); i++) {
            assertEquals(expected.get(i), result.get(i).getResolution());
        }
    }

    @Test
    public void getSortedStreamVideosListWithPreferVideoOnlyStreamsTest() {
        List<VideoStream> result = ListHelper.getSortedStreamVideosList(MediaFormat.MPEG_4, true,
                null, VIDEO_ONLY_STREAMS_TEST_LIST, true, true);

        List<String> expected =
                List.of("720p", "720p60", "1080p", "1080p60", "1440p60", "2160p", "2160p60");

        assertEquals(expected.size(), result.size());
        for (int i = 0; i < result.size(); i++) {
            assertEquals(expected.get(i), result.get(i).getResolution());
            assertTrue(result.get(i).isVideoOnly());
        }

        //////////////////////////////////////////////////////////
        // No video only streams -> should return mixed streams //
        //////////////////////////////////////////////////////////

        result = ListHelper.getSortedStreamVideosList(MediaFormat.MPEG_4, true,
                VIDEO_STREAMS_TEST_LIST, null, false, true);
        expected = List.of("720p", "480p", "360p", "240p", "144p");
        assertEquals(expected.size(), result.size());
        for (int i = 0; i < result.size(); i++) {
            assertEquals(expected.get(i), result.get(i).getResolution());
            assertFalse(result.get(i).isVideoOnly());
        }

        /////////////////////////////////////////////////////////////////
        // Both types of  streams -> should return correct one streams //
        /////////////////////////////////////////////////////////////////

        result = ListHelper.getSortedStreamVideosList(MediaFormat.MPEG_4, true,
                VIDEO_STREAMS_TEST_LIST, VIDEO_ONLY_STREAMS_TEST_LIST, true, true);
        expected = List.of("144p", "240p", "360p", "480p", "720p", "720p60",
                "1080p", "1080p60", "1440p60", "2160p", "2160p60");
        final List<String> expectedVideoOnly =
                List.of("720p", "720p60", "1080p", "1080p60", "1440p60", "2160p", "2160p60");

        assertEquals(expected.size(), result.size());
        for (int i = 0; i < result.size(); i++) {
            assertEquals(expected.get(i), result.get(i).getResolution());
            assertEquals(expectedVideoOnly.contains(result.get(i).getResolution()),
                    result.get(i).isVideoOnly());
        }
    }

    @Test
    public void getSortedStreamVideosExceptHighResolutionsTest() {
        ////////////////////////////////////
        // Don't show Higher resolutions //
        //////////////////////////////////

        final List<VideoStream> result = ListHelper.getSortedStreamVideosList(MediaFormat.MPEG_4,
                false, VIDEO_STREAMS_TEST_LIST, VIDEO_ONLY_STREAMS_TEST_LIST, false, false);
        final List<String> expected = List.of(
                "1080p60", "1080p", "720p60", "720p", "480p", "360p", "240p", "144p");
        assertEquals(expected.size(), result.size());
        for (int i = 0; i < result.size(); i++) {
            assertEquals(expected.get(i), result.get(i).getResolution());
        }
    }

    @Test
    public void getDefaultResolutionTest() {
        final List<VideoStream> testList = new ArrayList<>(List.of(
                generateVideoStream("mpeg_4-720", MediaFormat.MPEG_4, "720p", false),
                generateVideoStream("v3gpp-240", MediaFormat.v3GPP, "240p", false),
                generateVideoStream("webm-480",  MediaFormat.WEBM, "480p", false),
                generateVideoStream("webm-240", MediaFormat.WEBM, "240p", false),
                generateVideoStream("mpeg_4-240", MediaFormat.MPEG_4, "240p", false),
                generateVideoStream("webm-144", MediaFormat.WEBM, "144p", false),
                generateVideoStream("mpeg_4-360", MediaFormat.MPEG_4, "360p", false),
                generateVideoStream("webm-360", MediaFormat.WEBM, "360p", false)));
        VideoStream result = testList.get(ListHelper.getDefaultResolutionIndex(
                "720p", BEST_RESOLUTION_KEY, MediaFormat.MPEG_4, testList));
        assertEquals("720p", result.getResolution());
        assertEquals(MediaFormat.MPEG_4, result.getFormat());

        // Have resolution and the format
        result = testList.get(ListHelper.getDefaultResolutionIndex(
                "480p", BEST_RESOLUTION_KEY, MediaFormat.WEBM, testList));
        assertEquals("480p", result.getResolution());
        assertEquals(MediaFormat.WEBM, result.getFormat());

        // Have resolution but not the format
        result = testList.get(ListHelper.getDefaultResolutionIndex(
                "480p", BEST_RESOLUTION_KEY, MediaFormat.MPEG_4, testList));
        assertEquals("480p", result.getResolution());
        assertEquals(MediaFormat.WEBM, result.getFormat());

        // Have resolution and the format
        result = testList.get(ListHelper.getDefaultResolutionIndex(
                "240p", BEST_RESOLUTION_KEY, MediaFormat.WEBM, testList));
        assertEquals("240p", result.getResolution());
        assertEquals(MediaFormat.WEBM, result.getFormat());

        // The best resolution
        result = testList.get(ListHelper.getDefaultResolutionIndex(
                BEST_RESOLUTION_KEY, BEST_RESOLUTION_KEY, MediaFormat.WEBM, testList));
        assertEquals("720p", result.getResolution());
        assertEquals(MediaFormat.MPEG_4, result.getFormat());

        // Doesn't have the 60fps variant and format
        result = testList.get(ListHelper.getDefaultResolutionIndex(
                "720p60", BEST_RESOLUTION_KEY, MediaFormat.WEBM, testList));
        assertEquals("720p", result.getResolution());
        assertEquals(MediaFormat.MPEG_4, result.getFormat());

        // Doesn't have the 60fps variant
        result = testList.get(ListHelper.getDefaultResolutionIndex(
                "480p60", BEST_RESOLUTION_KEY, MediaFormat.WEBM, testList));
        assertEquals("480p", result.getResolution());
        assertEquals(MediaFormat.WEBM, result.getFormat());

        // Doesn't have the resolution, will return the best one
        result = testList.get(ListHelper.getDefaultResolutionIndex(
                "2160p60", BEST_RESOLUTION_KEY, MediaFormat.WEBM, testList));
        assertEquals("720p", result.getResolution());
        assertEquals(MediaFormat.MPEG_4, result.getFormat());
    }

    @Test
    public void getHighestQualityAudioFormatTest() {
        Comparator<AudioStream> cmp = ListHelper.getAudioFormatComparator(MediaFormat.M4A, false);
        AudioStream stream = AUDIO_STREAMS_TEST_LIST.get(ListHelper.getAudioIndexByHighestRank(
                        AUDIO_STREAMS_TEST_LIST, cmp));
        assertEquals(320, stream.getAverageBitrate());
        assertEquals(MediaFormat.M4A, stream.getFormat());

        cmp = ListHelper.getAudioFormatComparator(MediaFormat.WEBMA, false);
        stream = AUDIO_STREAMS_TEST_LIST.get(ListHelper.getAudioIndexByHighestRank(
                AUDIO_STREAMS_TEST_LIST, cmp));
        assertEquals(320, stream.getAverageBitrate());
        assertEquals(MediaFormat.WEBMA, stream.getFormat());

        cmp = ListHelper.getAudioFormatComparator(MediaFormat.MP3, false);
        stream = AUDIO_STREAMS_TEST_LIST.get(ListHelper.getAudioIndexByHighestRank(
                AUDIO_STREAMS_TEST_LIST, cmp));
        assertEquals(192, stream.getAverageBitrate());
        assertEquals(MediaFormat.MP3, stream.getFormat());
    }

    @Test
    public void getHighestQualityAudioFormatPreferredAbsent() {
        final Comparator<AudioStream> cmp =
                ListHelper.getAudioFormatComparator(MediaFormat.MP3, false);

        //////////////////////////////////////////
        // Doesn't contain the preferred format //
        ////////////////////////////////////////

        List<AudioStream> testList = List.of(
                generateAudioStream("m4a-128", MediaFormat.M4A, 128),
                generateAudioStream("webma-192", MediaFormat.WEBMA, 192));
        // List doesn't contains this format
        // It should fallback to the highest bitrate audio no matter what format it is
        AudioStream stream = testList.get(ListHelper.getAudioIndexByHighestRank(testList, cmp));
        assertEquals(192, stream.getAverageBitrate());
        assertEquals(MediaFormat.WEBMA, stream.getFormat());

        ////////////////////////////////////////////////////////
        // Multiple not-preferred-formats and equal bitrates //
        //////////////////////////////////////////////////////

        testList = new ArrayList<>(List.of(
                generateAudioStream("webma-192-1", MediaFormat.WEBMA, 192),
                generateAudioStream("m4a-192-1", MediaFormat.M4A, 192),
                generateAudioStream("webma-192-2", MediaFormat.WEBMA, 192),
                generateAudioStream("m4a-192-2", MediaFormat.M4A, 192),
                generateAudioStream("webma-192-3", MediaFormat.WEBMA, 192),
                generateAudioStream("m4a-192-3", MediaFormat.M4A, 192),
                generateAudioStream("webma-192-4", MediaFormat.WEBMA, 192)));
        // List doesn't contains this format, it should fallback to the highest bitrate audio and
        // the highest quality format.
        stream =
                testList.get(ListHelper.getAudioIndexByHighestRank(testList, cmp));
        assertEquals(192, stream.getAverageBitrate());
        assertEquals(MediaFormat.M4A, stream.getFormat());

        // Adding a new format and bitrate. Adding another stream will have no impact since
        // it's not a preferred format.
        testList.add(generateAudioStream("webma-192-5", MediaFormat.WEBMA, 192));
        stream =
                testList.get(ListHelper.getAudioIndexByHighestRank(testList, cmp));
        assertEquals(192, stream.getAverageBitrate());
        assertEquals(MediaFormat.M4A, stream.getFormat());
    }

    @Test
    public void getHighestQualityAudioNull() {
        final Comparator<AudioStream> cmp = ListHelper.getAudioFormatComparator(null, false);
        assertEquals(-1, ListHelper.getAudioIndexByHighestRank(null, cmp));
        assertEquals(-1, ListHelper.getAudioIndexByHighestRank(new ArrayList<>(), cmp));
    }

    @Test
    public void getLowestQualityAudioFormatTest() {
        Comparator<AudioStream> cmp = ListHelper.getAudioFormatComparator(MediaFormat.M4A, true);
        AudioStream stream = AUDIO_STREAMS_TEST_LIST.get(ListHelper.getAudioIndexByHighestRank(
                AUDIO_STREAMS_TEST_LIST, cmp));
        assertEquals(128, stream.getAverageBitrate());
        assertEquals(MediaFormat.M4A, stream.getFormat());

        cmp = ListHelper.getAudioFormatComparator(MediaFormat.WEBMA, true);
        stream = AUDIO_STREAMS_TEST_LIST.get(ListHelper.getAudioIndexByHighestRank(
                AUDIO_STREAMS_TEST_LIST, cmp));
        assertEquals(64, stream.getAverageBitrate());
        assertEquals(MediaFormat.WEBMA, stream.getFormat());

        cmp = ListHelper.getAudioFormatComparator(MediaFormat.MP3, true);
        stream = AUDIO_STREAMS_TEST_LIST.get(ListHelper.getAudioIndexByHighestRank(
                AUDIO_STREAMS_TEST_LIST, cmp));
        assertEquals(64, stream.getAverageBitrate());
        assertEquals(MediaFormat.MP3, stream.getFormat());
    }

    @Test
    public void getLowestQualityAudioFormatPreferredAbsent() {
        Comparator<AudioStream> cmp = ListHelper.getAudioFormatComparator(MediaFormat.MP3, true);

        //////////////////////////////////////////
        // Doesn't contain the preferred format //
        ////////////////////////////////////////

        List<AudioStream> testList = new ArrayList<>(List.of(
                generateAudioStream("m4a-128", MediaFormat.M4A, 128),
                generateAudioStream("webma-192-1", MediaFormat.WEBMA, 192)));
        // List doesn't contains this format
        // It should fallback to the most compact audio no matter what format it is.
        AudioStream stream = testList.get(ListHelper.getAudioIndexByHighestRank(testList, cmp));
        assertEquals(128, stream.getAverageBitrate());
        assertEquals(MediaFormat.M4A, stream.getFormat());

        // WEBMA is more compact than M4A
        testList.add(generateAudioStream("webma-192-2", MediaFormat.WEBMA, 128));
        stream = testList.get(ListHelper.getAudioIndexByHighestRank(testList, cmp));
        assertEquals(128, stream.getAverageBitrate());
        assertEquals(MediaFormat.WEBMA, stream.getFormat());

        ////////////////////////////////////////////////////////
        // Multiple not-preferred-formats and equal bitrates //
        //////////////////////////////////////////////////////

        testList = new ArrayList<>(List.of(
                generateAudioStream("webma-192-1", MediaFormat.WEBMA, 192),
                generateAudioStream("m4a-192-1",   MediaFormat.M4A, 192),
                generateAudioStream("webma-256", MediaFormat.WEBMA, 256),
                generateAudioStream("m4a-192-2", MediaFormat.M4A, 192),
                generateAudioStream("webma-192-2", MediaFormat.WEBMA, 192),
                generateAudioStream("m4a-192-3", MediaFormat.M4A, 192)));
        // List doesn't contain this format
        // It should fallback to the most compact audio no matter what format it is.
        stream = testList.get(
                ListHelper.getAudioIndexByHighestRank(testList, cmp));
        assertEquals(192, stream.getAverageBitrate());
        assertEquals(MediaFormat.WEBMA, stream.getFormat());

        // Should be same as above
        cmp = ListHelper.getAudioFormatComparator(null, true);
        stream = testList.get(
                ListHelper.getAudioIndexByHighestRank(testList, cmp));
        assertEquals(192, stream.getAverageBitrate());
        assertEquals(MediaFormat.WEBMA, stream.getFormat());
    }

    @Test
    public void getLowestQualityAudioNull() {
        final Comparator<AudioStream> cmp = ListHelper.getAudioFormatComparator(null, false);
        assertEquals(-1, ListHelper.getAudioIndexByHighestRank(null, cmp));
        assertEquals(-1, ListHelper.getAudioIndexByHighestRank(new ArrayList<>(), cmp));
    }

    @Test
    public void getAudioTrack() {
        // English language
        Comparator<AudioStream> cmp =
                ListHelper.getAudioTrackComparator(Locale.ENGLISH, false, false);
        AudioStream stream = AUDIO_TRACKS_TEST_LIST.get(ListHelper.getAudioIndexByHighestRank(
                AUDIO_TRACKS_TEST_LIST, cmp));
        assertEquals("en.or", stream.getId());

        // German language
        cmp = ListHelper.getAudioTrackComparator(Locale.GERMAN, false, false);
        stream = AUDIO_TRACKS_TEST_LIST.get(ListHelper.getAudioIndexByHighestRank(
                AUDIO_TRACKS_TEST_LIST, cmp));
        assertEquals("de.du", stream.getId());

        // German language, but prefer original
        cmp = ListHelper.getAudioTrackComparator(Locale.GERMAN, true, false);
        stream = AUDIO_TRACKS_TEST_LIST.get(ListHelper.getAudioIndexByHighestRank(
                AUDIO_TRACKS_TEST_LIST, cmp));
        assertEquals("en.or", stream.getId());

        // Prefer descriptive audio
        cmp = ListHelper.getAudioTrackComparator(Locale.ENGLISH, false, true);
        stream = AUDIO_TRACKS_TEST_LIST.get(ListHelper.getAudioIndexByHighestRank(
                AUDIO_TRACKS_TEST_LIST, cmp));
        assertEquals("en.ds", stream.getId());

        // Japanese language, fall back to original
        cmp = ListHelper.getAudioTrackComparator(Locale.JAPANESE, true, false);
        stream = AUDIO_TRACKS_TEST_LIST.get(ListHelper.getAudioIndexByHighestRank(
                AUDIO_TRACKS_TEST_LIST, cmp));
        assertEquals("en.or", stream.getId());
    }

    @Test
    public void getVideoDefaultStreamIndexCombinations() {
        final List<VideoStream> testList = List.of(
                generateVideoStream("mpeg_4-1080", MediaFormat.MPEG_4, "1080p",  false),
                generateVideoStream("mpeg_4-720_60", MediaFormat.MPEG_4, "720p60", false),
                generateVideoStream("mpeg_4-720", MediaFormat.MPEG_4, "720p",   false),
                generateVideoStream("webm-480", MediaFormat.WEBM, "480p",   false),
                generateVideoStream("mpeg_4-360", MediaFormat.MPEG_4, "360p",   false),
                generateVideoStream("webm-360", MediaFormat.WEBM, "360p",   false),
                generateVideoStream("v3gpp-240_60", MediaFormat.v3GPP, "240p60", false),
                generateVideoStream("webm-144", MediaFormat.WEBM, "144p",   false));

        // exact matches
        assertEquals(1, ListHelper.getVideoStreamIndex("720p60", MediaFormat.MPEG_4, testList));
        assertEquals(2, ListHelper.getVideoStreamIndex("720p", MediaFormat.MPEG_4, testList));

        // match but not refresh
        assertEquals(0, ListHelper.getVideoStreamIndex("1080p60", MediaFormat.MPEG_4, testList));
        assertEquals(6, ListHelper.getVideoStreamIndex("240p", MediaFormat.v3GPP, testList));

        // match but not format
        assertEquals(1, ListHelper.getVideoStreamIndex("720p60", MediaFormat.WEBM, testList));
        assertEquals(2, ListHelper.getVideoStreamIndex("720p", MediaFormat.WEBM, testList));
        assertEquals(1, ListHelper.getVideoStreamIndex("720p60", null, testList));
        assertEquals(2, ListHelper.getVideoStreamIndex("720p", null, testList));

        // match but not format and not refresh
        assertEquals(0, ListHelper.getVideoStreamIndex("1080p60", MediaFormat.WEBM, testList));
        assertEquals(6, ListHelper.getVideoStreamIndex("240p", MediaFormat.WEBM, testList));
        assertEquals(0, ListHelper.getVideoStreamIndex("1080p60", null, testList));
        assertEquals(6, ListHelper.getVideoStreamIndex("240p", null, testList));

        // match closest lower resolution
        assertEquals(7, ListHelper.getVideoStreamIndex("200p", MediaFormat.WEBM, testList));
        assertEquals(7, ListHelper.getVideoStreamIndex("200p60", MediaFormat.WEBM, testList));
        assertEquals(7, ListHelper.getVideoStreamIndex("200p", MediaFormat.MPEG_4, testList));
        assertEquals(7, ListHelper.getVideoStreamIndex("200p60", MediaFormat.MPEG_4, testList));
        assertEquals(7, ListHelper.getVideoStreamIndex("200p", null, testList));
        assertEquals(7, ListHelper.getVideoStreamIndex("200p60", null, testList));

        // Can't find a match
        assertEquals(-1, ListHelper.getVideoStreamIndex("100p", null, testList));
    }

    @NonNull
    private static AudioStream generateAudioStream(@NonNull final String id,
                                                   @Nullable final MediaFormat mediaFormat,
                                                   final int averageBitrate) {
        return new AudioStream.Builder()
                .setId(id)
                .setContent("", true)
                .setMediaFormat(mediaFormat)
                .setAverageBitrate(averageBitrate)
                .build();
    }

    private static AudioStream generateAudioTrack(
            @NonNull final String id,
            @Nullable final String trackId,
            @Nullable final Locale locale,
            @Nullable final AudioTrackType trackType) {
        return new AudioStream.Builder()
                .setId(id)
                .setContent("", true)
                .setMediaFormat(MediaFormat.M4A)
                .setAverageBitrate(128)
                .setAudioTrackId(trackId)
                .setAudioLocale(locale)
                .setAudioTrackType(trackType)
                .build();
    }

    @NonNull
    private static VideoStream generateVideoStream(@NonNull final String id,
                                                   @Nullable final MediaFormat mediaFormat,
                                                   @NonNull final String resolution,
                                                   final boolean isVideoOnly) {
        return new VideoStream.Builder()
                .setId(id)
                .setContent("", true)
                .setIsVideoOnly(isVideoOnly)
                .setResolution(resolution)
                .setMediaFormat(mediaFormat)
                .build();
    }
}
