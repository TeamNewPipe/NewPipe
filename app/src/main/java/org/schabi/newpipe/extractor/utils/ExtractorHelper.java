package org.schabi.newpipe.extractor.utils;

import org.schabi.newpipe.extractor.Info;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.InfoItemsCollector;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.ListInfo;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.comments.CommentsFilterConfig;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ExtractorHelper {
    private ExtractorHelper() {
    }

    public static <T extends InfoItem> InfoItemsPage<T> getItemsPageOrLogError(
            final Info info, final ListExtractor<T> extractor) {
        try {
            final InfoItemsPage<T> page = completeFilteredPage(extractor, extractor.getInitialPage());
            info.addAllErrors(page.getErrors());
            if (info instanceof ListInfo) {
                ((ListInfo<?>) info).setFilteredCount(page.getFilteredCount());
            }

            return page;
        } catch (final Exception e) {
            info.addError(e);
            return InfoItemsPage.emptyPage();
        }
    }

    /** At most three additional requests per load; the caller can resume via nextPage. */
    private static final int MAX_EXTRA_PAGE_FETCHES = 3;

    public static <T extends InfoItem> InfoItemsPage<T> getMoreItems(
            final ListExtractor<T> extractor, final Page page)
            throws IOException, ExtractionException {
        return completeFilteredPage(extractor, extractor.getPage(page));
    }

    private static <T extends InfoItem> InfoItemsPage<T> filterComments(
            final ListExtractor<T> extractor, final InfoItemsPage<T> page) {
        final CommentsFilterConfig config = extractor.getService().getCommentsFilterConfig();
        if (config == null || config.isEmpty()) {
            return page;
        }
        final List<T> items = new ArrayList<>();
        for (final T item : page.getItems()) {
            if (!(item instanceof CommentsInfoItem)
                    || !config.shouldFilter((CommentsInfoItem) item)) {
                items.add(item);
            }
        }
        return new InfoItemsPage<>(items, page.getNextPage(), page.getErrors(),
                page.getFilteredCount() + page.getItems().size() - items.size());
    }

    private static <T extends InfoItem> InfoItemsPage<T> completeFilteredPage(
            final ListExtractor<T> extractor, final InfoItemsPage<T> initial) {
        InfoItemsPage<T> current = filterComments(extractor, initial);
        int filteredCount = current.getFilteredCount();
        final List<Throwable> errors = new ArrayList<>(current.getErrors());
        for (int i = 0; current.getItems().isEmpty() && current.getFilteredCount() > 0
                && current.hasNextPage() && i < MAX_EXTRA_PAGE_FETCHES && errors.isEmpty(); i++) {
            try {
                current = filterComments(extractor, extractor.getPage(current.getNextPage()));
                filteredCount += current.getFilteredCount();
                errors.addAll(current.getErrors());
            } catch (final IOException | ExtractionException e) {
                // Keep the continuation so a manual retry remains possible.
                errors.add(e);
                break;
            }
        }
        return new InfoItemsPage<>(current.getItems(), current.getNextPage(), errors, filteredCount);
    }

    public static InfoItemsPage<StreamInfoItem> getItemsFullPageOrLogError(
            final Info info, final ListExtractor<StreamInfoItem> extractor) {
        try {
            final InfoItemsPage<StreamInfoItem> page = extractor.getFullPage();
            info.addAllErrors(page.getErrors());
            if (info instanceof ListInfo) {
                ((ListInfo<?>) info).setFilteredCount(page.getFilteredCount());
            }
            return page;
        } catch (final Exception e) {
            info.addError(e);
            return InfoItemsPage.emptyPage();
        }
    }


    public static List<InfoItem> getRelatedItemsOrLogError(final StreamInfo info,
                                                           final StreamExtractor extractor) {
        try {
            final InfoItemsCollector<? extends InfoItem, ?> collector = extractor.getRelatedItems();
            if (collector == null) {
                return Collections.emptyList();
            }
            info.addAllErrors(collector.getErrors());

            //noinspection unchecked
            return (List<InfoItem>) collector.getItems();
        } catch (final Exception e) {
            info.addError(e);
            return Collections.emptyList();
        }
    }

    public static List<StreamInfoItem> getPartitionsOrLogError(final StreamInfo info,
                                                               final StreamExtractor extractor) {
        try {
            final InfoItemsCollector<? extends InfoItem, ?> collector = extractor.getPartitions();
            if (collector == null) {
                return Collections.emptyList();
            }
            info.addAllErrors(collector.getErrors());

            //noinspection unchecked
            return (List<StreamInfoItem>) collector.getItems();
        } catch (final Exception e) {
            info.addError(e);
            return Collections.emptyList();
        }
    }

    /**
     * @deprecated Use {@link #getRelatedItemsOrLogError(StreamInfo, StreamExtractor)}
     */
    @Deprecated
    public static List<InfoItem> getRelatedVideosOrLogError(final StreamInfo info,
                                                            final StreamExtractor extractor) {
        return getRelatedItemsOrLogError(info, extractor);
    }

}
