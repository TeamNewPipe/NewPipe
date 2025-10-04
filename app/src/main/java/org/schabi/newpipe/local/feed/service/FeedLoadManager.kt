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
import io.reactivex.rxjava3.processors.PublishProcessor
import io.reactivex.rxjava3.schedulers.Schedulers
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
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class FeedLoadManager(private val context: Context) {

    private val subscriptionManager = SubscriptionManager(context)
    private val feedDatabaseManager = FeedDatabaseManager(context)

    private val notificationUpdater = PublishProcessor.create<String>()
    private val currentProgress = AtomicInteger(-1)
    private val maxProgress = AtomicInteger(-1)
    private val cancelSignal = AtomicBoolean()
    private val feedResultsHolder = FeedResultsHolder()

    val notification: Flowable<FeedLoadState> = notificationUpdater.map { description ->
        FeedLoadState(description, maxProgress.get(), currentProgress.get())
    }

    /**
     * Start checking for new streams of a subscription group.
     * @param groupId The ID of the subscription group to load. When using
     * [FeedGroupEntity.GROUP_ALL_ID], all subscriptions are loaded. When using
     * [GROUP_NOTIFICATION_ENABLED], only subscriptions with enabled notifications for new streams
     * are loaded. Using an id of a group created by the user results in that specific group to be
     * loaded.
     * @param ignoreOutdatedThreshold When `false`, only subscriptions which have not been updated
     * within the `feed_update_threshold` are checked for updates. This threshold can be set by
     * the user in the app settings. When `true`, all subscriptions are checked for new streams.
     */
    fun startLoading(
        groupId: Long = FeedGroupEntity.GROUP_ALL_ID,
        ignoreOutdatedThreshold: Boolean = false,
    ): Single<List<Notification<FeedUpdateInfo>>> {
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
        val outdatedSubscriptions = when (groupId) {
            FeedGroupEntity.GROUP_ALL_ID -> feedDatabaseManager.outdatedSubscriptions(
                outdatedThreshold
            )
            GROUP_NOTIFICATION_ENABLED -> feedDatabaseManager.outdatedSubscriptionsWithNotificationMode(
                outdatedThreshold, NotificationMode.ENABLED
            )
            else -> feedDatabaseManager.outdatedSubscriptionsForGroup(groupId, outdatedThreshold)
        }

        // like `currentProgress`, but counts the number of YouTube extractions that have begun, so
        // they can be properly throttled every once in a while (see doOnNext below)
        val youtubeExtractionCount = AtomicInteger()

        return outdatedSubscriptions
            .take(1)
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
            .flatMap { Flowable.fromIterable(it) }
            .takeWhile { !cancelSignal.get() }
            .doOnNext { subscriptionEntity ->
                // throttle YouTube extractions once every BATCH_SIZE to avoid being rate limited
                if (subscriptionEntity.serviceId == ServiceList.YouTube.serviceId) {
                    val previousCount = youtubeExtractionCount.getAndIncrement()
                    if (previousCount != 0 && previousCount % BATCH_SIZE == 0) {
                        Thread.sleep(DELAY_BETWEEN_BATCHES_MILLIS.random())
                    }
                }
            }
            .parallel(PARALLEL_EXTRACTIONS, PARALLEL_EXTRACTIONS * 2)
            .runOn(Schedulers.io(), PARALLEL_EXTRACTIONS * 2)
            .filter { !cancelSignal.get() }
            .map { subscriptionEntity ->
                loadStreams(subscriptionEntity, useFeedExtractor, defaultSharedPreferences)
            }
            .sequential()
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext(NotificationConsumer())
            .observeOn(Schedulers.io())
            .buffer(BUFFER_COUNT_BEFORE_INSERT)
            .doOnNext(DatabaseConsumer())
            .subscribeOn(Schedulers.io())
            .toList()
            .flatMap { x -> postProcessFeed().toSingleDefault(x.flatten()) }
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

    private fun loadStreams(
        subscriptionEntity: SubscriptionEntity,
        useFeedExtractor: Boolean,
        defaultSharedPreferences: SharedPreferences
    ): Notification<FeedUpdateInfo> {
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
                    subscriptionEntity.url, true
                )
                    .onErrorReturn(storeOriginalErrorAndRethrow)
                    .blockingGet()
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
                            getChannelTab(subscriptionEntity.serviceId, it, true)
                                .onErrorReturn(storeOriginalErrorAndRethrow)
                                .blockingGet(),
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
                                linkHandler, channelTabInfo.nextPage
                            )
                                .blockingGet()

                            errors.addAll(infoItemsPage.errors)
                            return@flatMap infoItemsPage.items
                        } else {
                            return@flatMap channelTabInfo.relatedItems
                        }
                    }
                    .filterIsInstance<StreamInfoItem>()
            }

            return Notification.createOnNext(
                FeedUpdateInfo(
                    subscriptionEntity,
                    originalInfo!!,
                    streams!!,
                    errors,
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
     * Remove streams from the feed which are older than [FeedDatabaseManager.FEED_OLDEST_ALLOWED_DATE].
     * Remove streams from the database which are not linked / used by any table.
     */
    private fun postProcessFeed() = Completable.fromRunnable {
        FeedEventManager.postEvent(FeedEventManager.Event.ProgressEvent(R.string.feed_processing_message))
        feedDatabaseManager.removeOrphansOrOlderStreams()

        FeedEventManager.postEvent(FeedEventManager.Event.SuccessResultEvent(feedResultsHolder.itemsErrors))
    }.doOnSubscribe {
        currentProgress.set(-1)
        maxProgress.set(-1)

        notificationUpdater.onNext(context.getString(R.string.feed_processing_message))
        FeedEventManager.postEvent(FeedEventManager.Event.ProgressEvent(R.string.feed_processing_message))
    }.subscribeOn(Schedulers.io())

    private inner class NotificationConsumer : Consumer<Notification<FeedUpdateInfo>> {
        override fun accept(item: Notification<FeedUpdateInfo>) {
            currentProgress.incrementAndGet()
            notificationUpdater.onNext(item.value?.name.orEmpty())

            broadcastProgress()
        }
    }

    private inner class DatabaseConsumer : Consumer<List<Notification<FeedUpdateInfo>>> {

        override fun accept(list: List<Notification<FeedUpdateInfo>>) {
            feedDatabaseManager.database().runInTransaction {
                for (notification in list) {
                    when {
                        notification.isOnNext -> {
                            val info = notification.value!!

                            notification.value!!.newStreams = filterNewStreams(info.streams)

                            feedDatabaseManager.upsertAll(info.uid, info.streams)
                            subscriptionManager.updateFromInfo(info)

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
                                feedDatabaseManager.markAsOutdated(info.uid)
                            }
                        }
                        notification.isOnError -> {
                            val error = notification.error
                            feedResultsHolder.addError(error!!)

                            if (error is FeedLoadService.RequestException) {
                                feedDatabaseManager.markAsOutdated(error.subscriptionId)
                            }
                        }
                    }
                }
            }
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
