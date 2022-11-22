package org.schabi.newpipe.fragments.list.search.filter;

import org.schabi.newpipe.App;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.search.filter.FilterContainer;
import org.schabi.newpipe.extractor.search.filter.FilterGroup;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.search.filter.LibraryStringIds;
import org.schabi.newpipe.extractor.services.youtube.search.filter.YoutubeFilters;

import java.util.List;

import androidx.annotation.NonNull;

/**
 * Inject a {@link FilterItem} that actually should not be a real filter.
 * <p>
 * This base class is meant to inject eg {@link DividerItem} (that inherits {@link FilterItem})
 * as Divider between {@link FilterItem}. It will be shown in the UI's.
 * <p>
 * Of course you have to handle {@link DividerItem} or whatever in the Ui's.
 * For that for example have a look at {@link SearchFilterDialogSpinnerAdapter}.
 */
public abstract class InjectFilterItem {

    protected InjectFilterItem(
            @NonNull final String serviceName,
            final int injectedAfterFilterWithId,
            @NonNull final FilterItem toBeInjectedFilterItem) {

        prepareAndInject(serviceName, injectedAfterFilterWithId, toBeInjectedFilterItem);
    }

    // Please refer a static boolean to determine if already injected
    protected abstract boolean isAlreadyInjected();

    // Please refer a static boolean to determine if already injected
    protected abstract void setAsInjected();

    private void prepareAndInject(
            @NonNull final String serviceName,
            final int injectedAfterFilterWithId,
            @NonNull final FilterItem toBeInjectedFilterItem) {

        if (isAlreadyInjected()) { // already run
            return;
        }

        try { // using serviceName to test if we are trying to inject into the right service
            final List<FilterGroup> groups = NewPipe.getService(serviceName)
                    .getSearchQHFactory().getAvailableContentFilter().getFilterGroups();
            injectFilterItemIntoGroup(
                    groups,
                    injectedAfterFilterWithId,
                    toBeInjectedFilterItem);
            setAsInjected();
        } catch (final ExtractionException ignored) {
            // no the service we want to prepareAndInject -> so ignore
        }
    }

    private void injectFilterItemIntoGroup(
            @NonNull final List<FilterGroup> groups,
            final int injectedAfterFilterWithId,
            @NonNull final FilterItem toBeInjectedFilterItem) {

        int indexForFilterId = 0;
        boolean isFilterItemFound = false;
        FilterGroup groupWithTheSearchFilterItem = null;

        for (final FilterGroup group : groups) {
            for (final FilterItem item : group.getFilterItems()) {
                if (item.getIdentifier() == injectedAfterFilterWithId) {
                    isFilterItemFound = true;
                    break;
                }
                indexForFilterId++;
            }

            if (isFilterItemFound) {
                groupWithTheSearchFilterItem = group;
                break;
            }
        }

        if (isFilterItemFound) {
            // we want to insert after the FilterItem we've searched
            indexForFilterId++;
            groupWithTheSearchFilterItem.getFilterItems()
                    .add(indexForFilterId, toBeInjectedFilterItem);
        }
    }

    /**
     * Inject DividerItem between YouTube content filters and YoutubeMusic content filters.
     */
    public static class DividerBetweenYoutubeAndYoutubeMusic extends InjectFilterItem {

        private static boolean isYoutubeMusicDividerInjected = false;

        protected DividerBetweenYoutubeAndYoutubeMusic() {
            super(App.getApp().getApplicationContext().getString(R.string.youtube),
                    YoutubeFilters.ID_CF_MAIN_PLAYLISTS,
                    new DividerItem(R.string.search_filters_youtube_music)
            );
        }

        /**
         * Have a static runner method to avoid creating unnecessary objects if already inserted.
         */
        public static void run() {
            if (!isYoutubeMusicDividerInjected) {
                new DividerBetweenYoutubeAndYoutubeMusic();
            }
        }

        @Override
        protected boolean isAlreadyInjected() {
            return isYoutubeMusicDividerInjected;
        }

        @Override
        protected void setAsInjected() {
            isYoutubeMusicDividerInjected = true;
        }
    }

    /**
     * Used to have a title divider between regular {@link FilterItem}s.
     */
    public static class DividerItem extends FilterItem {

        private final int resId;

        public DividerItem(final int resId) {
            // the LibraryStringIds.. is not needed at all I just need one to satisfy FilterItem.
            super(FilterContainer.ITEM_IDENTIFIER_UNKNOWN, LibraryStringIds.SEARCH_FILTERS_ALL);
            this.resId = resId;
        }

        public int getStringResId() {
            return this.resId;
        }
    }
}
