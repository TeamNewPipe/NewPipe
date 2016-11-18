package org.schabi.newpipe.extractor.youtube;

import android.test.AndroidTestCase;

import org.schabi.newpipe.Downloader;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.channel.ChannelExtractor;

/**
 * Created by Christian Schabesberger on 12.09.16.
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

public class YoutubeChannelExtractorTest extends AndroidTestCase  {

    ChannelExtractor extractor;
    @Override
    public void setUp() throws Exception {
        super.setUp();
        extractor = NewPipe.getService("Youtube")
                .getChannelExtractorInstance("https://www.youtube.com/channel/UCYJ61XIK64sp6ZFFS8sctxw", 0);
    }

    public void testGetChannelName() throws Exception {
        assertEquals(extractor.getChannelName(), "Gronkh");
    }

    public void testGetAvatarUrl() throws Exception {
        assertTrue(extractor.getAvatarUrl(), extractor.getAvatarUrl().contains("yt3"));
    }

    public void testGetBannerurl() throws Exception {
        assertTrue(extractor.getBannerUrl(), extractor.getBannerUrl().contains("yt3"));
    }

    public void testGetFeedUrl() throws Exception {
        assertTrue(extractor.getFeedUrl(), extractor.getFeedUrl().contains("feed"));
    }

    public void testGetStreams() throws Exception {
        assertTrue("no streams are received", !extractor.getStreams().getItemList().isEmpty());
    }

    public void testGetStreamsErrors() throws Exception {
        assertTrue("errors during stream list extraction", extractor.getStreams().getErrors().isEmpty());
    }

    public void testHasNextPage() throws Exception {
        // this particular example (link) has a next page !!!
        assertTrue("no next page link found", extractor.hasNextPage());
    }

    public void testGetNextPage() throws Exception {
        extractor = NewPipe.getService("Youtube")
                .getChannelExtractorInstance("https://www.youtube.com/channel/UCYJ61XIK64sp6ZFFS8sctxw", 1);
        assertTrue("next page didn't have content", !extractor.getStreams().getItemList().isEmpty());
    }

    public void testGetNextNextPageUrl() throws Exception {
        extractor = NewPipe.getService("Youtube")
                .getChannelExtractorInstance("https://www.youtube.com/channel/UCYJ61XIK64sp6ZFFS8sctxw", 2);
        assertTrue("next page didn't have content", extractor.hasNextPage());
    }
}
