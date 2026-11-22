package org.schabi.newpipe.extractor.services;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.services.bitchute.linkHandler.BitchuteChannelTabLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.bitchute.search.filter.BitchuteFilters;
import org.schabi.newpipe.extractor.services.rumble.linkHandler.RumbleChannelTabLinkHandlerFactory;
import org.schabi.newpipe.extractor.services.rumble.search.filter.RumbleFilters;

class AdditionalStreamingServicesTest {

    @Test
    void registersServicesWithoutChangingExistingIds() {
        assertEquals(11, ServiceList.all().size());
        assertSame(ServiceList.BitChute, ServiceList.all().get(7));
        assertSame(ServiceList.Rumble, ServiceList.all().get(8));
        assertSame(ServiceList.Odysee, ServiceList.all().get(9));
        assertEquals(7, ServiceList.BitChute.getServiceId());
        assertEquals(8, ServiceList.Rumble.getServiceId());
        assertEquals(9, ServiceList.Odysee.getServiceId());
        assertEquals("BitChute", ServiceList.BitChute.getServiceInfo().getName());
        assertEquals("Rumble", ServiceList.Rumble.getServiceInfo().getName());
        assertEquals("Odysee", ServiceList.Odysee.getServiceInfo().getName());
    }

    @Test
    void recognizesBitChuteAndRumbleVideoUrls() throws Exception {
        assertEquals("8gwdyYJ8BUk", ServiceList.BitChute.getStreamLHFactory()
                .fromUrl("https://www.bitchute.com/video/8gwdyYJ8BUk/").getId());
        assertEquals("vg1hkl", ServiceList.Rumble.getStreamLHFactory()
                .fromUrl("https://rumble.com/vg1hkl-example-title.html").getId());
        assertTrue(ServiceList.Rumble.getStreamLHFactory()
                .acceptUrl("https://rumble.com/shorts/v6abcde"));
        assertFalse(ServiceList.Rumble.getStreamLHFactory()
                .acceptUrl("https://rumble.com/category/news"));
    }

    @Test
    void buildsServiceSpecificSearchUrls() throws Exception {
        final FilterItem bitChuteVideos = ServiceList.BitChute.getSearchQHFactory()
                .getFilterItem(BitchuteFilters.ID_CF_MAIN_VIDEOS);
        final FilterItem rumbleChannels = ServiceList.Rumble.getSearchQHFactory()
                .getFilterItem(RumbleFilters.ID_CF_MAIN_CHANNELS);

        assertEquals("https://www.bitchute.com/search/?query=privacy&kind=video",
                ServiceList.BitChute.getSearchQHFactory()
                        .fromQuery("privacy", singletonList(bitChuteVideos), emptyList()).getUrl());
        assertEquals("https://rumble.com/search/channel?q=world+news",
                ServiceList.Rumble.getSearchQHFactory()
                        .fromQuery(
                                "world news",
                                singletonList(rumbleChannels),
                                emptyList())
                        .getUrl());
    }

    @Test
    void buildsChannelTabUrls() throws Exception {
        assertEquals("https://www.bitchute.com/channel/example",
                BitchuteChannelTabLinkHandlerFactory.getInstance().getUrl(
                        "example",
                        singletonList(BitchuteChannelTabLinkHandlerFactory.VIDEOS_TAB),
                        emptyList()));
        assertEquals("https://rumble.com/c/example/livestreams",
                RumbleChannelTabLinkHandlerFactory.getInstance().getUrl(
                        "c/example",
                        singletonList(RumbleChannelTabLinkHandlerFactory.LIVESTREAMS_TAB),
                        emptyList()));
    }
}
