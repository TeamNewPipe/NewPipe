package org.schabi.newpipe.extractor.youtube;

import android.test.AndroidTestCase;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.schabi.newpipe.extractor.AbstractVideoInfo;
import org.schabi.newpipe.extractor.SearchResult;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamPreviewInfo;
import org.schabi.newpipe.extractor.SearchEngine;
import org.schabi.newpipe.extractor.services.youtube.YoutubeSearchEngine;
import org.schabi.newpipe.Downloader;
import org.schabi.newpipe.extractor.services.youtube.YoutubeService;

import java.util.ArrayList;

/**
 * Created by Christian Schabesberger on 29.12.15.
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
    private SearchResult result;
    private ArrayList<String> suggestionReply;

    @Override
    public void setUp() throws Exception{
        super.setUp();
        SearchEngine engine = ServiceList.getService("Youtube")
                .getSearchEngineInstance(new Downloader());

        result = engine.search("lefloid",
                0, "de", new Downloader()).getSearchResult();
        suggestionReply = engine.suggestionList("hello","de",new Downloader());
    }

    public void testIfNoErrorOccur() {
        assertTrue(result.errors.isEmpty() ? "" : ExceptionUtils.getStackTrace(result.errors.get(0))
                ,result.errors.isEmpty());
    }

    public void testIfListIsNotEmpty() {
        assertEquals(result.resultList.size() > 0, true);
    }

    public void testItemsHaveTitle() {
        for(StreamPreviewInfo i : result.resultList) {
            assertEquals(i.title.isEmpty(), false);
        }
    }

    public void testItemsHaveUploader() {
        for(StreamPreviewInfo i : result.resultList) {
            assertEquals(i.uploader.isEmpty(), false);
        }
    }

    public void testItemsHaveRightDuration() {
        for(StreamPreviewInfo i : result.resultList) {
            assertTrue(i.duration >= 0);
        }
    }

    public void testItemsHaveRightThumbnail() {
        for (StreamPreviewInfo i : result.resultList) {
            assertTrue(i.thumbnail_url, i.thumbnail_url.contains("https://"));
        }
    }

    public void testItemsHaveRightVideoUrl() {
        for (StreamPreviewInfo i : result.resultList) {
            assertTrue(i.webpage_url, i.webpage_url.contains("https://"));
        }
    }

    public void testViewCount() {
        /*
        for(StreamPreviewInfo i : result.resultList) {
            assertTrue(Long.toString(i.view_count), i.view_count != -1);
        }
        */
        // that specific link used for this test, there are no videos with less
        // than 10.000 views, so we can test against that.
        for(StreamPreviewInfo i : result.resultList) {
            assertTrue(i.title + ": " + Long.toString(i.view_count), i.view_count >= 10000);
        }
    }

    public void testStreamType() {
        for(StreamPreviewInfo i : result.resultList) {
            assertTrue("not a livestream and not a video",
                    i.stream_type == AbstractVideoInfo.StreamType.VIDEO_STREAM ||
                    i.stream_type == AbstractVideoInfo.StreamType.LIVE_STREAM);
        }
    }

    public void testIfSuggestionsAreReplied() {
        assertEquals(!suggestionReply.isEmpty(), true);
    }

    public void testIfSuggestionsAreValid() {
        for(String s : suggestionReply) {
            assertTrue(s, !s.isEmpty());
        }
    }
}
