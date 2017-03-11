package org.schabi.newpipe.extractor.services.youtube.youtube;

import org.junit.Before;
import org.junit.Test;
import org.schabi.newpipe.Downloader;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.SuggestionExtractor;
import org.schabi.newpipe.extractor.services.youtube.YoutubeSuggestionExtractor;
import java.util.List;
import static junit.framework.Assert.assertFalse;

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

/**
 * Test for {@link SuggestionExtractor}
 */
public class YoutubeSearchResultTest {
    List<String> suggestionReply;


    @Before
    public void setUp() throws Exception {
        NewPipe.init(Downloader.getInstance());
        SuggestionExtractor engine = new YoutubeSuggestionExtractor(0);
        suggestionReply = engine.suggestionList("hello", "de");
    }

    @Test
    public void testIfSuggestions() {
        assertFalse(suggestionReply.isEmpty());
    }
}
