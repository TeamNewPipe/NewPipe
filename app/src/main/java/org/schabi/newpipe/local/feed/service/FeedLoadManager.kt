package org.schabi.newpipe.local.feed.service

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Notification
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.processors.BehaviorProcessor
import io.reactivex.rxjava3.processors.PublishProcessor
import io.reactivex.rxjava3.schedulers.Schedulers
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.stream.StreamUploadDate
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.Info
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.feed.FeedExtractor
import org.schabi.newpipe.extractor.feed.FeedInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.ktx.getStringSafe
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.local.feed.FeedScope
import org.schabi.newpipe.local.feed.archive.withFeedArchive
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.plugin.PluginManager
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.ChannelTabHelper
import org.schabi.newpipe.util.ExtractorHelper.getChannelInfo
import org.schabi.newpipe.util.ExtractorHelper.getChannelTab
import org.schabi.newpipe.util.ExtractorHelper.getMoreChannelTabItems
import org.schabi.newpipe.util.SavedServicePolicy

class FeedLoadManager @JvmOverloads constructor(
    private val context: Context,
    private val profileId: String = ProfileManager.getActiveProfileId(context),
    private val notificationRecorder: ((AppDatabase, FeedUpdateInfo, SubscriptionEntity) -> (() -> Unit)?)? = null
) {

    private val subscriptionManager = SubscriptionManager(context, profileId)
    private val feedDatabaseManager = FeedDatabaseManager(context, profileId)

    private val notificationUpdater = PublishProcessor.create<String>()
    private val currentProgress = AtomicInteger(-1)
    private val maxProgress = AtomicInteger(-1)

    @Volatile
    private var currentUpdateDescription = ""
    private val cancelSignal = AtomicBoolean()
    private val cancelNotifier = BehaviorProcessor.createDefault(false)
    private val feedResultsHolder = FeedResultsHolder()
    private var feedScope: FeedScope? = null

    val notification: Flowable<FeedLoadState> = notificationUpdater.map { description ->
        FeedLoadState(description, maxProgress.get(), currentProgress.get())
    }

    /**
     * Start checking for new streams of a subscription group.
     * @param groupId The ID of the subscription group to load. When using
     * [FeedGroupEntity.GROUP_ALL_ID], all subscriptions are loaded. When using
     * [GROUP_NOTIFICATION_ENABLED], only subscriptions with enabled notifications for new streams
     * are loaded. [FeedGroupEntity.GROUP_UNGROUPED_ID] loads subscriptions without a group.
     * Using an id of a group created by the user results in that specific group to be loaded.
     * @param ignoreOutdatedThreshold When `false`, only subscriptions which have not been updated
     * within the `feed_update_threshold` are checked for updates. This threshold can be set by
     * the user in the app settings. When `true`, all subscriptions are checked for new streams.
     */
    fun startLoading(
        groupId: Long = FeedGroupEntity.GROUP_ALL_ID,
        ignoreOutdatedThreshold: Boolean = false,
        scope: FeedScope? = null,
        onlySubscriptions: Set<Long>? = null
    ): Single<List<Notification<FeedUpdateInfo>>> {
        feedScope = scope
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

        /**
         * subscriptions which have not been updated within the feed updated threshold
         */
        val outdatedSubscriptions = when {
            groupId == GROUP_NOTIFICATION_ENABLED ->
                feedDatabaseManager.outdatedSubscriptionsWithNotificationModes(
                    outdatedThreshold,
                    listOf(NotificationMode.ENABLED, NotificationMode.KEYWORDS_ONLY)
                )

            scope == null && groupId == FeedGroupEntity.GROUP_ALL_ID ->
                feedDatabaseManager.outdatedSubscriptions(outdatedThreshold)

            scope == null ->
                feedDatabaseManager.outdatedSubscriptionsForGroup(groupId, outdatedThreshold)

            groupId == FeedGroupEntity.GROUP_ALL_ID ->
                feedDatabaseManager.outdatedSubscriptionsForScope(scope, outdatedThreshold)

            else ->
                feedDatabaseManager.outdatedSubscriptionsForGroupAndScope(
                    groupId,
                    scope,
                    outdatedThreshold
                )
        }

        return outdatedSubscriptions
            .take(1)
            .map { subscriptions ->
                val availableServices = ServiceList.all().mapTo(mutableSetOf()) { it.serviceId }
                subscriptions.filter {
                    (onlySubscriptions == null || it.uid in onlySubscriptions) &&
                        SavedServicePolicy.canRefresh(
                            it.serviceId,
                            availableServices,
                            PluginManager.loadedProfileId,
                            profileId
                        )
                }
            }
            .doOnNext {
                currentProgress.set(0)
                maxProgress.set(it.size)
            }
            .filter { it.isNotEmpty() }
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                notificationUpdater.onNext("")
                broadcastProgress()
            }
            .observeOn(Schedulers.io())
            // Randomize user subscription ordering to attempt to resist fingerprinting
            .flatMap { subscriptions ->
                val requests = subscriptions.shuffled().map { subscription ->
                    createFeedLoadRequest(subscription, useFeedExtractor)
                }
                val (dedicatedFeedRequests, fullExtractionRequests) = requests.partition {
                    it.feedExtractor != null || it.feedExtractorError != null
                }

                Flowable.merge(
                    loadRequests(
                        dedicatedFeedRequests,
                        FeedExtractionPlanner.DEDICATED_FEED_PARALLELISM,
                        defaultSharedPreferences
                    ),
                    loadFullExtractionRequests(
                        fullExtractionRequests,
                        defaultSharedPreferences
                    )
                )
            }
            // Manual UI refreshes consume progress events and database updates, not this result
            // list. Workers requesting specific subscriptions still need the returned streams.
            .let { storeResults(it, retainResults = onlySubscriptions != null || scope == null) }
    }

    /** Serialized persistence shared by manual and scheduled refreshes. */
    internal fun storeResults(
        results: Flowable<Notification<FeedUpdateInfo>>,
        retainResults: Boolean = true
    ): Single<List<Notification<FeedUpdateInfo>>> {
        val persistedResults = results
            .filter { !cancelSignal.get() }
            .observeOn(Schedulers.io())
            // Persist each completed channel before announcing progress. Slow or failed
            // channels must not hold back content already retrieved from other channels.
            .doOnNext(DatabaseConsumer())
            .doOnNext(NotificationConsumer())
            .subscribeOn(Schedulers.io())

        val completion = if (retainResults) {
            persistedResults.toList()
        } else {
            // A manual refresh only needs completion. Do not retain every channel result and its
            // stream list for the entire refresh, especially on low-memory Android TV devices.
            persistedResults.ignoreElements().andThen(Single.just(emptyList()))
        }

        return completion
            .flatMap { results -> postProcessFeed().toSingleDefault(results) }
            .doOnError { cancel() }
            .doOnDispose(::cancel)
    }

    fun cancel() {
        synchronized(cancelSignal) {
            cancelSignal.set(true)
        }
        cancelNotifier.onNext(true)
    }

    private fun createFeedLoadRequest(
        subscriptionEntity: SubscriptionEntity,
        useFeedExtractor: Boolean
    ): FeedLoadRequest {
        if (!useFeedExtractor) {
            return FeedLoadRequest(subscriptionEntity)
        }

        return try {
            FeedLoadRequest(
                subscriptionEntity,
                feedExtractor = NewPipe.getService(subscriptionEntity.serviceId)
                    .getFeedExtractor(subscriptionEntity.url)
            )
        } catch (error: Throwable) {
            FeedLoadRequest(subscriptionEntity, feedExtractorError = error)
        }
    }

    private fun loadFullExtractionRequests(
        requests: List<FeedLoadRequest>,
        defaultSharedPreferences: SharedPreferences
    ): Flowable<Notification<FeedUpdateInfo>> {
        val batches = FeedExtractionPlanner.batchFullExtractions(requests) { request ->
            request.isFullYouTubeExtraction
        }

        return Flowable.fromIterable(batches.withIndex())
            .takeWhile { !cancelSignal.get() }
            .concatMap { (index, batch) ->
                val loadedBatch = loadRequests(
                    batch,
                    FeedExtractionPlanner.FULL_CHANNEL_PARALLELISM,
                    defaultSharedPreferences
                )

                if (index < batches.lastIndex) {
                    loadedBatch.concatWith(cancellableBatchDelay().toFlowable())
                } else {
                    loadedBatch
                }
            }
    }

    private fun loadRequests(
        requests: List<FeedLoadRequest>,
        parallelism: Int,
        defaultSharedPreferences: SharedPreferences
    ): Flowable<Notification<FeedUpdateInfo>> = Flowable.fromIterable(requests)
        .takeWhile { !cancelSignal.get() }
        .flatMapSingle(
            { request ->
                Single.fromCallable { loadStreams(request, defaultSharedPreferences) }
                    .subscribeOn(Schedulers.io())
            },
            false,
            parallelism
        )

    private fun cancellableBatchDelay(): Completable = FeedExtractionPlanner.cancellableDelay(
        DELAY_BETWEEN_BATCHES_MILLIS.random(),
        cancelNotifier,
        Schedulers.computation(),
        cancelSignal::get
    )

    private fun broadcastProgress() {
        postEvent(
            FeedEventManager.Event.ProgressEvent(
                currentProgress.get(),
                maxProgress.get(),
                updateDescription = currentUpdateDescription
            )
        )
    }

    private fun loadStreams(
        request: FeedLoadRequest,
        defaultSharedPreferences: SharedPreferences
    ): Notification<FeedUpdateInfo> {
        val subscriptionEntity = request.subscriptionEntity
        var error: Throwable? = null
        val storeOriginalErrorAndRethrow = { e: Throwable ->
            // keep original to prevent blockingGet() from wrapping it into RuntimeException
            error = e
            throw e
        }

        try {
            // check for and load new streams
            // either by using the dedicated feed method or by getting the channel info
            var originalInfo: Info? = null
            var streams: List<StreamInfoItem>? = null
            val errors = ArrayList<Throwable>()

            request.feedExtractorError?.let { throw it }
            request.feedExtractor?.also { feedExtractor ->
                // the user wants to use a feed extractor and there is one, use it
                val feedInfo = FeedInfo.getInfo(feedExtractor)
                errors.addAll(feedInfo.errors)
                originalInfo = feedInfo
                streams = feedInfo.relatedItems
            }

            if (originalInfo == null) {
                // use the normal channel tabs extractor if either the user wants it, or
                // the current service does not have a dedicated feed extractor

                val channelInfo = getChannelInfo(
                    subscriptionEntity.serviceId,
                    subscriptionEntity.url,
                    true
                )
                    .onErrorReturn(storeOriginalErrorAndRethrow)
                    .blockingGet()
                errors.addAll(channelInfo.errors)
                originalInfo = channelInfo

                streams = FeedChannelTabLoader.load(
                    tabs = channelInfo.tabs.filter { tab ->
                        ChannelTabHelper.fetchFeedChannelTab(context, defaultSharedPreferences, tab)
                    },
                    fetchFirst = { getChannelTab(subscriptionEntity.serviceId, it, true).blockingGet() },
                    fetchMore = { tab, page -> getMoreChannelTabItems(subscriptionEntity.serviceId, tab, page).blockingGet() },
                    isCancelled = cancelSignal::get,
                    oldestAllowedDate = FeedDatabaseManager.oldestAllowedDate(context),
                    errors = errors,
                    maximumContinuationPages = FeedExtractionPlanner.continuationLimit(
                        defaultSharedPreferences.getBoolean(
                            context.getString(R.string.feed_extended_history_key),
                            false
                        )
                    )
                ).filterIsInstance<StreamInfoItem>()
            }

            FeedItemDateResolver.applyApproximateDates(
                streams!!,
                OffsetDateTime.now(ZoneOffset.UTC)
            )

            return Notification.createOnNext(
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
                // do this to prevent blockingGet() from wrapping into RuntimeException
                error ?: e
            )
            return Notification.createOnError(wrapper)
        }
    }

    /**
     * Keep the feed and the stream tables small
     * to reduce loading times when trying to display the feed.
     * <br>
     * Remove streams from the feed which are older than [FeedDatabaseManager.oldestAllowedDate(context)].
     * Remove streams from the database which are not linked / used by any table.
     */
    private fun postProcessFeed() = Completable.fromRunnable {
        postEvent(FeedEventManager.Event.ProgressEvent(R.string.feed_processing_message))
        feedDatabaseManager.removeOrphansOrOlderStreams()

        postEvent(FeedEventManager.Event.SuccessResultEvent(feedResultsHolder.itemsErrors))
    }.doOnSubscribe {
        currentProgress.set(-1)
        maxProgress.set(-1)

        notificationUpdater.onNext(context.getString(R.string.feed_processing_message))
        postEvent(FeedEventManager.Event.ProgressEvent(R.string.feed_processing_message))
    }.subscribeOn(Schedulers.io())

    private fun postEvent(event: FeedEventManager.Event) {
        synchronized(cancelSignal) {
            if (!cancelSignal.get()) {
                feedScope?.let { FeedEventManager.postEvent(it, event) }
            }
        }
    }

    private inner class NotificationConsumer : Consumer<Notification<FeedUpdateInfo>> {
        override fun accept(item: Notification<FeedUpdateInfo>) {
            if (cancelSignal.get()) return
            currentProgress.incrementAndGet()
            currentUpdateDescription = item.value?.name.orEmpty()
            notificationUpdater.onNext(currentUpdateDescription)

            broadcastProgress()
        }
    }

    private inner class DatabaseConsumer : Consumer<Notification<FeedUpdateInfo>> {

        override fun accept(notification: Notification<FeedUpdateInfo>) {
            var archiveItems: List<StreamInfoItem>? = null
            var notificationCommitted: (() -> Unit)? = null
            feedDatabaseManager.database().runInTransaction {
                when {
                    notification.isOnNext -> {
                        val info = notification.value!!
                        // A user can unsubscribe while this channel is still fetching.
                        val subscription = subscriptionManager.getSubscriptionById(info.uid)
                        if (subscription == null) {
                            info.newStreams = emptyList()
                            return@runInTransaction
                        }
                        val updateModeMask = feedScope?.takeUnless { it.serviceId == FeedScope.ALL_SERVICES }?.youtubeModeMask
                            ?: info.youtubeModeMask

                        notification.value!!.newStreams = filterNewStreams(info.streams)
                        // The optional notification snapshot commits atomically with this feed
                        // update. It must not take any coordinator lock while Room owns the DB.
                        notificationCommitted = notificationRecorder?.invoke(feedDatabaseManager.database(), info, subscription)

                        feedDatabaseManager.upsertAll(
                            info.uid,
                            info.streams,
                            updateModeMask,
                            uploaderAvatarUrl = info.avatarUrl,
                            isComplete = info.errors.isEmpty()
                        )
                        subscriptionManager.updateFromInfo(info)
                        archiveItems = info.streams

                        if (info.errors.isNotEmpty()) {
                            feedResultsHolder.addErrors(
                                info.errors.map {
                                    FeedLoadService.RequestException(
                                        info.uid,
                                        "${info.serviceId}:${info.url}",
                                        it
                                    )
                                }
                            )
                            feedDatabaseManager.markAsOutdated(info.uid, updateModeMask)
                        }
                    }

                    notification.isOnError -> {
                        val error = notification.error
                        feedResultsHolder.addError(error!!)

                        if (error is FeedLoadService.RequestException) {
                            val subscription =
                                subscriptionManager.getSubscriptionById(
                                    error.subscriptionId
                                ) ?: return@runInTransaction
                            val updateModeMask = feedScope?.takeUnless { it.serviceId == FeedScope.ALL_SERVICES }?.youtubeModeMask
                                ?: subscription.youtubeModeMask
                            feedDatabaseManager.markAsOutdated(
                                error.subscriptionId,
                                updateModeMask
                            )
                        }
                    }
                }
            }
            notificationCommitted?.invoke()
            // Archive only after the Room write succeeds, without retaining it in the feed UI.
            archiveItems?.let { streams ->
                runCatching {
                    withFeedArchive(context) { archive ->
                        archive.record(profileId, streams)
                    }
                }
            }
        }

        private fun filterNewStreams(list: List<StreamInfoItem>): List<StreamInfoItem> {
            return list.filter {
                !feedDatabaseManager.doesStreamExist(it) &&
                    it.uploadDate != null &&
                    !StreamUploadDate.isSynthetic(it.serviceId, it.uploadDate?.isApproximation, it.textualUploadDate) &&
                    // Streams older than this date are automatically removed from the feed.
                    // Therefore, streams which are not in the database,
                    // but older than this date, are considered old.
                    it.uploadDate!!.offsetDateTime().isAfter(
                        FeedDatabaseManager.oldestAllowedDate(context)
                    )
            }
        }
    }

    companion object {

        /**
         * Constant used to check subscriptions with any enabled notification mode.
         */
        const val GROUP_NOTIFICATION_ENABLED = -2L

        /**
         * Wait a short random delay between full YouTube channel batches to avoid being rate
         * limited. Dedicated RSS feeds are not delayed.
         */
        private val DELAY_BETWEEN_BATCHES_MILLIS = (500L..1500L)
    }
}

private data class FeedLoadRequest(
    val subscriptionEntity: SubscriptionEntity,
    val feedExtractor: FeedExtractor? = null,
    val feedExtractorError: Throwable? = null
) {
    val isFullYouTubeExtraction: Boolean
        get() = FeedExtractionPlanner.isFullYouTubeExtraction(
            isYouTube = subscriptionEntity.serviceId == ServiceList.YouTube.serviceId,
            hasDedicatedFeedExtractor = feedExtractor != null,
            feedExtractorLookupFailed = feedExtractorError != null
        )
}
