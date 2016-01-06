package org.schabi.newpipe.services.youtube;

import android.test.AndroidTestCase;
import android.util.Log;

import org.schabi.newpipe.services.VideoInfo;

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

public class YoutubeVideoExtractorDefaultTest extends AndroidTestCase {
    private YoutubeVideoExtractor extractor;

    public void setUp() {
        extractor = new YoutubeVideoExtractor("https://www.youtube.com/watch?v=FmG385_uUys");
    }

    public void testGetErrorCode() {
        assertEquals(extractor.getErrorCode(), VideoInfo.NO_ERROR);
    }

    public void testGetErrorMessage() {
        assertEquals(extractor.getErrorMessage(), "");
    }

    public void testGetTimeStamp() {
        assertTrue(Integer.toString(extractor.getTimeStamp()),
                extractor.getTimeStamp() >= 0);
    }

    public void testGetTitle() {
        assertTrue(!extractor.getTitle().isEmpty());
    }

    public void testGetDescription() {
        assertTrue(extractor.getDescription() != null);
    }

    public void testGetUploader() {
        assertTrue(!extractor.getUploader().isEmpty());
    }

    public void testGetLength() {
        assertTrue(extractor.getLength() > 0);
    }

    public void testGetViews() {
        assertTrue(extractor.getLength() > 0);
    }

    public void testGetUploadDate() {
        assertTrue(extractor.getUploadDate().length() > 0);
    }

    public void testGetThumbnailUrl() {
        assertTrue(extractor.getThumbnailUrl(),
                extractor.getThumbnailUrl().contains("https://"));
    }

    public void testGetUploaderThumbnailUrl() {
        assertTrue(extractor.getUploaderThumbnailUrl(),
                extractor.getUploaderThumbnailUrl().contains("https://"));
    }

    public void testGetAudioStreams() {
        for(VideoInfo.AudioStream s : extractor.getAudioStreams()) {
            assertTrue(s.url,
                    s.url.contains("https://"));
            assertTrue(s.bandwidth > 0);
            assertTrue(s.samplingRate > 0);
        }
    }

    public void testGetVideoStreams() {
        for(VideoInfo.VideoStream s : extractor.getVideoStreams()) {
            assertTrue(s.url,
                    s.url.contains("https://"));
            assertTrue(s.resolution.length() > 0);
            assertTrue(Integer.toString(s.format),
                    0 <= s.format && s.format <= 4);
        }
    }
}
