package org.schabi.newpipe.extractor.services.youtube.youtube;

import org.junit.Before;
import org.junit.Test;
import org.schabi.newpipe.Downloader;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.search.SearchEngine;
import org.schabi.newpipe.extractor.search.SearchResult;

import java.util.EnumSet;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;


/**
 * Created by Christian Schabesberger on 29.12.15.
 *
 * Copyright (C) Christian Schabesberger 2015 <chris.schabesberger@mailbox.org>
 * YoutubeSearchEngineStreamTest.java is part of NewPipe.
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

/**
 * Test for {@link SearchEngine}
 */
public class YoutubeSearchEngineAllTest {
    private SearchResult result;

    @Before
    public void setUp() throws Exception {
        NewPipe.init(Downloader.getInstance());
        SearchEngine engine = NewPipe.getService("Youtube").getSearchEngineInstance();

        result = engine.search("asdf", 0, "de",
                EnumSet.of(SearchEngine.Filter.CHANNEL,
                        SearchEngine.Filter.STREAM)).getSearchResult();
    }

    @Test
    public void testResultList() {
        assertFalse(result.resultList.isEmpty());
    }

    @Test
    public void testResultErrors() {
        assertTrue(result.errors == null || result.errors.isEmpty());
    }

    @Test
    public void testSuggestion() {
        //todo write a real test
        assertTrue(result.suggestion != null);
    }
}
