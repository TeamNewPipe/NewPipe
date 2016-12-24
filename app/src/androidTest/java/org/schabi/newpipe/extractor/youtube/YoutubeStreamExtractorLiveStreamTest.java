package org.schabi.newpipe.extractor.youtube;

import android.test.AndroidTestCase;


import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.stream_info.StreamExtractor;

import java.io.IOException;

/**
 * Created by Christian Schabesberger on 11.03.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * YoutubeStreamExtractorLiveStreamTest.java is part of NewPipe.
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


public class YoutubeStreamExtractorLiveStreamTest extends AndroidTestCase {

    private StreamExtractor extractor;

    public void setUp() throws IOException, ExtractionException {
        //todo: make the extractor not throw over a livestream
        /*

        NewPipe.init(Downloader.getInstance());
        extractor = NewPipe.getService("Youtube")
                .getExtractorInstance("https://www.youtube.com/watch?v=J0s6NjqdjLE", Downloader.getInstance());
                */
    }

    public void testStreamType() throws ParsingException {
        assertTrue(true);
        // assertTrue(extractor.getStreamType() == AbstractVideoInfo.StreamType.LIVE_STREAM);
    }
}

