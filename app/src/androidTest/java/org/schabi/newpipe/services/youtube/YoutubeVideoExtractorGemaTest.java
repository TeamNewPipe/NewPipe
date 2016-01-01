package org.schabi.newpipe.services.youtube;

import android.test.AndroidTestCase;

import org.schabi.newpipe.services.VideoInfo;

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
public class YoutubeVideoExtractorGemaTest extends AndroidTestCase {

    // Deaktivate this Test Case bevore uploading it githup, otherwise CI will fail.
    private static final boolean testActive = false;
    //


    private YoutubeVideoExtractor extractor;

    public void setUp() {
        if(testActive) {
            extractor = new YoutubeVideoExtractor("https://www.youtube.com/watch?v=3O1_3zBUKM8");
        }
    }

    public void testGetErrorCode() {
        if(testActive) {
            assertEquals(extractor.getErrorCode(), VideoInfo.ERROR_BLOCKED_BY_GEMA);
        } else {
            assertTrue(true);
        }
    }

    public void testGetErrorMessage() {
        if(testActive) {
            assertTrue(extractor.getErrorMessage(),
                    extractor.getErrorMessage().contains("GEMA"));
        } else {
            assertTrue(true);
        }
    }
}
