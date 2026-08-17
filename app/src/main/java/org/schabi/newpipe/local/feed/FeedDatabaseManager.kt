package org.schabi.newpipe.local.feed

import android.content.Context
import android.util.Log
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import org.schabi.newpipe.DebugConstants.DEBUG
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.feed.model.FeedEntity
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.feed.model.FeedLastUpdatedEntity
import org.schabi.newpipe.database.stream.StreamWithState
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.subscription.FeedGroupIcon

class FeedDatabaseManager(context: Context) {
    private val database = NewPipeDatabase.getInstance(context)
    private val feedTable = database.feedDAO()
    private val feedGroupTable = database.feedGroupDAO()
    private val streamTable = database.streamDAO()

    companion object {
        /**
         * Only items that are newer than this will be saved.
         */
        val FEED_OLDEST_ALLOWED_DATE: OffsetDateTime = LocalDate.now().minusWeeks(13)
            .atStartOfDay().atOffset(ZoneOffset.UTC)
    }

    fun groups(): Flow<List<FeedGroupEntity>> = feedGroupTable.getAll()

    fun database() = database

    suspend fun getStreams(
        groupId: Long,
        includePlayedStreams: Boolean,
        includePartiallyPlayedStreams: Boolean,
        includeFutureStreams: Boolean
    ): List<StreamWithState> {
        return feedTable.getStreams(
            groupId,
            includePlayedStreams,
            includePartiallyPlayedStreams,
            if (includeFutureStreams) null else OffsetDateTime.now()
        )
    }

    fun outdatedSubscriptions(outdatedThreshold: OffsetDateTime): Flow<List<SubscriptionEntity>> = feedTable.getAllOutdated(outdatedThreshold)

    fun outdatedSubscriptionsWithNotificationMode(
        outdatedThreshold: OffsetDateTime,
        @NotificationMode notificationMode: Int
    ): Flow<List<SubscriptionEntity>> = feedTable.getOutdatedWithNotificationMode(outdatedThreshold, notificationMode)

    fun notLoadedCount(groupId: Long = FeedGroupEntity.GROUP_ALL_ID): Flow<Long> {
        return when (groupId) {
            FeedGroupEntity.GROUP_ALL_ID -> feedTable.notLoadedCount()
            else -> feedTable.notLoadedCountForGroup(groupId)
        }
    }

    fun outdatedSubscriptionsForGroup(
        groupId: Long = FeedGroupEntity.GROUP_ALL_ID,
        outdatedThreshold: OffsetDateTime
    ): Flow<List<SubscriptionEntity>> = feedTable.getAllOutdatedForGroup(groupId, outdatedThreshold)

    suspend fun markAsOutdated(subscriptionId: Long) = feedTable
        .setLastUpdatedForSubscription(FeedLastUpdatedEntity(subscriptionId, null))

    fun doesStreamExist(stream: StreamInfoItem): Boolean {
        return streamTable.exists(stream.serviceId, stream.url)
    }

    suspend fun upsertAll(
        subscriptionId: Long,
        items: List<StreamInfoItem>,
        oldestAllowedDate: OffsetDateTime = FEED_OLDEST_ALLOWED_DATE
    ) {
        val itemsToInsert = items.mapNotNull { stream ->
            val uploadDate = stream.uploadDate

            when {
                uploadDate == null && stream.streamType == StreamType.LIVE_STREAM -> stream
                uploadDate != null && uploadDate.offsetDateTime() >= oldestAllowedDate -> stream
                else -> null
            }
        }

        feedTable.unlinkOldLivestreams(subscriptionId)

        if (itemsToInsert.isNotEmpty()) {
            val streamEntities = itemsToInsert.map { StreamEntity(it) }
            val streamIds = streamTable.upsertAll(streamEntities)
            val feedEntities = streamIds.map { FeedEntity(it, subscriptionId) }

            feedTable.insertAll(feedEntities)
        }

        feedTable.setLastUpdatedForSubscription(
            FeedLastUpdatedEntity(subscriptionId, OffsetDateTime.now(ZoneOffset.UTC))
        )
    }

    suspend fun removeOrphansOrOlderStreams(oldestAllowedDate: OffsetDateTime = FEED_OLDEST_ALLOWED_DATE) {
        feedTable.unlinkStreamsOlderThan(oldestAllowedDate)
        streamTable.deleteOrphans()
    }

    suspend fun clear() {
        feedTable.deleteAll()
        val deletedOrphans = streamTable.deleteOrphans()
        if (DEBUG) {
            Log.d(
                this::class.java.simpleName,
                "clear() → streamTable.deleteOrphans() → $deletedOrphans"
            )
        }
    }

    // /////////////////////////////////////////////////////////////////////////
    // Feed Groups
    // /////////////////////////////////////////////////////////////////////////

    fun subscriptionIdsForGroup(groupId: Long): Flow<List<Long>> {
        return feedGroupTable.getSubscriptionIdsFor(groupId)
    }

    suspend fun updateSubscriptionsForGroup(groupId: Long, subscriptionIds: List<Long>) {
        feedGroupTable.updateSubscriptionsForGroup(groupId, subscriptionIds)
    }

    suspend fun createGroup(name: String, icon: FeedGroupIcon): Long {
        return feedGroupTable.insert(FeedGroupEntity(0, name, icon))
    }

    suspend fun getGroup(groupId: Long): FeedGroupEntity? {
        return feedGroupTable.getGroup(groupId)
    }

    suspend fun updateGroup(feedGroupEntity: FeedGroupEntity): Int {
        return feedGroupTable.update(feedGroupEntity)
    }

    suspend fun deleteGroup(groupId: Long): Int {
        return feedGroupTable.delete(groupId)
    }

    suspend fun updateGroupsOrder(groupIdList: List<Long>) {
        var index = 0L
        val orderMap = groupIdList.associateBy({ it }, { index++ })
        feedGroupTable.updateOrder(orderMap)
    }

    fun oldestSubscriptionUpdate(groupId: Long): Flow<List<OffsetDateTime?>> {
        return when (groupId) {
            FeedGroupEntity.GROUP_ALL_ID -> feedTable.oldestSubscriptionUpdateFromAll()
            else -> feedTable.oldestSubscriptionUpdate(groupId)
        }
    }
}
