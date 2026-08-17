package org.schabi.newpipe.local.feed.service

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.room.withTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.Info
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.feed.FeedInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.ktx.getStringSafe
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.util.ChannelTabHelper
import org.schabi.newpipe.util.ExtractorHelper.getChannelInfo
import org.schabi.newpipe.util.ExtractorHelper.getChannelTab
import org.schabi.newpipe.util.ExtractorHelper.getMoreChannelTabItems

class FeedLoadManager(private val context: Context) {

    private val subscriptionManager = SubscriptionManager(context)
    private val feedDatabaseManager = FeedDatabaseManager(context)

    private val notificationUpdater = MutableStateFlow("")
    private val currentProgress = AtomicInteger(-1)
    private val maxProgress = AtomicInteger(-1)
    private val cancelSignal = AtomicBoolean()
    private val feedResultsHolder = FeedResultsHolder()

    val notification: Flow<FeedLoadState> = notificationUpdater.map { description ->
        FeedLoadState(description, maxProgress.get(), currentProgress.get())
    }

    /**
     * Start checking for new streams of a subscription group.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun startLoading(
        groupId: Long = FeedGroupEntity.GROUP_ALL_ID,
        ignoreOutdatedThreshold: Boolean = false
    ): List<FeedResult<FeedUpdateInfo>> {
        val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val useFeedExtractor = defaultSharedPreferences.getBoolean(
            context.getString(R.string.feed_use_dedicated_fetch_method_key),
            false
        )

        val outdatedThreshold = if (ignoreOutdatedThreshold) {
            OffsetDateTime.now(ZoneOffset.UTC)
        } else {
            val thresholdOutdatedSeconds = defaultSharedPreferences.getStringSafe(
                context.getString(R.string.feed_update_threshold_key),
                context.getString(R.string.feed_update_threshold_default_value)
            ).toInt()
            OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(thresholdOutdatedSeconds.toLong())
        }

        val outdatedSubscriptionsFlow = when (groupId) {
            FeedGroupEntity.GROUP_ALL_ID -> feedDatabaseManager.outdatedSubscriptions(outdatedThreshold)

            GROUP_NOTIFICATION_ENABLED -> feedDatabaseManager.outdatedSubscriptionsWithNotificationMode(
                outdatedThreshold,
                NotificationMode.ENABLED
            )

            else -> feedDatabaseManager.outdatedSubscriptionsForGroup(groupId, outdatedThreshold)
        }

        val subscriptions = outdatedSubscriptionsFlow.first()
        if (subscriptions.isEmpty()) {
            return emptyList()
        }

        currentProgress.set(0)
        maxProgress.set(subscriptions.size)
        notificationUpdater.value = ""
        broadcastProgress()

        val youtubeExtractionCount = AtomicInteger()

        val results = subscriptions.shuffled().asFlow()
            .filter { !cancelSignal.get() }
            .onEach { subscriptionEntity ->
                if (subscriptionEntity.serviceId == ServiceList.YouTube.serviceId) {
                    val previousCount = youtubeExtractionCount.getAndIncrement()
                    if (previousCount != 0 && previousCount % BATCH_SIZE == 0) {
                        delay(DELAY_BETWEEN_BATCHES_MILLIS.random())
                    }
                }
            }
            .flatMapMerge(concurrency = PARALLEL_EXTRACTIONS) { subscriptionEntity ->
                kotlinx.coroutines.flow.flow {
                    emit(loadStreams(subscriptionEntity, useFeedExtractor, defaultSharedPreferences))
                }
            }
            .onEach { result ->
                currentProgress.incrementAndGet()
                notificationUpdater.value = result.value?.name.orEmpty()
                broadcastProgress()
            }
            .toList()

        // Batch processing results into DB
        results.chunked(BUFFER_COUNT_BEFORE_INSERT).forEach { batch ->
            feedDatabaseManager.database().withTransaction {
                for (result in batch) {
                    if (result.isOnNext) {
                        val info = result.value!!
                        info.newStreams = filterNewStreams(info.streams)
                        feedDatabaseManager.upsertAll(info.uid, info.streams)
                        subscriptionManager.updateFromInfo(info)

                        if (info.errors.isNotEmpty()) {
                            feedResultsHolder.addErrors(
                                info.errors.map {
                                    FeedLoadService.RequestException(info.uid, "${info.serviceId}:${info.url}", it)
                                }
                            )
                            feedDatabaseManager.markAsOutdated(info.uid)
                        }
                    } else if (result.isOnError) {
                        val error = result.error!!
                        feedResultsHolder.addError(error)
                        if (error is FeedLoadService.RequestException) {
                            feedDatabaseManager.markAsOutdated(error.subscriptionId)
                        }
                    }
                }
            }
        }

        postProcessFeed()
        return results
    }

    fun cancel() {
        cancelSignal.set(true)
    }

    private fun broadcastProgress() {
        FeedEventManager.postEvent(
            FeedEventManager.Event.ProgressEvent(
                currentProgress.get(),
                maxProgress.get()
            )
        )
    }

    private suspend fun loadStreams(
        subscriptionEntity: SubscriptionEntity,
        useFeedExtractor: Boolean,
        defaultSharedPreferences: SharedPreferences
    ): FeedResult<FeedUpdateInfo> {
        return withContext(Dispatchers.IO) {
            try {
                // check for and load new streams
                // either by using the dedicated feed method or by getting the channel info
                var originalInfo: Info? = null
                var streams: List<StreamInfoItem>? = null
                val errors = ArrayList<Throwable>()

                if (useFeedExtractor) {
                    NewPipe.getService(subscriptionEntity.serviceId)
                        .getFeedExtractor(subscriptionEntity.url)
                        ?.also { feedExtractor ->
                            // the user wants to use a feed extractor and there is one, use it
                            val feedInfo = FeedInfo.getInfo(feedExtractor)
                            errors.addAll(feedInfo.errors)
                            originalInfo = feedInfo
                            streams = feedInfo.relatedItems
                        }
                }

                if (originalInfo == null) {
                    // use the normal channel tabs extractor if either the user wants it, or
                    // the current service does not have a dedicated feed extractor

                    val channelInfo = getChannelInfo(
                        subscriptionEntity.serviceId,
                        subscriptionEntity.url!!,
                        true
                    )
                    errors.addAll(channelInfo.errors)
                    originalInfo = channelInfo

                    streams = channelInfo.tabs
                        .filter { tab ->
                            ChannelTabHelper.fetchFeedChannelTab(
                                context,
                                defaultSharedPreferences,
                                tab
                            )
                        }
                        .map {
                            Pair(
                                getChannelTab(subscriptionEntity.serviceId, it, true),
                                it
                            )
                        }
                        .flatMap { (channelTabInfo, linkHandler) ->
                            errors.addAll(channelTabInfo.errors)
                            if (channelTabInfo.relatedItems.isEmpty() &&
                                channelTabInfo.nextPage != null
                            ) {
                                val infoItemsPage = getMoreChannelTabItems(
                                    subscriptionEntity.serviceId,
                                    linkHandler,
                                    channelTabInfo.nextPage
                                )

                                errors.addAll(infoItemsPage.errors)
                                infoItemsPage.items
                            } else {
                                channelTabInfo.relatedItems
                            }
                        }
                        .filterIsInstance<StreamInfoItem>()
                }

                FeedResult.createOnNext(
                    FeedUpdateInfo(
                        subscriptionEntity,
                        originalInfo!!,
                        streams!!,
                        errors
                    )
                )
            } catch (e: Throwable) {
                val request = "${subscriptionEntity.serviceId}:${subscriptionEntity.url}"
                val wrapper = FeedLoadService.RequestException(
                    subscriptionEntity.uid,
                    request,
                    e
                )
                FeedResult.createOnError(wrapper)
            }
        }
    }

    private suspend fun postProcessFeed() {
        currentProgress.set(-1)
        maxProgress.set(-1)
        notificationUpdater.value = context.getString(R.string.feed_processing_message)
        FeedEventManager.postEvent(FeedEventManager.Event.ProgressEvent(R.string.feed_processing_message))

        feedDatabaseManager.removeOrphansOrOlderStreams()
        FeedEventManager.postEvent(FeedEventManager.Event.SuccessResultEvent(feedResultsHolder.itemsErrors))
    }

    private fun filterNewStreams(list: List<StreamInfoItem>): List<StreamInfoItem> {
        return list.filter {
            !feedDatabaseManager.doesStreamExist(it) &&
                it.uploadDate != null &&
                // Streams older than this date are automatically removed from the feed.
                // Therefore, streams which are not in the database,
                // but older than this date, are considered old.
                it.uploadDate!!.offsetDateTime().isAfter(
                    FeedDatabaseManager.FEED_OLDEST_ALLOWED_DATE
                )
        }
    }

    // Helper class to mimic RxJava Notification
    class FeedResult<T>(val value: T?, val error: Throwable?) {
        val isOnNext get() = error == null
        val isOnError get() = error != null
        companion object {
            fun <T> createOnNext(value: T) = FeedResult(value, null)
            fun <T> createOnError(error: Throwable) = FeedResult<T>(null, error)
        }
    }

    companion object {
        /**
         * Constant used to check for updates of subscriptions with [NotificationMode.ENABLED].
         */
        const val GROUP_NOTIFICATION_ENABLED = -2L

        /**
         * How many extractions will be running in parallel.
         */
        private const val PARALLEL_EXTRACTIONS = 3

        /**
         * How many YouTube extractions to perform before waiting [DELAY_BETWEEN_BATCHES_MILLIS]
         * to avoid being rate limited
         */
        private const val BATCH_SIZE = 50

        /**
         * Wait a random delay in this range once every [BATCH_SIZE] YouTube extractions to avoid
         * being rate limited
         */
        private val DELAY_BETWEEN_BATCHES_MILLIS = (6000L..12000L)

        /**
         * Number of items to buffer to mass-insert in the database.
         */
        private const val BUFFER_COUNT_BEFORE_INSERT = 20
    }
}
