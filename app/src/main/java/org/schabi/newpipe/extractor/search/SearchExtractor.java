package org.schabi.newpipe.extractor.search;

import org.schabi.newpipe.extractor.*;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

public abstract class SearchExtractor extends ListExtractor<InfoItem> {

    public static class NothingFoundException extends ExtractionException {
        public NothingFoundException(final String message) {
            super(message);
        }
    }

    public SearchExtractor(final StreamingService service, final SearchQueryHandler linkHandler) {
        super(service, linkHandler);
    }

    public String getSearchString() {
        return getLinkHandler().getSearchString();
    }

    /**
     * The search suggestion provided by the service.
     * <p>
     * This method also returns the corrected query if
     * {@link SearchExtractor#isCorrectedSearch()} is true.
     *
     * @return a suggestion to another query, the corrected query, or an empty String.
     */
    @Nonnull
    public String getSearchSuggestion() throws ParsingException {
        return "";
    }

    @Nonnull
    @Override
    public SearchQueryHandler getLinkHandler() {
        return (SearchQueryHandler) super.getLinkHandler();
    }

    @Nonnull
    @Override
    public String getName() {
        return getLinkHandler().getSearchString();
    }

    /**
     * Tell if the search was corrected by the service (if it's not exactly the search you typed).
     * <p>
     * Example: on YouTube, if you search for "pewdeipie",
     * it will give you results for "pewdiepie", then isCorrectedSearch should return true.
     *
     * @return whether the results comes from a corrected query or not.
     */
    public boolean isCorrectedSearch() throws ParsingException {
        return false;
    }

    /**
     * Meta information about the search query.
     * <p>
     * Example: on YouTube, if you search for "Covid-19",
     * there is a box with information from the WHO about Covid-19 and a link to the WHO's website.
     * @return additional meta information about the search query
     */
    @Nonnull
    public List<MetaInfo> getMetaInfo() throws ParsingException {
        return Collections.emptyList();
    }


    /**
     * Fetches and parses the very first page of search results.
     * This method should NOT apply any filters. Filtering is handled by the public getInitialPage().
     * @return An InfoItemsPage containing the raw items from the first page and a potential next page.
     */
    protected abstract InfoItemsPage<InfoItem> getInitialPageInternal() throws IOException, ExtractionException;

    /**
     * Fetches and parses a subsequent page of search results given a Page object.
     * This method should NOT apply any filters. Filtering is handled by the public getPage().
     * @param page The page descriptor for the page to fetch.
     * @return An InfoItemsPage containing the raw items from that page and a potential next page.
     */
    protected abstract InfoItemsPage<InfoItem> getPageInternal(final Page page) throws IOException, ExtractionException;

    public static final int MAX_SEQUENTIAL_EMPTY_PAGE_FETCHES = 3;

    @Override
    public final InfoItemsPage<InfoItem> getInitialPage() throws IOException, ExtractionException {
        InfoItemsPage<InfoItem> currentPageResult = getInitialPageInternal();
        final List<Throwable> errors = new ArrayList<>(currentPageResult.getErrors());
        int filteredCount = currentPageResult.getFilteredCount();
        int attempts = 0;

        while (attempts < MAX_SEQUENTIAL_EMPTY_PAGE_FETCHES) {
            if (getService().getFilterTypes().contains("search_result")) {
                final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());
                collector.addAll(currentPageResult.getItems());
                collector.applyBlocking(getService().getFilterConfig());
                filteredCount += collector.getFilteredCount();

                // If we have items after filtering, we are done. Return this page.
                if (!collector.getItems().isEmpty()) {
                    return new InfoItemsPage<>(collector.getItems(), currentPageResult.getNextPage(),
                            errors, filteredCount);
                }
            } else {
                // If no filtering is configured, and the page has items, we are done.
                if (!currentPageResult.getItems().isEmpty()) {
                    return new InfoItemsPage<>(currentPageResult.getItems(),
                            currentPageResult.getNextPage(), errors, filteredCount);
                }
            }

            // If we are here, the page was empty (either originally or after filtering).
            // Check if there is a next page to try.
            if (currentPageResult.hasNextPage()) {
                attempts++;
                // Fetch the next page to continue the loop
                currentPageResult = getPageInternal(currentPageResult.getNextPage());
                errors.addAll(currentPageResult.getErrors());
                filteredCount += currentPageResult.getFilteredCount();
            } else {
                // No more pages, break the loop
                break;
            }
        }

        // If we exit the loop, it means we tried enough times or there are no more pages.
        // Return an empty page with no "next page" marker.
        return new InfoItemsPage<>(Collections.emptyList(), null, errors, filteredCount);
    }

    @Override
    public final InfoItemsPage<InfoItem> getPage(final Page page) throws IOException, ExtractionException {
        if (page == null || isNullOrEmpty(page.getUrl())) {
            throw new IllegalArgumentException("Page doesn't contain an URL");
        }

        // The logic is nearly identical to getInitialPage, but starts with a given page
        InfoItemsPage<InfoItem> currentPageResult = getPageInternal(page);
        final List<Throwable> errors = new ArrayList<>(currentPageResult.getErrors());
        int filteredCount = currentPageResult.getFilteredCount();
        int attempts = 0;

        // We start with 1 attempt since the first getPageInternal is one try
        while (attempts < MAX_SEQUENTIAL_EMPTY_PAGE_FETCHES) {
            final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());
            collector.addAll(currentPageResult.getItems());
            if (getService().getFilterTypes().contains("search_result")) {
                collector.applyBlocking(getService().getFilterConfig());
                filteredCount += collector.getFilteredCount();
            }

            if (!collector.getItems().isEmpty()) {
                return new InfoItemsPage<>(collector.getItems(), currentPageResult.getNextPage(),
                            errors, filteredCount);
            }

            if (currentPageResult.hasNextPage()) {
                attempts++;
                currentPageResult = getPageInternal(currentPageResult.getNextPage());
                errors.addAll(currentPageResult.getErrors());
                filteredCount += currentPageResult.getFilteredCount();
            } else {
                break;
            }
        }

        return new InfoItemsPage<>(Collections.emptyList(), null, errors, filteredCount);
    }

}
