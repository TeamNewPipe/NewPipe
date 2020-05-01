package org.schabi.newpipe.database.feed.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.reactivex.Flowable
import java.util.Date
import org.schabi.newpipe.database.feed.model.FeedEntity
import org.schabi.newpipe.database.feed.model.FeedLastUpdatedEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity

@Dao
abstract class FeedDAO {
    @Query("DELETE FROM feed")
    abstract fun deleteAll(): Int

    @Query("""
        SELECT s.* FROM streams s

        INNER JOIN feed f
        ON s.uid = f.stream_id

        ORDER BY s.upload_date IS NULL DESC, s.upload_date DESC, s.uploader ASC

        LIMIT 500
        """)
    abstract fun getAllStreams(): Flowable<List<StreamEntity>>

    @Query("""
        SELECT s.* FROM streams s

        INNER JOIN feed f
        ON s.uid = f.stream_id

        INNER JOIN feed_group_subscription_join fgs
        ON fgs.subscription_id = f.subscription_id

        INNER JOIN feed_group fg
        ON fg.uid = fgs.group_id

        WHERE fgs.group_id = :groupId

        ORDER BY s.upload_date IS NULL DESC, s.upload_date DESC, s.uploader ASC
        LIMIT 500
        """)
    abstract fun getAllStreamsFromGroup(groupId: Long): Flowable<List<StreamEntity>>

    @Query("""
        DELETE FROM feed WHERE

        feed.stream_id IN (
            SELECT s.uid FROM streams s

            INNER JOIN feed f
            ON s.uid = f.stream_id

            WHERE s.upload_date < :date
        )
        """)
    abstract fun unlinkStreamsOlderThan(date: Date)

    @Query("""
        DELETE FROM feed
        
        WHERE feed.subscription_id = :subscriptionId

        AND feed.stream_id IN (
            SELECT s.uid FROM streams s

            INNER JOIN feed f
            ON s.uid = f.stream_id

            WHERE s.stream_type = "LIVE_STREAM" OR s.stream_type = "AUDIO_LIVE_STREAM"
        )
        """)
    abstract fun unlinkOldLivestreams(subscriptionId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insert(feedEntity: FeedEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insertAll(entities: List<FeedEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    internal abstract fun insertLastUpdated(lastUpdatedEntity: FeedLastUpdatedEntity): Long

    @Update(onConflict = OnConflictStrategy.IGNORE)
    internal abstract fun updateLastUpdated(lastUpdatedEntity: FeedLastUpdatedEntity)

    @Transaction
    open fun setLastUpdatedForSubscription(lastUpdatedEntity: FeedLastUpdatedEntity) {
        val id = insertLastUpdated(lastUpdatedEntity)

        if (id == -1L) {
            updateLastUpdated(lastUpdatedEntity)
        }
    }

    @Query("""
        SELECT MIN(lu.last_updated) FROM feed_last_updated lu

        INNER JOIN feed_group_subscription_join fgs
        ON fgs.subscription_id = lu.subscription_id AND fgs.group_id = :groupId
        """)
    abstract fun oldestSubscriptionUpdate(groupId: Long): Flowable<List<Date>>

    @Query("SELECT MIN(last_updated) FROM feed_last_updated")
    abstract fun oldestSubscriptionUpdateFromAll(): Flowable<List<Date>>

    @Query("SELECT COUNT(*) FROM feed_last_updated WHERE last_updated IS NULL")
    abstract fun notLoadedCount(): Flowable<Long>

    @Query("""
        SELECT COUNT(*) FROM subscriptions s
        
        INNER JOIN feed_group_subscription_join fgs
        ON s.uid = fgs.subscription_id AND fgs.group_id = :groupId

        LEFT JOIN feed_last_updated lu
        ON s.uid = lu.subscription_id 

        WHERE lu.last_updated IS NULL
        """)
    abstract fun notLoadedCountForGroup(groupId: Long): Flowable<Long>

    @Query("""
        SELECT s.* FROM subscriptions s

        LEFT JOIN feed_last_updated lu
        ON s.uid = lu.subscription_id 

        WHERE lu.last_updated IS NULL OR lu.last_updated < :outdatedThreshold
        """)
    abstract fun getAllOutdated(outdatedThreshold: Date): Flowable<List<SubscriptionEntity>>

    @Query("""
        SELECT s.* FROM subscriptions s

        INNER JOIN feed_group_subscription_join fgs
        ON s.uid = fgs.subscription_id AND fgs.group_id = :groupId

        LEFT JOIN feed_last_updated lu
        ON s.uid = lu.subscription_id

        WHERE lu.last_updated IS NULL OR lu.last_updated < :outdatedThreshold
        """)
    abstract fun getAllOutdatedForGroup(groupId: Long, outdatedThreshold: Date): Flowable<List<SubscriptionEntity>>
}
