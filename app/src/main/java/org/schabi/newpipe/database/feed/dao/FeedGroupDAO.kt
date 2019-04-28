package org.schabi.newpipe.database.feed.dao

import androidx.room.*
import io.reactivex.Flowable
import io.reactivex.Maybe
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.feed.model.FeedGroupSubscriptionEntity

@Dao
abstract class FeedGroupDAO {

    @Query("SELECT * FROM feed_group")
    abstract fun getAll(): Flowable<List<FeedGroupEntity>>

    @Query("SELECT * FROM feed_group WHERE uid = :groupId")
    abstract fun getGroup(groupId: Long): Maybe<FeedGroupEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract fun insert(feedEntity: FeedGroupEntity): Long

    @Update(onConflict = OnConflictStrategy.IGNORE)
    abstract fun update(feedGroupEntity: FeedGroupEntity): Int

    @Query("DELETE FROM feed_group")
    abstract fun deleteAll(): Int

    @Query("DELETE FROM feed_group WHERE uid = :groupId")
    abstract fun delete(groupId: Long): Int

    @Query("SELECT subscription_id FROM feed_group_subscription_join WHERE group_id = :groupId")
    abstract fun getSubscriptionIdsFor(groupId: Long): Flowable<List<Long>>

    @Query("DELETE FROM feed_group_subscription_join WHERE group_id = :groupId")
    abstract fun deleteSubscriptionsFromGroup(groupId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insertSubscriptionsToGroup(entities: List<FeedGroupSubscriptionEntity>): List<Long>

    @Transaction
    open fun updateSubscriptionsForGroup(groupId: Long, subscriptionIds: List<Long>) {
        deleteSubscriptionsFromGroup(groupId)
        insertSubscriptionsToGroup(subscriptionIds.map { FeedGroupSubscriptionEntity(groupId, it) })
    }
}
