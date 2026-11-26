package org.schabi.newpipe.database.feed.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.feed.model.FeedGroupSubscriptionEntity

@Dao
abstract class FeedGroupDAO {

    @Query("SELECT * FROM feed_group ORDER BY sort_order ASC")
    abstract fun getAll(): Flowable<List<FeedGroupEntity>>

    @Query(
        """
        SELECT * FROM feed_group
        WHERE profile_id = :profileId
        ORDER BY sort_order ASC
        """
    )
    abstract fun getAllForProfile(profileId: String): Flowable<List<FeedGroupEntity>>

    @Query("SELECT * FROM feed_group ORDER BY sort_order ASC")
    abstract fun getAllDirect(): List<FeedGroupEntity>

    @Query(
        """
        SELECT * FROM feed_group
        WHERE profile_id = :profileId
        ORDER BY sort_order ASC
        """
    )
    abstract fun getAllDirectForProfile(profileId: String): List<FeedGroupEntity>

    @Query("SELECT * FROM feed_group WHERE uid = :groupId")
    abstract fun getGroup(groupId: Long): Maybe<FeedGroupEntity>

    @Query(
        """
        SELECT * FROM feed_group
        WHERE profile_id = :profileId AND uid = :groupId
        """
    )
    abstract fun getGroupForProfile(
        profileId: String,
        groupId: Long
    ): Maybe<FeedGroupEntity>

    @Query("SELECT * FROM feed_group WHERE uid = :groupId")
    abstract fun getGroupDirect(groupId: Long): FeedGroupEntity?

    @Query(
        """
        SELECT * FROM feed_group
        WHERE profile_id = :profileId AND uid = :groupId
        """
    )
    abstract fun getGroupDirectForProfile(
        profileId: String,
        groupId: Long
    ): FeedGroupEntity?

    @Transaction
    open fun insert(feedGroupEntity: FeedGroupEntity): Long {
        val nextSortOrder = nextSortOrder(feedGroupEntity.profileId)
        feedGroupEntity.sortOrder = nextSortOrder
        return insertInternal(feedGroupEntity)
    }

    @Update(onConflict = OnConflictStrategy.IGNORE)
    abstract fun update(feedGroupEntity: FeedGroupEntity): Int

    @Transaction
    open fun updateForProfile(
        profileId: String,
        feedGroupEntity: FeedGroupEntity
    ): Int {
        if (feedGroupEntity.profileId != profileId ||
            !groupBelongsToProfile(profileId, feedGroupEntity.uid)
        ) {
            return 0
        }
        return update(feedGroupEntity)
    }

    @Query("DELETE FROM feed_group")
    abstract fun deleteAll(): Int

    @Query("DELETE FROM feed_group WHERE profile_id = :profileId")
    abstract fun deleteAllForProfile(profileId: String): Int

    @Query("DELETE FROM feed_group WHERE uid = :groupId")
    abstract fun delete(groupId: Long): Int

    @Query(
        """
        DELETE FROM feed_group
        WHERE profile_id = :profileId AND uid = :groupId
        """
    )
    abstract fun deleteForProfile(profileId: String, groupId: Long): Int

    @Query("SELECT subscription_id FROM feed_group_subscription_join WHERE group_id = :groupId")
    abstract fun getSubscriptionIdsFor(groupId: Long): Flowable<List<Long>>

    @Query(
        """
        SELECT fgs.subscription_id
        FROM feed_group_subscription_join fgs
        INNER JOIN feed_group g
            ON g.uid = fgs.group_id
        INNER JOIN subscriptions s
            ON s.uid = fgs.subscription_id
        WHERE g.profile_id = :profileId
        AND s.profile_id = :profileId
        AND fgs.group_id = :groupId
        """
    )
    abstract fun getSubscriptionIdsForProfile(
        profileId: String,
        groupId: Long
    ): Flowable<List<Long>>

