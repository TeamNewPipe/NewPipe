package org.schabi.newpipe.extractor.youtube;

import android.test.AndroidTestCase;

import org.schabi.newpipe.Downloader;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.search.SuggestionExtractor;
import org.schabi.newpipe.extractor.services.youtube.YoutubeSuggestionExtractor;

import java.io.IOException;
import java.util.List;

/**
 * Created by Christian Schabesberger on 18.11.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * YoutubeSearchResultTest.java is part of NewPipe.
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

public class YoutubeSearchResultTest extends AndroidTestCase {
    List<String> suggestionReply;


    @Override
    public void setUp() throws Exception {
        super.setUp();
        NewPipe.init(new Downloader());
        SuggestionExtractor engine = new YoutubeSuggestionExtractor(0);
        suggestionReply = engine.suggestionList("hello", "de");
    }

    public void testIfSuggestions() {
        assertFalse(suggestionReply.isEmpty());
    }
}
