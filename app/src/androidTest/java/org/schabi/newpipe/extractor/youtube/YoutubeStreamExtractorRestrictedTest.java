package org.schabi.newpipe.extractor.youtube;

import android.test.AndroidTestCase;

import org.schabi.newpipe.Downloader;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.stream_info.StreamExtractor;
import org.schabi.newpipe.extractor.stream_info.VideoStream;

import java.io.IOException;

public class YoutubeStreamExtractorRestrictedTest extends AndroidTestCase {
    public static final String HTTPS = "https://";
    private StreamExtractor extractor;

    public void setUp() throws Exception {
        super.setUp();
        NewPipe.init(new Downloader());
        extractor = NewPipe.getService("Youtube")
                .getExtractorInstance("https://www.youtube.com/watch?v=i6JTvzrpBy0");
    }

    public void testGetInvalidTimeStamp() throws ParsingException {
        assertTrue(Integer.toString(extractor.getTimeStamp()),
                extractor.getTimeStamp() <= 0);
    }

    public void testGetValidTimeStamp() throws ExtractionException, IOException {
        StreamExtractor extractor= NewPipe.getService("Youtube")
                .getExtractorInstance("https://youtu.be/FmG385_uUys?t=174");
        assertTrue(Integer.toString(extractor.getTimeStamp()),
                extractor.getTimeStamp() == 174);
    }

    public void testGetAgeLimit() throws ParsingException {
        assertTrue(extractor.getAgeLimit() == 18);
    }

    public void testGetTitle() throws ParsingException {
        assertTrue(!extractor.getTitle().isEmpty());
    }

    public void testGetDescription() throws ParsingException {
        assertTrue(extractor.getDescription() != null);
    }

    public void testGetUploader() throws ParsingException {
        assertTrue(!extractor.getUploader().isEmpty());
    }

    public void testGetLength() throws ParsingException {
        assertTrue(extractor.getLength() > 0);
    }

    public void testGetViews() throws ParsingException {
        assertTrue(extractor.getLength() > 0);
    }

    public void testGetUploadDate() throws ParsingException {
        assertTrue(extractor.getUploadDate().length() > 0);
    }

    public void testGetThumbnailUrl() throws ParsingException {
        assertTrue(extractor.getThumbnailUrl(),
                extractor.getThumbnailUrl().contains(HTTPS));
    }

    public void testGetUploaderThumbnailUrl() throws ParsingException {
        assertTrue(extractor.getUploaderThumbnailUrl(),
                extractor.getUploaderThumbnailUrl().contains(HTTPS));
    }

    public void testGetAudioStreams() throws ParsingException {
        // audiostream not always necessary
        //assertTrue(!extractor.getAudioStreams().isEmpty());
    }

    public void testGetVideoStreams() throws ParsingException {
        for(VideoStream s : extractor.getVideoStreams()) {
            assertTrue(s.url,
                    s.url.contains(HTTPS));
            assertTrue(s.resolution.length() > 0);
            assertTrue(Integer.toString(s.format),
                    0 <= s.format && s.format <= 4);
        }
    }
}