    @Query("SELECT subscription_id FROM feed_group_subscription_join WHERE group_id = :groupId")
    abstract fun getSubscriptionIdsForDirect(groupId: Long): List<Long>

    @Query(
        """
        SELECT fgs.group_id
        FROM feed_group_subscription_join fgs
        INNER JOIN feed_group g
            ON g.uid = fgs.group_id
        INNER JOIN subscriptions s
            ON s.uid = fgs.subscription_id
        WHERE g.profile_id = :profileId
        AND s.profile_id = :profileId
        AND fgs.subscription_id = :subscriptionId
        ORDER BY g.sort_order ASC
        """
    )
    abstract fun getGroupIdsForSubscriptionForProfile(
        profileId: String,
        subscriptionId: Long
    ): Flowable<List<Long>>

    @Query(
        """
        SELECT fgs.group_id
        FROM feed_group_subscription_join fgs
        INNER JOIN feed_group g ON g.uid = fgs.group_id
        INNER JOIN subscriptions s ON s.uid = fgs.subscription_id
        WHERE g.profile_id = :profileId AND s.profile_id = :profileId
        AND fgs.subscription_id = :subscriptionId
        ORDER BY g.sort_order ASC
        """
    )
    abstract fun getGroupIdsForSubscriptionDirectForProfile(
        profileId: String,
        subscriptionId: Long
    ): List<Long>

    @Query(
        """
        SELECT fgs.subscription_id
        FROM feed_group_subscription_join fgs
        INNER JOIN feed_group g
            ON g.uid = fgs.group_id
        INNER JOIN subscriptions s
            ON s.uid = fgs.subscription_id
        WHERE g.profile_id = :profileId
        AND s.profile_id = :profileId
        AND fgs.group_id = :groupId
        """
    )
    abstract fun getSubscriptionIdsForDirectForProfile(
        profileId: String,
        groupId: Long
    ): List<Long>

