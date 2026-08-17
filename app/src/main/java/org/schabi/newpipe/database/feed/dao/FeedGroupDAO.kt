package org.schabi.newpipe.database.feed.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.feed.model.FeedGroupSubscriptionEntity

@Dao
abstract class FeedGroupDAO {

    @Query("SELECT * FROM feed_group ORDER BY sort_order ASC")
    abstract fun getAll(): Flow<List<FeedGroupEntity>>

    @Query("SELECT * FROM feed_group WHERE uid = :groupId")
    abstract suspend fun getGroup(groupId: Long): FeedGroupEntity?

    @Transaction
    open suspend fun insert(feedGroupEntity: FeedGroupEntity): Long {
        val nextSortOrder = nextSortOrder()
        feedGroupEntity.sortOrder = nextSortOrder
        return insertInternal(feedGroupEntity)
    }

    @Update(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun update(feedGroupEntity: FeedGroupEntity): Int

    @Query("DELETE FROM feed_group")
    abstract suspend fun deleteAll(): Int

    @Query("DELETE FROM feed_group WHERE uid = :groupId")
    abstract suspend fun delete(groupId: Long): Int

    @Query("SELECT subscription_id FROM feed_group_subscription_join WHERE group_id = :groupId")
    abstract fun getSubscriptionIdsFor(groupId: Long): Flow<List<Long>>

    @Query("DELETE FROM feed_group_subscription_join WHERE group_id = :groupId")
    abstract suspend fun deleteSubscriptionsFromGroup(groupId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertSubscriptionsToGroup(entities: List<FeedGroupSubscriptionEntity>): List<Long>

    @Transaction
    open suspend fun updateSubscriptionsForGroup(groupId: Long, subscriptionIds: List<Long>) {
        deleteSubscriptionsFromGroup(groupId)
        insertSubscriptionsToGroup(subscriptionIds.map { FeedGroupSubscriptionEntity(groupId, it) })
    }

    @Transaction
    open suspend fun updateOrder(orderMap: Map<Long, Long>) {
        orderMap.forEach { (groupId, sortOrder) -> updateOrder(groupId, sortOrder) }
    }

    @Query("UPDATE feed_group SET sort_order = :sortOrder WHERE uid = :groupId")
    abstract suspend fun updateOrder(groupId: Long, sortOrder: Long): Int

    @Query("SELECT IFNULL(MAX(sort_order) + 1, 0) FROM feed_group")
    protected abstract suspend fun nextSortOrder(): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertInternal(feedGroupEntity: FeedGroupEntity): Long
}
