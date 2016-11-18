package org.schabi.newpipe.extractor.youtube;

import android.test.AndroidTestCase;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.search.SearchEngine;
import org.schabi.newpipe.extractor.search.SearchResult;

import java.util.List;

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

    @Override
    public void setUp() throws Exception {
        super.setUp();
        SearchEngine engine = NewPipe.getService("Youtube").getSearchEngineInstance();

        result = engine.search("this is something boring", 0, "de").getSearchResult();
    }

    public void testResultList() {
        assertFalse(result.resultList.isEmpty());
    }

    public void testResultErrors() {
        assertTrue(result.errors == null || result.errors.isEmpty());
    }

    public void testSuggestion() {
        //todo write a real test
        assertTrue(result.suggestion != null);
    }
}
