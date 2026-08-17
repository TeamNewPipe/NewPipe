package org.schabi.newpipe.database.subscription

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.schabi.newpipe.database.BasicDAO

@Dao
abstract class SubscriptionDAO : BasicDAO<SubscriptionEntity> {
    @Query("SELECT COUNT(*) FROM subscriptions")
    abstract fun rowCount(): Flow<Long>

    @Query("SELECT * FROM subscriptions WHERE service_id = :serviceId")
    abstract override fun listByService(serviceId: Int): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions ORDER BY name COLLATE NOCASE ASC")
    abstract override fun getAll(): Flow<List<SubscriptionEntity>>

    @Query(
        """
        SELECT * FROM subscriptions

        WHERE name LIKE '%' || :filter || '%'

        ORDER BY name COLLATE NOCASE ASC
        """
    )
    abstract fun getSubscriptionsFiltered(filter: String): Flow<List<SubscriptionEntity>>

    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT * FROM subscriptions s

        LEFT JOIN feed_group_subscription_join fgs
        ON s.uid = fgs.subscription_id

        WHERE (fgs.subscription_id IS NULL OR fgs.group_id = :currentGroupId)

        ORDER BY name COLLATE NOCASE ASC
        """
    )
    abstract fun getSubscriptionsOnlyUngrouped(
        currentGroupId: Long
    ): Flow<List<SubscriptionEntity>>

    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT * FROM subscriptions s

        LEFT JOIN feed_group_subscription_join fgs
        ON s.uid = fgs.subscription_id

        WHERE (fgs.subscription_id IS NULL OR fgs.group_id = :currentGroupId)
        AND s.name LIKE '%' || :filter || '%'

        ORDER BY name COLLATE NOCASE ASC
        """
    )
    abstract fun getSubscriptionsOnlyUngroupedFiltered(
        currentGroupId: Long,
        filter: String
    ): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE url LIKE :url AND service_id = :serviceId")
    abstract fun getSubscriptionFlow(serviceId: Int, url: String): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE url LIKE :url AND service_id = :serviceId")
    abstract suspend fun getSubscription(serviceId: Int, url: String): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions WHERE uid = :subscriptionId")
    abstract suspend fun getSubscription(subscriptionId: Long): SubscriptionEntity?

    @Query("DELETE FROM subscriptions")
    abstract override suspend fun deleteAll(): Int

    @Query("DELETE FROM subscriptions WHERE url LIKE :url AND service_id = :serviceId")
    abstract suspend fun deleteSubscription(serviceId: Int, url: String): Int

    @Query("SELECT uid FROM subscriptions WHERE url LIKE :url AND service_id = :serviceId")
    internal abstract suspend fun getSubscriptionIdInternal(serviceId: Int, url: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    internal abstract suspend fun silentInsertAllInternal(entities: List<SubscriptionEntity>): List<Long>

    @Transaction
    open suspend fun upsertAll(entities: List<SubscriptionEntity>): List<SubscriptionEntity> {
        val insertUidList = silentInsertAllInternal(entities)

        insertUidList.forEachIndexed { index: Int, uidFromInsert: Long ->
            val entity = entities[index]

            if (uidFromInsert != -1L) {
                entity.uid = uidFromInsert
            } else {
                val subscriptionIdFromDb = getSubscriptionIdInternal(entity.serviceId, entity.url!!)
                    ?: error("Subscription cannot be null just after insertion.")
                entity.uid = subscriptionIdFromDb

                update(entity)
            }
        }

        return entities
    }
}
