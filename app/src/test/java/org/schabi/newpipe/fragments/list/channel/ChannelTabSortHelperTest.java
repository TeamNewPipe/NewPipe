package org.schabi.newpipe.fragments.list.channel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeChannelTabLinkHandlerFactory;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ChannelTabSortHelperTest {
    private static final String CHANNEL_ID = "channel/UC123";

    @Test
    public void exposesVideoSortFiltersButNotShorts() {
        final List<FilterItem> videoFilters = ChannelTabSortHelper.getAvailableSortFilters(
                ServiceList.YouTube.getServiceId(), factory(), handler(ChannelTabs.VIDEOS, null));
        final List<FilterItem> shortsFilters = ChannelTabSortHelper.getAvailableSortFilters(
                ServiceList.YouTube.getServiceId(), factory(), handler(ChannelTabs.SHORTS, null));

        assertEquals(
                List.of("latest", "popular", "oldest"),
                videoFilters.stream().map(FilterItem::getName).collect(Collectors.toList()));
        assertTrue(shortsFilters.isEmpty());
    }

    @Test
    public void defaultsToTheFirstAdvertisedSortFilter() {
        final ListLinkHandler handler = handler(ChannelTabs.VIDEOS, null);
        final List<FilterItem> filters =
                ChannelTabSortHelper.getAvailableSortFilters(
                        ServiceList.YouTube.getServiceId(), factory(), handler);

        assertEquals(0, ChannelTabSortHelper.getSelectedSortFilterIndex(handler, filters));
    }

    @Test
    public void rebuildsTheHandlerWithTheSelectedServerSideOrder() throws Exception {
        final ListLinkHandler handler = handler(ChannelTabs.VIDEOS, null);
        final List<FilterItem> filters =
                ChannelTabSortHelper.getAvailableSortFilters(
                        ServiceList.YouTube.getServiceId(), factory(), handler);

        final ListLinkHandler sorted =
                ChannelTabSortHelper.withSortFilter(factory(), handler, filters.get(1));

        assertEquals(handler.getOriginalUrl(), sorted.getOriginalUrl());
        assertEquals(handler.getId(), sorted.getId());
        assertEquals("popular", sorted.getSortFilter().get(0).getName());
        assertTrue(sorted.getUrl().endsWith("/videos?sort=popular"));
        assertEquals(1, ChannelTabSortHelper.getSelectedSortFilterIndex(sorted, filters));
    }

    private static YoutubeChannelTabLinkHandlerFactory factory() {
        return YoutubeChannelTabLinkHandlerFactory.getInstance();
    }

    private static ListLinkHandler handler(final String tab,
                                           final FilterItem selectedSortFilter) {
        final List<FilterItem> contentFilter = Collections.singletonList(
                new FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, tab));
        final List<FilterItem> sortFilter = selectedSortFilter == null
                ? null : Collections.singletonList(selectedSortFilter);
        return new ListLinkHandler(
                "https://www.youtube.com/" + CHANNEL_ID + "/" + tab,
                "https://www.youtube.com/" + CHANNEL_ID + "/" + tab,
                CHANNEL_ID,
                contentFilter,
                sortFilter);
    }
}
