package org.schabi.newpipe.extractor.services.youtube.youtube;

import org.junit.Before;
import org.junit.Test;

import org.schabi.newpipe.Downloader;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.channel.ChannelExtractor;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

/**
 * Created by Christian Schabesberger on 12.09.16.
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
 * Test for {@link ChannelExtractor}
 */

public class YoutubeChannelExtractorTest  {

    ChannelExtractor extractor;

    @Before
    public void setUp() throws Exception {
        NewPipe.init(Downloader.getInstance());
        extractor = NewPipe.getService("Youtube")
                .getChannelExtractorInstance("https://www.youtube.com/channel/UCYJ61XIK64sp6ZFFS8sctxw", 0);
    }

    @Test
    public void testGetDownloader()  throws Exception {
        assertNotNull(NewPipe.getDownloader());
    }

    @Test
    public void testGetChannelName() throws Exception {
        assertEquals(extractor.getChannelName(), "Gronkh");
    }

    @Test
    public void testGetAvatarUrl() throws Exception {
        assertTrue(extractor.getAvatarUrl(), extractor.getAvatarUrl().contains("yt3"));
    }

    @Test
    public void testGetBannerurl() throws Exception {
        assertTrue(extractor.getBannerUrl(), extractor.getBannerUrl().contains("yt3"));
    }

    @Test
    public void testGetFeedUrl() throws Exception {
        assertTrue(extractor.getFeedUrl(), extractor.getFeedUrl().contains("feed"));
    }

    @Test
    public void testGetStreams() throws Exception {
        assertTrue("no streams are received", !extractor.getStreams().getItemList().isEmpty());
    }

    @Test
    public void testGetStreamsErrors() throws Exception {
        assertTrue("errors during stream list extraction", extractor.getStreams().getErrors().isEmpty());
    }

    @Test
    public void testHasNextPage() throws Exception {
        // this particular example (link) has a next page !!!
        assertTrue("no next page link found", extractor.hasNextPage());
    }

    @Test
    public void testGetSubscriberCount() throws Exception {
        assertTrue("wrong subscriber count", extractor.getSubscriberCount() >= 0);
    }

    @Test
    public void testGetNextPage() throws Exception {
        extractor = NewPipe.getService("Youtube")
                .getChannelExtractorInstance("https://www.youtube.com/channel/UCYJ61XIK64sp6ZFFS8sctxw", 1);
        assertTrue("next page didn't have content", !extractor.getStreams().getItemList().isEmpty());
    }

    @Test
    public void testGetNextNextPageUrl() throws Exception {
        extractor = NewPipe.getService("Youtube")
                .getChannelExtractorInstance("https://www.youtube.com/channel/UCYJ61XIK64sp6ZFFS8sctxw", 2);
        assertTrue("next page didn't have content", extractor.hasNextPage());
    }
}
