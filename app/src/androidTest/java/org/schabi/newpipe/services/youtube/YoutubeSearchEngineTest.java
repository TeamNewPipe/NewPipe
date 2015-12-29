package org.schabi.newpipe.services.youtube;

import android.test.AndroidTestCase;

import org.schabi.newpipe.services.SearchEngine;

/**
 * Created by the-scrabi on 29.12.15.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * YoutubeSearchEngineTest.java is part of NewPipe.
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

public class YoutubeSearchEngineTest extends AndroidTestCase {

    SearchEngine engine;
    SearchEngine.Result result;

    @Override
    public void setUp() throws Exception{
        super.setUp();
        engine = new YoutubeSearchEngine();
        result = engine.search("https://www.youtube.com/results?search_query=bla", 0, "de");
    }

    public void testIfNoErrorOccure() {
        assertEquals(result.errorMessage, "");
    }

    public void testIfListIsNotEmpty() {
        assertEquals(result.resultList.size() > 0, true);
    }

    public void testItemHasTitle() {
        assertEquals(result.resultList.get(0).title.isEmpty(), false);
    }

    public void testItemHasUploader() {
        assertEquals(result.resultList.get(0).uploader.isEmpty(), false);
    }

    public void testItemHasRightDuration() {
        assertEquals(result.resultList.get(0).duration.contains(":"), true);
    }

    public void testItemHasRightThumbnail() {
        assertEquals(result.resultList.get(0).thumbnail_url.contains("https://"), true);
    }

    public void testItemHasRightVideoUrl() {
        assertEquals(result.resultList.get(0).webpage_url.contains("https://"), true);
    }
}
