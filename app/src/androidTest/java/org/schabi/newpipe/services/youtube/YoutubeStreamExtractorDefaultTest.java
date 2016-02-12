package org.schabi.newpipe.services.youtube;

import android.test.AndroidTestCase;

import org.schabi.newpipe.Downloader;
import org.schabi.newpipe.crawler.CrawlingException;
import org.schabi.newpipe.crawler.ParsingException;
import org.schabi.newpipe.crawler.services.youtube.YoutubeStreamExtractor;
import org.schabi.newpipe.crawler.VideoInfo;

import java.io.IOException;

/**
 * Created by the-scrabi on 30.12.15.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * YoutubeVideoExtractorDefault.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

public class YoutubeStreamExtractorDefaultTest extends AndroidTestCase {
    private YoutubeStreamExtractor extractor;

    public void setUp() throws IOException, CrawlingException {
        /* some anonymus video test
        extractor = new YoutubeStreamExtractor("https://www.youtube.com/watch?v=FmG385_uUys",
                new Downloader()); */
        /* some vevo video (suggested to test against) */
        extractor = new YoutubeStreamExtractor("https://www.youtube.com/watch?v=YQHsXMglC9A",
                new Downloader());
    }

    public void testGetInvalidTimeStamp() throws ParsingException {
        assertTrue(Integer.toString(extractor.getTimeStamp()),
                extractor.getTimeStamp() <= 0);
    }

    public void testGetValidTimeStamp() throws CrawlingException, IOException {
        YoutubeStreamExtractor extractor =
                new YoutubeStreamExtractor("https://youtu.be/FmG385_uUys?t=174", new Downloader());
        assertTrue(Integer.toString(extractor.getTimeStamp()),
                extractor.getTimeStamp() == 174);
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
                extractor.getThumbnailUrl().contains("https://"));
    }

    public void testGetUploaderThumbnailUrl() throws ParsingException {
        assertTrue(extractor.getUploaderThumbnailUrl(),
                extractor.getUploaderThumbnailUrl().contains("https://"));
    }

    public void testGetAudioStreams() throws ParsingException {
        assertTrue(!extractor.getAudioStreams().isEmpty());
    }

    public void testGetVideoStreams() throws ParsingException {
        for(VideoInfo.VideoStream s : extractor.getVideoStreams()) {
            assertTrue(s.url,
                    s.url.contains("https://"));
            assertTrue(s.resolution.length() > 0);
            assertTrue(Integer.toString(s.format),
                    0 <= s.format && s.format <= 4);
        }
    }

    public void testGetDashMpd() throws ParsingException {
        assertTrue(extractor.getDashMpdUrl(),
                !extractor.getDashMpdUrl().isEmpty());
    }
}
