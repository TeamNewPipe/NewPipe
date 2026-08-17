package org.schabi.newpipe.local.subscription

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionDAO
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.local.feed.service.FeedUpdateInfo
import org.schabi.newpipe.util.ExtractorHelper
import org.schabi.newpipe.util.image.ImageStrategy

class SubscriptionManager(context: Context) {
    private val database = NewPipeDatabase.getInstance(context)
    private val subscriptionTable = database.subscriptionDAO()
    private val feedDatabaseManager = FeedDatabaseManager(context)

    fun subscriptionTable(): SubscriptionDAO = subscriptionTable
    fun subscriptions(): Flow<List<SubscriptionEntity>> = subscriptionTable.getAll()

    fun getSubscriptions(
        currentGroupId: Long = FeedGroupEntity.GROUP_ALL_ID,
        filterQuery: String = "",
        showOnlyUngrouped: Boolean = false
    ): Flow<List<SubscriptionEntity>> {
        return when {
            filterQuery.isNotEmpty() -> {
                if (showOnlyUngrouped) {
                    subscriptionTable.getSubscriptionsOnlyUngroupedFiltered(
                        currentGroupId,
                        filterQuery
                    )
                } else {
                    subscriptionTable.getSubscriptionsFiltered(filterQuery)
                }
            }

            showOnlyUngrouped -> subscriptionTable.getSubscriptionsOnlyUngrouped(currentGroupId)

            else -> subscriptionTable.getAll()
        }
    }

    suspend fun upsertAll(infoList: List<Pair<ChannelInfo, ChannelTabInfo?>>) {
        val listEntities = infoList.map { SubscriptionEntity.from(it.first) }
        subscriptionTable.upsertAll(listEntities)

        database.withTransaction {
            infoList.forEachIndexed { index, info ->
                // There may be no tabs on the channel to refresh the feed from
                val channelTabInfo = info.second ?: return@forEachIndexed

                val streams = channelTabInfo.relatedItems.filterIsInstance<StreamInfoItem>()
                feedDatabaseManager.upsertAll(listEntities[index].uid, streams)
            }
        }
    }

    suspend fun updateChannelInfo(info: ChannelInfo) {
        val entity = subscriptionTable.getSubscription(info.serviceId, info.url) ?: return
        entity.apply {
            name = info.name
            avatarUrl = ImageStrategy.imageListToDbUrl(info.avatars)
            description = info.description
            subscriberCount = info.subscriberCount
        }
        subscriptionTable.update(entity)
    }

    suspend fun updateNotificationMode(serviceId: Int, url: String, @NotificationMode mode: Int) {
        val entity = subscriptionTable().getSubscription(serviceId, url) ?: return
        entity.notificationMode = mode
        subscriptionTable().update(entity)

        if (mode != NotificationMode.DISABLED) {
            // notifications have just been enabled, mark all streams as "old"
            try {
                rememberAllStreams(entity)
            } catch (e: Exception) {
                // Equivalent to onErrorComplete()
            }
        }
    }

    suspend fun updateFromInfo(info: FeedUpdateInfo) {
        val subscriptionEntity = subscriptionTable.getSubscription(info.uid) ?: return

        subscriptionEntity.name = info.name

        // some services do not provide an avatar URL
        info.avatarUrl?.let { subscriptionEntity.avatarUrl = it }

        // these two fields are null if the feed info was fetched using the fast feed method
        info.description?.let { subscriptionEntity.description = it }
        info.subscriberCount?.let { subscriptionEntity.subscriberCount = it }

        subscriptionTable.update(subscriptionEntity)
    }

    suspend fun deleteSubscription(serviceId: Int, url: String): Int {
        return subscriptionTable.deleteSubscription(serviceId, url)
    }

    suspend fun insertSubscription(subscriptionEntity: SubscriptionEntity) {
        subscriptionTable.insert(subscriptionEntity)
    }

    suspend fun deleteSubscription(subscriptionEntity: SubscriptionEntity) {
        subscriptionTable.delete(subscriptionEntity)
    }

    /**
     * Fetches the list of videos for the provided channel and saves them in the database, so that
     * they will be considered as "old"/"already seen" streams and the user will never be notified
     * about any one of them.
     */
    private suspend fun rememberAllStreams(subscription: SubscriptionEntity) {
        val info = ExtractorHelper.getChannelInfo(subscription.serviceId, subscription.url!!, false)
        val channel = ExtractorHelper.getChannelTab(subscription.serviceId, info.tabs.first(), false)
        val entities = channel.relatedItems.filterIsInstance<StreamInfoItem>().map { StreamEntity(it) }
        database.streamDAO().upsertAll(entities)
    }
}
