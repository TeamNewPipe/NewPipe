package org.schabi.newpipe.fragments.list.channel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.Filter;
import org.schabi.newpipe.extractor.search.filter.FilterGroup;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeChannelTabLinkHandlerFactory;
import org.schabi.newpipe.util.ChannelTabHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ChannelTabSortHelper {
    private ChannelTabSortHelper() {
    }

    @NonNull
    static List<FilterItem> getAvailableSortFilters(
            final int serviceId,
            @Nullable final ListLinkHandlerFactory factory,
            @NonNull final ListLinkHandler tabHandler) {
        final String tabName = ChannelTabHelper.getTabName(tabHandler);
        final boolean youtubeLive = serviceId == ServiceList.YouTube.getServiceId()
                && factory instanceof YoutubeChannelTabLinkHandlerFactory
                && ChannelTabs.LIVESTREAMS.equals(tabName);
        if (factory == null
                || (!ChannelTabs.VIDEOS.equals(tabName) && !youtubeLive)) {
            return Collections.emptyList();
        }

        Filter availableSortFilter = null;
        if (!tabHandler.getContentFilters().isEmpty()) {
            availableSortFilter = factory.getContentFilterSortFilterVariant(
                    tabHandler.getContentFilters().get(0).getIdentifier());
        }
        if (availableSortFilter == null) {
            availableSortFilter = factory.getAvailableSortFilter();
        }
        if (availableSortFilter == null || availableSortFilter.getFilterGroups() == null) {
            return Collections.emptyList();
        }

        final List<FilterItem> filters = new ArrayList<>();
        for (final FilterGroup group : availableSortFilter.getFilterGroups()) {
            Collections.addAll(filters, group.filterItems);
        }
        return filters;
    }

    static int getSelectedSortFilterIndex(@NonNull final ListLinkHandler tabHandler,
                                          @NonNull final List<FilterItem> availableFilters) {
        if (availableFilters.isEmpty()) {
            return -1;
        }
        final List<FilterItem> selectedFilters = tabHandler.getSortFilter();
        if (selectedFilters == null || selectedFilters.isEmpty()) {
            return 0;
        }

        final String selectedName = selectedFilters.get(0).getName();
        return getSortFilterIndex(selectedName, availableFilters);
    }

    static int getSortFilterIndex(@Nullable final String filterName,
                                  @NonNull final List<FilterItem> availableFilters) {
        if (availableFilters.isEmpty()) {
            return -1;
        }
        if (filterName == null) {
            return 0;
        }
        for (int i = 0; i < availableFilters.size(); i++) {
            if (filterName.equals(availableFilters.get(i).getName())) {
                return i;
            }
        }
        return 0;
    }

    @NonNull
    static ListLinkHandler withSortFilter(@NonNull final ListLinkHandlerFactory factory,
                                          @NonNull final ListLinkHandler currentHandler,
                                          @NonNull final FilterItem selectedFilter)
            throws ParsingException {
        final List<FilterItem> selectedFilters = Collections.singletonList(selectedFilter);
        final String sortedUrl = factory.getUrl(
                currentHandler.getId(), currentHandler.getContentFilters(), selectedFilters);
        return new ListLinkHandler(
                currentHandler.getOriginalUrl(),
                sortedUrl,
                currentHandler.getId(),
                currentHandler.getContentFilters(),
                selectedFilters);
    }
}
