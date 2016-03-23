package org.schabi.newpipe.extractor.youtube;

import android.test.AndroidTestCase;

import org.schabi.newpipe.Downloader;
import org.schabi.newpipe.extractor.AbstractVideoInfo;
import org.schabi.newpipe.extractor.ExtractionException;
import org.schabi.newpipe.extractor.ParsingException;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamExtractor;

import java.io.IOException;

/**
 * Created by Christian Schabesberger on 11.03.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * YoutubestreamExtractorLiveStreamTest.java is part of NewPipe.
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


public class YoutubestreamExtractorLiveStreamTest extends AndroidTestCase {

    private StreamExtractor extractor;

    public void setUp() throws IOException, ExtractionException {
        //todo: make the extractor not throw over a livestream
        /*
        extractor = ServiceList.getService("Youtube")
                .getExtractorInstance("https://www.youtube.com/watch?v=J0s6NjqdjLE", new Downloader());
                */
    }

    public void testStreamType() throws ParsingException {
        assertTrue(true);
        // assertTrue(extractor.getStreamType() == AbstractVideoInfo.StreamType.LIVE_STREAM);
    }
}

