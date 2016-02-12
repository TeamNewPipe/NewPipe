package org.schabi.newpipe.services.youtube;

import android.test.AndroidTestCase;

import org.schabi.newpipe.Downloader;
import org.schabi.newpipe.crawler.CrawlingException;
import org.schabi.newpipe.crawler.services.youtube.YoutubeStreamExtractor;

import java.io.IOException;

/**
 * Created by the-scrabi on 30.12.15.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * YoutubeVideoExtractorGema.java is part of NewPipe.
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


// This class only works in Germany.
public class YoutubeStreamExtractorGemaTest extends AndroidTestCase {

    // Deaktivate this Test Case bevore uploading it githup, otherwise CI will fail.
    private static final boolean testActive = false;

    public void testGemaError() throws IOException, CrawlingException {
        if(testActive) {
            try {
                new YoutubeStreamExtractor("https://www.youtube.com/watch?v=3O1_3zBUKM8",
                        new Downloader());
                assertTrue("Gema exception not thrown", false);
            } catch(YoutubeStreamExtractor.GemaException ge) {
                assertTrue(true);
            }
        }
    }
}
