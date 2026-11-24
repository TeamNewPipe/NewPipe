package org.schabi.newpipe.extractor;

import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;


/**
 * Base class to extractors that have a list (e.g. playlists, users).
 * @param <R> the info item type this list extractor provides
 */
public abstract class ListExtractor<R extends InfoItem> extends Extractor {
    /**
     * Constant that should be returned whenever
     * a list has an unknown number of items.
     */
    public static final long ITEM_COUNT_UNKNOWN = -1;
    /**
     * Constant that should be returned whenever a list has an
     * infinite number of items. For example a YouTube mix.
     */
    public static final long ITEM_COUNT_INFINITE = -2;
    /**
     * Constant that should be returned whenever a list
     * has an unknown number of items bigger than 100.
     */
    public static final long ITEM_COUNT_MORE_THAN_100 = -3;

    public ListExtractor(final StreamingService service, final ListLinkHandler linkHandler) {
        super(service, linkHandler);
    }

    /**
     * A {@link InfoItemsPage InfoItemsPage} corresponding to the initial page
     * where the items are from the initial request and the nextPage relative to it.
     *
     * @return a {@link InfoItemsPage} corresponding to the initial page
     */
    @Nonnull
    public abstract InfoItemsPage<R> getInitialPage() throws IOException, ExtractionException;

    @Nonnull
    public InfoItemsPage<StreamInfoItem> getFullPage() throws IOException, ExtractionException{
        InfoItemsPage<StreamInfoItem> currentPage = (InfoItemsPage<StreamInfoItem>) getInitialPage();
        ArrayList<StreamInfoItem> itemArrayList = new ArrayList<>(currentPage.getItems());
        ArrayList<Throwable> errors = new ArrayList<>(currentPage.getErrors());
        int filteredCount = currentPage.getFilteredCount();
        while (currentPage.hasNextPage()){
            currentPage = (InfoItemsPage<StreamInfoItem>) getPage(currentPage.getNextPage());
            itemArrayList.addAll(currentPage.getItems());
            errors.addAll(currentPage.getErrors());
            filteredCount += currentPage.getFilteredCount();
        }
        return new InfoItemsPage<>(itemArrayList, null, errors, filteredCount);
    }

    /**
     * Get a list of items corresponding to the specific requested page.
     *
     * @param page any page got from the exclusive implementation of the list extractor
     * @return a {@link InfoItemsPage} corresponding to the requested page
     * @see InfoItemsPage#getNextPage()
     */
    public abstract InfoItemsPage<R> getPage(Page page) throws IOException, ExtractionException;

    @Nonnull
    @Override
    public ListLinkHandler getLinkHandler() {
        return (ListLinkHandler) super.getLinkHandler();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Inner
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * A class that is used to wrap a list of gathered items and eventual errors, it
     * also contains a field that points to the next available page ({@link #nextPage}).
     * @param <T> the info item type that this page is supposed to store and provide
     */
    public static class InfoItemsPage<T extends InfoItem> {
        private static final InfoItemsPage<InfoItem> EMPTY =
                new InfoItemsPage<>(Collections.emptyList(), null, Collections.emptyList());

        /**
         * A convenient method that returns a representation of an empty page.
         *
         * @return a type-safe page with the list of items and errors empty and the nextPage set to
         *         {@code null}.
         */
        public static <T extends InfoItem> InfoItemsPage<T> emptyPage() {
            //noinspection unchecked
            return (InfoItemsPage<T>) EMPTY;
        }

        /**
         * The current list of items of this page
         */
        private final List<T> itemsList;

        /**
         * Url pointing to the next page relative to this one
         *
         * @see ListExtractor#getPage(Page)
         * @see Page
         */
        private final Page nextPage;

        /**
         * Errors that happened during the extraction
         */
        private final List<Throwable> errors;
        private final int filteredCount;

        public InfoItemsPage(final InfoItemsCollector<T, ?> collector, final Page nextPage) {
            this(collector.getItems(), nextPage, collector.getErrors(), collector.getFilteredCount());
        }

        public InfoItemsPage(final List<T> itemsList,
                             Page nextPage,
                             final List<Throwable> errors) {
            this(itemsList, nextPage, errors, 0);
        }

        public InfoItemsPage(final List<T> itemsList,
                             final Page nextPage,
                             final List<Throwable> errors,
                             final int filteredCount) {
            this.itemsList = itemsList;
            // Preserve WizeStream's existing continuation semantics, including empty pages.
            this.nextPage = nextPage;
            this.errors = errors;
            this.filteredCount = filteredCount;
        }

        public int getFilteredCount() {
            return filteredCount;
        }

        public boolean hasNextPage() {
            return Page.isValid(nextPage);
        }

        public List<T> getItems() {
            return itemsList;
        }

        public Page getNextPage() {
            return nextPage;
        }

        public List<Throwable> getErrors() {
            return errors;
        }
    }
}