    @Query("DELETE FROM feed_group_subscription_join WHERE group_id = :groupId")
    abstract fun deleteSubscriptionsFromGroup(groupId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insertSubscriptionsToGroup(
        entities: List<FeedGroupSubscriptionEntity>
    ): List<Long>

    @Query(
        """
        SELECT uid FROM subscriptions
        WHERE profile_id = :profileId
        AND uid IN (:subscriptionIds)
        """
    )
    protected abstract fun validSubscriptionIds(
        profileId: String,
        subscriptionIds: List<Long>
    ): List<Long>

    @Transaction
    open fun updateSubscriptionsForGroup(groupId: Long, subscriptionIds: List<Long>) {
        deleteSubscriptionsFromGroup(groupId)
        insertSubscriptionsToGroup(
            subscriptionIds.map { FeedGroupSubscriptionEntity(groupId, it) }
        )
    }

    @Transaction
    open fun updateSubscriptionsForGroupForProfile(
        profileId: String,
        groupId: Long,
        subscriptionIds: List<Long>
    ) {
        if (!groupBelongsToProfile(profileId, groupId)) {
            return
        }
        deleteSubscriptionsFromGroup(groupId)
        val validIds = if (subscriptionIds.isEmpty()) {
            emptyList()
        } else {
            validSubscriptionIds(profileId, subscriptionIds)
        }
        insertSubscriptionsToGroup(
            validIds.map { FeedGroupSubscriptionEntity(groupId, it) }
        )
    }

    @Query(
        """
        DELETE FROM feed_group_subscription_join
        WHERE subscription_id = :subscriptionId
        AND group_id IN (
            SELECT uid FROM feed_group WHERE profile_id = :profileId
        )
        """
    )
    protected abstract fun deleteGroupsForSubscriptionForProfile(
        profileId: String,
        subscriptionId: Long
    ): Int

    @Query(
        """
        SELECT uid FROM feed_group
        WHERE profile_id = :profileId
        AND uid IN (:groupIds)
        """
    )
    protected abstract fun validGroupIds(
        profileId: String,
        groupIds: List<Long>
    ): List<Long>

    @Transaction
    open fun setGroupsForSubscriptionForProfile(
        profileId: String,
        subscriptionId: Long,
        groupIds: List<Long>
    ) {
        val subscriptionExists = validSubscriptionIds(
            profileId,
            listOf(subscriptionId)
        ).isNotEmpty()
        if (!subscriptionExists) {
            return
        }

        deleteGroupsForSubscriptionForProfile(profileId, subscriptionId)
        val validGroups = if (groupIds.isEmpty()) {
            emptyList()
        } else {
            validGroupIds(profileId, groupIds)
        }
        insertSubscriptionsToGroup(
            validGroups.map { FeedGroupSubscriptionEntity(it, subscriptionId) }
        )
    }

    @Query(
        """
        DELETE FROM feed_group_subscription_join
        WHERE subscription_id IN (:subscriptionIds) AND group_id IN (:groupIds)
        """
    )
    protected abstract fun removeSubscriptionsFromGroups(
        subscriptionIds: List<Long>,
        groupIds: List<Long>
    ): Int

    /** Applies only explicit membership changes, preserving unrelated and concurrently added groups. */
    @Transaction
    open fun changeGroupsForSubscriptionsForProfile(
        profileId: String,
        subscriptionIds: List<Long>,
        addedGroupIds: List<Long>,
        removedGroupIds: List<Long>
    ): Int {
        require(addedGroupIds.toSet().intersect(removedGroupIds.toSet()).isEmpty())
        // Keep each query below the older Android SQLite bind limit, even for large selections.
        val subscriptions = subscriptionIds.distinct().chunked(400)
            .flatMap { validSubscriptionIds(profileId, it) }
        if (subscriptions.isEmpty()) return 0
        val added = addedGroupIds.distinct().chunked(400)
            .flatMap { validGroupIds(profileId, it) }
        val removed = removedGroupIds.distinct().chunked(400)
            .flatMap { validGroupIds(profileId, it) }
        var changed = 0
        subscriptions.chunked(400).forEach { selected ->
            removed.chunked(400).forEach { groups ->
                changed += removeSubscriptionsFromGroups(selected, groups)
            }
            added.forEach { group ->
                changed += insertSubscriptionsToGroup(
                    selected.map { FeedGroupSubscriptionEntity(group, it) }
                ).count { it != -1L }
            }
        }
        return changed
    }

    @Transaction
    open fun updateOrder(orderMap: Map<Long, Long>) {
        orderMap.forEach { (groupId, sortOrder) -> updateOrder(groupId, sortOrder) }
    }

    @Transaction
    open fun updateOrderForProfile(profileId: String, orderMap: Map<Long, Long>) {
        orderMap.forEach { (groupId, sortOrder) ->
            updateOrderForProfile(profileId, groupId, sortOrder)
        }
    }

    @Query("UPDATE feed_group SET sort_order = :sortOrder WHERE uid = :groupId")
    abstract fun updateOrder(groupId: Long, sortOrder: Long): Int

    @Query(
        """
        UPDATE feed_group
        SET sort_order = :sortOrder
        WHERE profile_id = :profileId AND uid = :groupId
        """
    )
    abstract fun updateOrderForProfile(
        profileId: String,
        groupId: Long,
        sortOrder: Long
    ): Int

    @Query(
        """
        SELECT IFNULL(MAX(sort_order) + 1, 0)
        FROM feed_group
        WHERE profile_id = :profileId
        """
    )
    protected abstract fun nextSortOrder(profileId: String): Long

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM feed_group
            WHERE profile_id = :profileId AND uid = :groupId
        )
        """
    )
    protected abstract fun groupBelongsToProfile(
        profileId: String,
        groupId: Long
    ): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract fun insertInternal(feedGroupEntity: FeedGroupEntity): Long
}
