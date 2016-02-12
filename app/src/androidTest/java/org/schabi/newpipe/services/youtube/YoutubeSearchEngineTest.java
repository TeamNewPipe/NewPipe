package org.schabi.newpipe.services.youtube;

import android.test.AndroidTestCase;

import org.schabi.newpipe.crawler.VideoPreviewInfo;
import org.schabi.newpipe.crawler.SearchEngine;
import org.schabi.newpipe.crawler.services.youtube.YoutubeSearchEngine;
import org.schabi.newpipe.Downloader;

import java.util.ArrayList;

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
    private SearchEngine.Result result;
    private ArrayList<String> suggestionReply;

    @Override
    public void setUp() throws Exception{
        super.setUp();
        SearchEngine engine = new YoutubeSearchEngine();

        result = engine.search("https://www.youtube.com/results?search_query=bla",
                0, "de", new Downloader());
        suggestionReply = engine.suggestionList("hello", new Downloader());
    }

    public void testIfNoErrorOccur() {
        assertEquals(result.errorMessage, "");
    }

    public void testIfListIsNotEmpty() {
        assertEquals(result.resultList.size() > 0, true);
    }

    public void testItemsHaveTitle() {
        for(VideoPreviewInfo i : result.resultList) {
            assertEquals(i.title.isEmpty(), false);
        }
    }

    public void testItemsHaveUploader() {
        for(VideoPreviewInfo i : result.resultList) {
            assertEquals(i.uploader.isEmpty(), false);
        }
    }

    public void testItemsHaveRightDuration() {
        for(VideoPreviewInfo i : result.resultList) {
            assertTrue(i.duration, i.duration.contains(":"));
        }
    }

    public void testItemsHaveRightThumbnail() {
        for (VideoPreviewInfo i : result.resultList) {
            assertTrue(i.thumbnail_url, i.thumbnail_url.contains("https://"));
        }
    }

    public void testItemsHaveRightVideoUrl() {
        for (VideoPreviewInfo i : result.resultList) {
            assertTrue(i.webpage_url, i.webpage_url.contains("https://"));
        }
    }

    public void testIfSuggestionsAreReplied() {
        assertEquals(suggestionReply.size() > 0, true);
    }

    public void testIfSuggestionsAreValid() {
        for(String s : suggestionReply) {
            assertTrue(s, !s.isEmpty());
        }
    }
}
