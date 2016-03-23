package org.schabi.newpipe.extractor.youtube;

import android.test.AndroidTestCase;

import org.schabi.newpipe.Downloader;
import org.schabi.newpipe.extractor.ExtractionException;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.services.youtube.YoutubeStreamExtractor;

import java.io.IOException;

/**
 * Created by Christian Schabesberger on 30.12.15.
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

    public void testGemaError() throws IOException, ExtractionException {
        if(testActive) {
            try {
                ServiceList.getService("Youtube")
                        .getExtractorInstance("https://www.youtube.com/watch?v=3O1_3zBUKM8",
                        new Downloader());
            } catch(YoutubeStreamExtractor.GemaException ge) {
                assertTrue(true);
            }
        }
    }
}
