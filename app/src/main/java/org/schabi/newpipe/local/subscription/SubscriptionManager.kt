package org.schabi.newpipe.local.subscription

import android.content.Context
import android.util.Pair
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionDAO
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.Info
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.feed.FeedInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.image.ImageStrategy

class SubscriptionManager(context: Context) {
    private val database = NewPipeDatabase.getInstance(context)
    private val subscriptionTable = database.subscriptionDAO()
    private val feedDatabaseManager = FeedDatabaseManager(context)

    fun subscriptionTable(): SubscriptionDAO = subscriptionTable
    fun subscriptions() = subscriptionTable.all

    fun getSubscriptions(
        currentGroupId: Long = FeedGroupEntity.GROUP_ALL_ID,
        filterQuery: String = "",
        showOnlyUngrouped: Boolean = false
    ): Flowable<List<SubscriptionEntity>> {
        return when {
            filterQuery.isNotEmpty() -> {
                return if (showOnlyUngrouped) {
                    subscriptionTable.getSubscriptionsOnlyUngroupedFiltered(
                        currentGroupId, filterQuery
                    )
                } else {
                    subscriptionTable.getSubscriptionsFiltered(filterQuery)
                }
            }
            showOnlyUngrouped -> subscriptionTable.getSubscriptionsOnlyUngrouped(currentGroupId)
            else -> subscriptionTable.all
        }
    }

    fun upsertAll(infoList: List<Pair<ChannelInfo, List<ChannelTabInfo>>>): List<SubscriptionEntity> {
        val listEntities = subscriptionTable.upsertAll(
            infoList.map { SubscriptionEntity.from(it.first) }
        )

        database.runInTransaction {
            infoList.forEachIndexed { index, info ->
                info.second.forEach {
                    feedDatabaseManager.upsertAll(
                        listEntities[index].uid,
                        it.relatedItems.filterIsInstance<StreamInfoItem>()
                    )
                }
            }
        }

        return listEntities
    }

    fun updateChannelInfo(info: ChannelInfo): Completable =
        subscriptionTable.getSubscription(info.serviceId, info.url)
            .flatMapCompletable {
                Completable.fromRunnable {
                    it.setData(
                        info.name,
                        ImageStrategy.imageListToDbUrl(info.avatars),
                        info.description,
                        info.subscriberCount
                    )
                    subscriptionTable.update(it)
                }
            }

    fun updateNotificationMode(serviceId: Int, url: String, @NotificationMode mode: Int): Completable {
        return subscriptionTable().getSubscription(serviceId, url)
            .flatMapCompletable { entity: SubscriptionEntity ->
                Completable.fromAction {
                    entity.notificationMode = mode
                    subscriptionTable().update(entity)
                }.apply {
                    if (mode != NotificationMode.DISABLED) {
                        // notifications have just been enabled, mark all streams as "old"
                        andThen(rememberAllStreams(entity))
                    }
                }
            }
    }

    fun updateFromInfo(subscriptionId: Long, info: Info) {
        val subscriptionEntity = subscriptionTable.getSubscription(subscriptionId)

        if (info is FeedInfo) {
            subscriptionEntity.name = info.name
        } else if (info is ChannelInfo) {
            subscriptionEntity.setData(
                info.name,
                ImageStrategy.imageListToDbUrl(info.avatars),
                info.description,
                info.subscriberCount
            )
        }

        subscriptionTable.update(subscriptionEntity)
    }

    fun deleteSubscription(serviceId: Int, url: String): Completable {
        return Completable.fromCallable { subscriptionTable.deleteSubscription(serviceId, url) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    fun insertSubscription(subscriptionEntity: SubscriptionEntity) {
        subscriptionTable.insert(subscriptionEntity)
    }

    fun deleteSubscription(subscriptionEntity: SubscriptionEntity) {
        subscriptionTable.delete(subscriptionEntity)
    }

    /**
     * Fetches the list of videos for the provided channel and saves them in the database, so that
     * they will be considered as "old"/"already seen" streams and the user will never be notified
     * about any one of them.
     */
    private fun rememberAllStreams(subscription: SubscriptionEntity): Completable {
        return ExtractorHelper.getChannelInfo(subscription.serviceId, subscription.url, false)
            .flatMap { info ->
                ExtractorHelper.getChannelTab(subscription.serviceId, info.tabs.first(), false)
            }
            .map { channel -> channel.relatedItems.filterIsInstance<StreamInfoItem>().map { stream -> StreamEntity(stream) } }
            .flatMapCompletable { entities ->
                Completable.fromAction {
                    database.streamDAO().upsertAll(entities)
                }
            }.onErrorComplete()
    }
}
