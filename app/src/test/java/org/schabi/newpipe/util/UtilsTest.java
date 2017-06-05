package org.schabi.newpipe.util;

import org.junit.Test;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream_info.AudioStream;
import org.schabi.newpipe.extractor.stream_info.VideoStream;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class UtilsTest {
    private List<AudioStream> audioStreamsTestList = Arrays.asList(
            new AudioStream("", MediaFormat.M4A.id,   /**/ 120, 0, 0),
            new AudioStream("", MediaFormat.WEBMA.id, /**/ 190, 0, 0),
            new AudioStream("", MediaFormat.M4A.id,   /**/ 130, 0, 0),
            new AudioStream("", MediaFormat.WEBMA.id, /**/ 60, 0, 0),
            new AudioStream("", MediaFormat.M4A.id,   /**/ 320, 0, 0),
            new AudioStream("", MediaFormat.WEBMA.id, /**/ 320, 0, 0));

    private List<VideoStream> videoStreamsTestList = Arrays.asList(
            new VideoStream("",       /**/ MediaFormat.MPEG_4.id,   /**/ "720p"),
            new VideoStream("",       /**/ MediaFormat.v3GPP.id,    /**/ "240p"),
            new VideoStream("",       /**/ MediaFormat.WEBM.id,     /**/ "480p"),
            new VideoStream("",       /**/ MediaFormat.v3GPP.id,    /**/ "144p"),
            new VideoStream("",       /**/ MediaFormat.MPEG_4.id,   /**/ "360p"),
            new VideoStream("",       /**/ MediaFormat.WEBM.id,     /**/ "360p"));

    private List<VideoStream> videoOnlyStreamsTestList = Arrays.asList(
            new VideoStream(true, "", /**/ MediaFormat.MPEG_4.id,  /**/ "720p"),
            new VideoStream(true, "", /**/ MediaFormat.MPEG_4.id,  /**/ "720p"),
            new VideoStream(true, "", /**/ MediaFormat.MPEG_4.id,  /**/ "2160p"),
            new VideoStream(true, "", /**/ MediaFormat.MPEG_4.id,  /**/ "1440p60"),
            new VideoStream(true, "", /**/ MediaFormat.WEBM.id,    /**/ "720p60"),
            new VideoStream(true, "", /**/ MediaFormat.MPEG_4.id,  /**/ "2160p60"),
            new VideoStream(true, "", /**/ MediaFormat.MPEG_4.id,  /**/ "720p60"),
            new VideoStream(true, "", /**/ MediaFormat.MPEG_4.id,  /**/ "1080p"),
            new VideoStream(true, "", /**/ MediaFormat.MPEG_4.id,  /**/ "1080p60"));

    @Test
    public void getHighestQualityAudioTest() throws Exception {
        assertEquals(320, Utils.getHighestQualityAudio(audioStreamsTestList).avgBitrate);
    }

    @Test
    public void getSortedStreamVideosListTest() throws Exception {
        List<VideoStream> result = Utils.getSortedStreamVideosList(MediaFormat.MPEG_4, true, videoStreamsTestList, videoOnlyStreamsTestList, true);

        List<String> expected = Arrays.asList("144p", "240p", "360p", "480p", "720p", "720p60", "1080p", "1080p60", "1440p60", "2160p", "2160p60");
        //for (VideoStream videoStream : result) System.out.println(videoStream.resolution + " > " + MediaFormat.getSuffixById(videoStream.format) + " > " + videoStream.isVideoOnly);

        assertEquals(result.size(), expected.size());
        for (int i = 0; i < result.size(); i++) {
            assertEquals(result.get(i).resolution, expected.get(i));
        }

        ////////////////////
        // Reverse Order //
        //////////////////

        result = Utils.getSortedStreamVideosList(MediaFormat.MPEG_4, true, videoStreamsTestList, videoOnlyStreamsTestList, false);

        expected = Arrays.asList("2160p60", "2160p", "1440p60", "1080p60", "1080p", "720p60", "720p", "480p", "360p", "240p", "144p");
        assertEquals(result.size(), expected.size());
        for (int i = 0; i < result.size(); i++) assertEquals(result.get(i).resolution, expected.get(i));

        ////////////////////////////////////
        // Don't show Higher resolutions //
        //////////////////////////////////

        result = Utils.getSortedStreamVideosList(MediaFormat.MPEG_4, false, videoStreamsTestList, videoOnlyStreamsTestList, false);
        expected = Arrays.asList("1080p60", "1080p", "720p60", "720p", "480p", "360p", "240p", "144p");
        assertEquals(result.size(), expected.size());
        for (int i = 0; i < result.size(); i++) assertEquals(result.get(i).resolution, expected.get(i));
    }

}