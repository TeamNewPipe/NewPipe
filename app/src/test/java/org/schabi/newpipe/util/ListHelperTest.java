package org.schabi.newpipe.util;

import org.junit.Test;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ListHelperTest {
    private static final String BEST_RESOLUTION_KEY = "best_resolution";
    private static final List<AudioStream> AUDIO_STREAMS_TEST_LIST = Arrays.asList(
            new AudioStream("", MediaFormat.M4A,   /**/ 128),
            new AudioStream("", MediaFormat.WEBMA, /**/ 192),
            new AudioStream("", MediaFormat.MP3,   /**/ 64),
            new AudioStream("", MediaFormat.WEBMA, /**/ 192),
            new AudioStream("", MediaFormat.M4A,   /**/ 128),
            new AudioStream("", MediaFormat.MP3,   /**/ 128),
            new AudioStream("", MediaFormat.WEBMA, /**/ 64),
            new AudioStream("", MediaFormat.M4A,   /**/ 320),
            new AudioStream("", MediaFormat.MP3,   /**/ 192),
            new AudioStream("", MediaFormat.WEBMA, /**/ 320));

    private static final List<VideoStream> VIDEO_STREAMS_TEST_LIST = Arrays.asList(
            new VideoStream("", MediaFormat.MPEG_4,   /**/ "720p"),
            new VideoStream("", MediaFormat.v3GPP,    /**/ "240p"),
            new VideoStream("", MediaFormat.WEBM,     /**/ "480p"),
            new VideoStream("", MediaFormat.v3GPP,    /**/ "144p"),
            new VideoStream("", MediaFormat.MPEG_4,   /**/ "360p"),
            new VideoStream("", MediaFormat.WEBM,     /**/ "360p"));

    private static final List<VideoStream> VIDEO_ONLY_STREAMS_TEST_LIST = Arrays.asList(
            new VideoStream("", MediaFormat.MPEG_4,   /**/ "720p", true),
            new VideoStream("", MediaFormat.MPEG_4,   /**/ "720p", true),
            new VideoStream("", MediaFormat.MPEG_4,   /**/ "2160p", true),
            new VideoStream("", MediaFormat.MPEG_4,   /**/ "1440p60", true),
            new VideoStream("", MediaFormat.WEBM,     /**/ "720p60", true),
            new VideoStream("", MediaFormat.MPEG_4,   /**/ "2160p60", true),
            new VideoStream("", MediaFormat.MPEG_4,   /**/ "720p60", true),
            new VideoStream("", MediaFormat.MPEG_4,   /**/ "1080p", true),
            new VideoStream("", MediaFormat.MPEG_4,   /**/ "1080p60", true));

    @Test
    public void getSortedStreamVideosListTest() {
        List<VideoStream> result = ListHelper.getSortedStreamVideosList(MediaFormat.MPEG_4, true,
                VIDEO_STREAMS_TEST_LIST, VIDEO_ONLY_STREAMS_TEST_LIST, true);

        List<String> expected = Arrays.asList("144p", "240p", "360p", "480p", "720p", "720p60",
                "1080p", "1080p60", "1440p60", "2160p", "2160p60");
//        for (VideoStream videoStream : result) {
//            System.out.println(videoStream.resolution + " > "
//                    + MediaFormat.getSuffixById(videoStream.format) + " > "
//                    + videoStream.isVideoOnly);
//        }

        assertEquals(result.size(), expected.size());
        for (int i = 0; i < result.size(); i++) {
            assertEquals(result.get(i).resolution, expected.get(i));
        }

        ////////////////////
        // Reverse Order //
        //////////////////

        result = ListHelper.getSortedStreamVideosList(MediaFormat.MPEG_4, true,
                VIDEO_STREAMS_TEST_LIST, VIDEO_ONLY_STREAMS_TEST_LIST, false);
        expected = Arrays.asList("2160p60", "2160p", "1440p60", "1080p60", "1080p", "720p60",
                "720p", "480p", "360p", "240p", "144p");
        assertEquals(result.size(), expected.size());
        for (int i = 0; i < result.size(); i++) {
            assertEquals(result.get(i).resolution, expected.get(i));
        }
    }

    @Test
    public void getSortedStreamVideosExceptHighResolutionsTest() {
        ////////////////////////////////////
        // Don't show Higher resolutions //
        //////////////////////////////////

        List<VideoStream> result = ListHelper.getSortedStreamVideosList(MediaFormat.MPEG_4,
                false, VIDEO_STREAMS_TEST_LIST, VIDEO_ONLY_STREAMS_TEST_LIST, false);
        List<String> expected = Arrays.asList(
                "1080p60", "1080p", "720p60", "720p", "480p", "360p", "240p", "144p");
        assertEquals(result.size(), expected.size());
        for (int i = 0; i < result.size(); i++) {
            assertEquals(result.get(i).resolution, expected.get(i));
        }
    }

    @Test
    public void getDefaultResolutionTest() {
        List<VideoStream> testList = Arrays.asList(
                new VideoStream("", MediaFormat.MPEG_4,   /**/ "720p"),
                new VideoStream("", MediaFormat.v3GPP,    /**/ "240p"),
                new VideoStream("", MediaFormat.WEBM,     /**/ "480p"),
                new VideoStream("", MediaFormat.WEBM,     /**/ "240p"),
                new VideoStream("", MediaFormat.MPEG_4,   /**/ "240p"),
                new VideoStream("", MediaFormat.WEBM,     /**/ "144p"),
                new VideoStream("", MediaFormat.MPEG_4,   /**/ "360p"),
                new VideoStream("", MediaFormat.WEBM,     /**/ "360p"));
        VideoStream result = testList.get(ListHelper.getDefaultResolutionIndex(
                "720p", BEST_RESOLUTION_KEY, MediaFormat.MPEG_4, testList));
        assertEquals("720p", result.resolution);
        assertEquals(MediaFormat.MPEG_4, result.getFormat());

        // Have resolution and the format
        result = testList.get(ListHelper.getDefaultResolutionIndex(
                "480p", BEST_RESOLUTION_KEY, MediaFormat.WEBM, testList));
        assertEquals("480p", result.resolution);
        assertEquals(MediaFormat.WEBM, result.getFormat());

        // Have resolution but not the format
        result = testList.get(ListHelper.getDefaultResolutionIndex(
                "480p", BEST_RESOLUTION_KEY, MediaFormat.MPEG_4, testList));
        assertEquals("480p", result.resolution);
        assertEquals(MediaFormat.WEBM, result.getFormat());

        // Have resolution and the format
        result = testList.get(ListHelper.getDefaultResolutionIndex(
                "240p", BEST_RESOLUTION_KEY, MediaFormat.WEBM, testList));
        assertEquals("240p", result.resolution);
        assertEquals(MediaFormat.WEBM, result.getFormat());

        // The best resolution
        result = testList.get(ListHelper.getDefaultResolutionIndex(
                BEST_RESOLUTION_KEY, BEST_RESOLUTION_KEY, MediaFormat.WEBM, testList));
        assertEquals("720p", result.resolution);
        assertEquals(MediaFormat.MPEG_4, result.getFormat());

        // Doesn't have the 60fps variant and format
        result = testList.get(ListHelper.getDefaultResolutionIndex(
                "720p60", BEST_RESOLUTION_KEY, MediaFormat.WEBM, testList));
        assertEquals("720p", result.resolution);
        assertEquals(MediaFormat.MPEG_4, result.getFormat());

        // Doesn't have the 60fps variant
        result = testList.get(ListHelper.getDefaultResolutionIndex(
                "480p60", BEST_RESOLUTION_KEY, MediaFormat.WEBM, testList));
        assertEquals("480p", result.resolution);
        assertEquals(MediaFormat.WEBM, result.getFormat());

        // Doesn't have the resolution, will return the best one
        result = testList.get(ListHelper.getDefaultResolutionIndex(
                "2160p60", BEST_RESOLUTION_KEY, MediaFormat.WEBM, testList));
        assertEquals("720p", result.resolution);
        assertEquals(MediaFormat.MPEG_4, result.getFormat());
    }

    @Test
    public void getHighestQualityAudioFormatTest() {
        AudioStream stream = AUDIO_STREAMS_TEST_LIST.get(ListHelper.getHighestQualityAudioIndex(
                MediaFormat.M4A, AUDIO_STREAMS_TEST_LIST));
        assertEquals(320, stream.average_bitrate);
        assertEquals(MediaFormat.M4A, stream.getFormat());

        stream = AUDIO_STREAMS_TEST_LIST.get(ListHelper.getHighestQualityAudioIndex(
                MediaFormat.WEBMA, AUDIO_STREAMS_TEST_LIST));
        assertEquals(320, stream.average_bitrate);
        assertEquals(MediaFormat.WEBMA, stream.getFormat());

        stream = AUDIO_STREAMS_TEST_LIST.get(ListHelper.getHighestQualityAudioIndex(
                MediaFormat.MP3, AUDIO_STREAMS_TEST_LIST));
        assertEquals(192, stream.average_bitrate);
        assertEquals(MediaFormat.MP3, stream.getFormat());
    }

    @Test
    public void getHighestQualityAudioFormatPreferredAbsent() {

        //////////////////////////////////////////
        // Doesn't contain the preferred format //
        ////////////////////////////////////////

        List<AudioStream> testList = Arrays.asList(
                new AudioStream("", MediaFormat.M4A,   /**/ 128),
                new AudioStream("", MediaFormat.WEBMA, /**/ 192));
        // List doesn't contains this format
        // It should fallback to the highest bitrate audio no matter what format it is
        AudioStream stream = testList.get(ListHelper.getHighestQualityAudioIndex(
                MediaFormat.MP3, testList));
        assertEquals(192, stream.average_bitrate);
        assertEquals(MediaFormat.WEBMA, stream.getFormat());

        ////////////////////////////////////////////////////////
        // Multiple not-preferred-formats and equal bitrates //
        //////////////////////////////////////////////////////

        testList = new ArrayList<>(Arrays.asList(
                new AudioStream("", MediaFormat.WEBMA, /**/ 192),
                new AudioStream("", MediaFormat.M4A,   /**/ 192),
                new AudioStream("", MediaFormat.WEBMA, /**/ 192),
                new AudioStream("", MediaFormat.M4A,   /**/ 192),
                new AudioStream("", MediaFormat.WEBMA, /**/ 192),
                new AudioStream("", MediaFormat.M4A,   /**/ 192),
                new AudioStream("", MediaFormat.WEBMA, /**/ 192)));
        // List doesn't contains this format, it should fallback to the highest bitrate audio and
        // the highest quality format.
        stream = testList.get(ListHelper.getHighestQualityAudioIndex(MediaFormat.MP3, testList));
        assertEquals(192, stream.average_bitrate);
        assertEquals(MediaFormat.M4A, stream.getFormat());

        // Adding a new format and bitrate. Adding another stream will have no impact since
        // it's not a prefered format.
        testList.add(new AudioStream("", MediaFormat.WEBMA, /**/ 192));
        stream = testList.get(ListHelper.getHighestQualityAudioIndex(MediaFormat.MP3, testList));
        assertEquals(192, stream.average_bitrate);
        assertEquals(MediaFormat.M4A, stream.getFormat());
    }

    @Test
    public void getHighestQualityAudioNull() {
        assertEquals(-1, ListHelper.getHighestQualityAudioIndex(null, null));
        assertEquals(-1, ListHelper.getHighestQualityAudioIndex(null, new ArrayList<>()));
    }

    @Test
    public void getLowestQualityAudioFormatTest() {
        AudioStream stream = AUDIO_STREAMS_TEST_LIST.get(ListHelper.getMostCompactAudioIndex(
                MediaFormat.M4A, AUDIO_STREAMS_TEST_LIST));
        assertEquals(128, stream.average_bitrate);
        assertEquals(MediaFormat.M4A, stream.getFormat());

        stream = AUDIO_STREAMS_TEST_LIST.get(ListHelper.getMostCompactAudioIndex(
                MediaFormat.WEBMA, AUDIO_STREAMS_TEST_LIST));
        assertEquals(64, stream.average_bitrate);
        assertEquals(MediaFormat.WEBMA, stream.getFormat());

        stream = AUDIO_STREAMS_TEST_LIST.get(ListHelper.getMostCompactAudioIndex(
                MediaFormat.MP3, AUDIO_STREAMS_TEST_LIST));
        assertEquals(64, stream.average_bitrate);
        assertEquals(MediaFormat.MP3, stream.getFormat());
    }

    @Test
    public void getLowestQualityAudioFormatPreferredAbsent() {

        //////////////////////////////////////////
        // Doesn't contain the preferred format //
        ////////////////////////////////////////

        List<AudioStream> testList = new ArrayList<>(Arrays.asList(
                new AudioStream("", MediaFormat.M4A,   /**/ 128),
                new AudioStream("", MediaFormat.WEBMA, /**/ 192)));
        // List doesn't contains this format
        // It should fallback to the most compact audio no matter what format it is.
        AudioStream stream = testList.get(ListHelper.getMostCompactAudioIndex(
                MediaFormat.MP3, testList));
        assertEquals(128, stream.average_bitrate);
        assertEquals(MediaFormat.M4A, stream.getFormat());

        // WEBMA is more compact than M4A
        testList.add(new AudioStream("", MediaFormat.WEBMA,   /**/ 128));
        stream = testList.get(ListHelper.getMostCompactAudioIndex(MediaFormat.MP3, testList));
        assertEquals(128, stream.average_bitrate);
        assertEquals(MediaFormat.WEBMA, stream.getFormat());

        ////////////////////////////////////////////////////////
        // Multiple not-preferred-formats and equal bitrates //
        //////////////////////////////////////////////////////

        testList = new ArrayList<>(Arrays.asList(
                new AudioStream("", MediaFormat.WEBMA, /**/ 192),
                new AudioStream("", MediaFormat.M4A,   /**/ 192),
                new AudioStream("", MediaFormat.WEBMA, /**/ 256),
                new AudioStream("", MediaFormat.M4A,   /**/ 192),
                new AudioStream("", MediaFormat.WEBMA, /**/ 192),
                new AudioStream("", MediaFormat.M4A,   /**/ 192)));
        // List doesn't contain this format
        // It should fallback to the most compact audio no matter what format it is.
        stream = testList.get(ListHelper.getMostCompactAudioIndex(MediaFormat.MP3, testList));
        assertEquals(192, stream.average_bitrate);
        assertEquals(MediaFormat.WEBMA, stream.getFormat());

        // Should be same as above
        stream = testList.get(ListHelper.getMostCompactAudioIndex(null, testList));
        assertEquals(192, stream.average_bitrate);
        assertEquals(MediaFormat.WEBMA, stream.getFormat());
    }

    @Test
    public void getLowestQualityAudioNull() {
        assertEquals(-1, ListHelper.getMostCompactAudioIndex(null, null));
        assertEquals(-1, ListHelper.getMostCompactAudioIndex(null, new ArrayList<>()));
    }

    @Test
    public void getVideoDefaultStreamIndexCombinations() {
        List<VideoStream> testList = Arrays.asList(
                new VideoStream("", MediaFormat.MPEG_4,   /**/ "1080p"),
                new VideoStream("", MediaFormat.MPEG_4,   /**/ "720p60"),
                new VideoStream("", MediaFormat.MPEG_4,   /**/ "720p"),
                new VideoStream("", MediaFormat.WEBM,     /**/ "480p"),
                new VideoStream("", MediaFormat.MPEG_4,   /**/ "360p"),
                new VideoStream("", MediaFormat.WEBM,     /**/ "360p"),
                new VideoStream("", MediaFormat.v3GPP,    /**/ "240p60"),
                new VideoStream("", MediaFormat.WEBM,     /**/ "144p"));

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
}
