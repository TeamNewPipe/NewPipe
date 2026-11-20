package org.schabi.newpipe.local.feed.service

import java.time.OffsetDateTime
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelTabInfo
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** Keep usable uploads when another tab or a later continuation fails. */
internal object FeedChannelTabLoader {
    fun load(
        tabs: List<ListLinkHandler>,
        fetchFirst: (ListLinkHandler) -> ChannelTabInfo,
        fetchMore: (ListLinkHandler, Page) -> InfoItemsPage<InfoItem>,
        isCancelled: () -> Boolean,
        oldestAllowedDate: OffsetDateTime,
        errors: MutableList<Throwable>,
        maximumContinuationPages: Int = FeedExtractionPlanner.MAX_FEED_CONTINUATION_PAGES_PER_TAB
    ): List<InfoItem> {
        val collected = mutableListOf<InfoItem>()
        for (tab in tabs) {
            if (isCancelled()) break
            try {
                val first = fetchFirst(tab)
                addActionableErrors(errors, first.errors)
                collected.addAll(first.relatedItems)
                var items = first.relatedItems
                var nextPage = first.nextPage
                var pagesLoaded = 0
                while (!isCancelled() && FeedExtractionPlanner.shouldLoadFeedContinuation(
                        pageItemCount = items.size,
                        sourceUploadDates = items.filterIsInstance<StreamInfoItem>()
                            .mapNotNull { it.uploadDate?.offsetDateTime() },
                        hasNextPage = Page.isValid(nextPage),
                        continuationPagesLoaded = pagesLoaded,
                        oldestAllowedDate = oldestAllowedDate,
                        maximumContinuationPages = maximumContinuationPages
                    )
                ) {
                    val page = fetchMore(tab, nextPage ?: break)
                    addActionableErrors(errors, page.errors)
                    collected.addAll(page.items)
                    items = page.items
                    nextPage = page.nextPage
                    pagesLoaded++
                }
            } catch (error: Exception) {
                errors.add(error)
            }
        }
        return collected
    }

    private fun addActionableErrors(
        errors: MutableList<Throwable>,
        candidates: List<Throwable>
    ) {
        errors.addAll(candidates.filterNot(::isUrlLessItemWarning))
    }

    private fun isUrlLessItemWarning(error: Throwable): Boolean = error is ParsingException &&
        error.message == "Could not get url"
}
