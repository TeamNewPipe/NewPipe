package org.schabi.newpipe.local.feed.service

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Scheduler
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

internal object FeedExtractionPlanner {
    const val DEDICATED_FEED_PARALLELISM = 5
    const val FULL_CHANNEL_PARALLELISM = 3
    const val MAX_FEED_CONTINUATION_PAGES_PER_TAB = 8
    const val EXTENDED_FEED_CONTINUATION_PAGES_PER_TAB = 24

    /** Extended history is a deliberate opt-in to avoid extra YouTube requests by default. */
    fun continuationLimit(extendedHistory: Boolean): Int = if (extendedHistory) {
        EXTENDED_FEED_CONTINUATION_PAGES_PER_TAB
    } else {
        MAX_FEED_CONTINUATION_PAGES_PER_TAB
    }
    private const val YOUTUBE_FULL_EXTRACTION_BATCH_SIZE = 50

    fun isFullYouTubeExtraction(
        isYouTube: Boolean,
        hasDedicatedFeedExtractor: Boolean,
        feedExtractorLookupFailed: Boolean
    ): Boolean = isYouTube && !hasDedicatedFeedExtractor && !feedExtractorLookupFailed

    fun cancellableDelay(
        delayMillis: Long,
        cancelNotifier: Flowable<Boolean>,
        scheduler: Scheduler,
        isCancelled: () -> Boolean
    ): Completable = Flowable.timer(delayMillis, TimeUnit.MILLISECONDS, scheduler)
        .takeUntil(cancelNotifier.filter { it })
        .filter { !isCancelled() }
        .ignoreElements()

    fun shouldLoadFeedContinuation(
        pageItemCount: Int,
        sourceUploadDates: List<OffsetDateTime>,
        hasNextPage: Boolean,
        continuationPagesLoaded: Int,
        oldestAllowedDate: OffsetDateTime,
        maximumContinuationPages: Int = MAX_FEED_CONTINUATION_PAGES_PER_TAB
    ): Boolean {
        if (!hasNextPage ||
            continuationPagesLoaded >= maximumContinuationPages.coerceIn(
                0,
                EXTENDED_FEED_CONTINUATION_PAGES_PER_TAB
            )
        ) {
            return false
        }

        // Preserve the existing workaround for services that expose an empty first page before
        // their first usable continuation.
        if (pageItemCount == 0) {
            return true
        }

        // Undated pages (notably Shorts) cannot prove where they sit in publication order, so do
        // not crawl them repeatedly. Dated channel tabs can safely continue until the retention
        // boundary is reached.
        // A pinned old upload does not prove that the remaining page is older than retention.
        val newestSourceDate = sourceUploadDates.maxOrNull() ?: return false
        return !newestSourceDate.isBefore(oldestAllowedDate)
    }

    /**
     * Split full-channel requests immediately before the next YouTube request after each batch.
     * Non-YouTube requests do not count toward the limit and remain in the current batch.
     */
    fun <T> batchFullExtractions(
        requests: List<T>,
        isFullYouTubeExtraction: (T) -> Boolean
    ): List<List<T>> {
        if (requests.isEmpty()) {
            return emptyList()
        }

        val batches = mutableListOf<MutableList<T>>()
        var currentBatch = mutableListOf<T>()
        var youtubeExtractionsInBatch = 0

        requests.forEach { request ->
            if (isFullYouTubeExtraction(request) &&
                youtubeExtractionsInBatch == YOUTUBE_FULL_EXTRACTION_BATCH_SIZE
            ) {
                batches += currentBatch
                currentBatch = mutableListOf()
                youtubeExtractionsInBatch = 0
            }

            currentBatch += request
            if (isFullYouTubeExtraction(request)) {
                youtubeExtractionsInBatch++
            }
        }

        batches += currentBatch
        return batches
    }
}
