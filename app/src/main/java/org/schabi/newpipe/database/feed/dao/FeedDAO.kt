package org.schabi.newpipe.database.feed.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import org.schabi.newpipe.database.feed.model.FeedEntity
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.feed.model.FeedLastUpdatedEntity
import org.schabi.newpipe.database.stream.StreamWithState
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import java.time.OffsetDateTime

@Dao
abstract class FeedDAO {
    @Query("DELETE FROM feed")
    abstract fun deleteAll(): Int

    /**
     * @param groupId          the group id to get feed streams of; use
     *                         [FeedGroupEntity.GROUP_ALL_ID] to not filter by group
     * @param includePlayed    if false, only return all of the live, never-played or non-finished
     *                         feed streams (see `@see` items); if true no filter is applied
     * @param uploadDateBefore get only streams uploaded before this date (useful to filter out
     *                         future streams); use null to not filter by upload date
     * @return the feed streams filtered according to the conditions provided in the parameters
     * @see StreamStateEntity.isFinished()
     * @see StreamStateEntity.PLAYBACK_FINISHED_END_MILLISECONDS
     * @see StreamStateEntity.PLAYBACK_SAVE_THRESHOLD_START_MILLISECONDS
     */
    @Query(
        """
        SELECT s.*, sst.progress_time
        FROM streams s

        LEFT JOIN stream_state sst
        ON s.uid = sst.stream_id
        
        LEFT JOIN stream_history sh
        ON s.uid = sh.stream_id
        
        INNER JOIN feed f
        ON s.uid = f.stream_id

        LEFT JOIN feed_group_subscription_join fgs
        ON (
            :groupId <> ${FeedGroupEntity.GROUP_ALL_ID}
            AND fgs.subscription_id = f.subscription_id
        )

        WHERE (
            :groupId = ${FeedGroupEntity.GROUP_ALL_ID}
            OR fgs.group_id = :groupId
        )
        AND (
            :includePlayed
            OR sh.stream_id IS NULL
            OR sst.stream_id IS NULL
            OR sst.progress_time < s.duration * 1000 - ${StreamStateEntity.PLAYBACK_FINISHED_END_MILLISECONDS}
            OR sst.progress_time < s.duration * 1000 * 3 / 4
            OR s.stream_type = 'LIVE_STREAM'
            OR s.stream_type = 'AUDIO_LIVE_STREAM'
        )
        AND (
            :includePartiallyPlayed
            OR sh.stream_id IS NULL
            OR sst.stream_id IS NULL
            OR (sst.progress_time <= ${StreamStateEntity.PLAYBACK_SAVE_THRESHOLD_START_MILLISECONDS}
            AND sst.progress_time <= s.duration * 1000 / 4)
            OR (sst.progress_time >= s.duration * 1000 - ${StreamStateEntity.PLAYBACK_FINISHED_END_MILLISECONDS}
            AND sst.progress_time >= s.duration * 1000 * 3 / 4)
        )
        AND (
            :uploadDateBefore IS NULL
            OR s.upload_date IS NULL
            OR s.upload_date < :uploadDateBefore
        )

        ORDER BY s.upload_date IS NULL DESC, s.upload_date DESC, s.uploader ASC
        LIMIT 500
        """
    )
    abstract fun getStreams(
        groupId: Long,
        includePlayed: Boolean,
        includePartiallyPlayed: Boolean,
        uploadDateBefore: OffsetDateTime?
    ): Maybe<List<StreamWithState>>

    @Query(
        """
        DELETE FROM feed WHERE

        feed.stream_id IN (
            SELECT s.uid FROM streams s

            INNER JOIN feed f
            ON s.uid = f.stream_id

            WHERE s.upload_date < :offsetDateTime
        )
        """
    )
    abstract fun unlinkStreamsOlderThan(offsetDateTime: OffsetDateTime)

    @Query(
        """
        DELETE FROM feed
        
        WHERE feed.subscription_id = :subscriptionId

        AND feed.stream_id IN (
            SELECT s.uid FROM streams s

            INNER JOIN feed f
            ON s.uid = f.stream_id

            WHERE s.stream_type = "LIVE_STREAM" OR s.stream_type = "AUDIO_LIVE_STREAM"
        )
        """
    )
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

    @Query(
        """
        SELECT MIN(lu.last_updated) FROM feed_last_updated lu

        INNER JOIN feed_group_subscription_join fgs
        ON fgs.subscription_id = lu.subscription_id AND fgs.group_id = :groupId
        """
    )
    abstract fun oldestSubscriptionUpdate(groupId: Long): Flowable<List<OffsetDateTime>>

    @Query("SELECT MIN(last_updated) FROM feed_last_updated")
    abstract fun oldestSubscriptionUpdateFromAll(): Flowable<List<OffsetDateTime>>

    @Query("SELECT COUNT(*) FROM feed_last_updated WHERE last_updated IS NULL")
    abstract fun notLoadedCount(): Flowable<Long>

    @Query(
        """
        SELECT COUNT(*) FROM subscriptions s
        
        INNER JOIN feed_group_subscription_join fgs
        ON s.uid = fgs.subscription_id AND fgs.group_id = :groupId

        LEFT JOIN feed_last_updated lu
        ON s.uid = lu.subscription_id 

        WHERE lu.last_updated IS NULL
        """
    )
    abstract fun notLoadedCountForGroup(groupId: Long): Flowable<Long>

    @Query(
        """
        SELECT s.* FROM subscriptions s

        LEFT JOIN feed_last_updated lu
        ON s.uid = lu.subscription_id 

        WHERE lu.last_updated IS NULL OR lu.last_updated < :outdatedThreshold
        """
    )
    abstract fun getAllOutdated(outdatedThreshold: OffsetDateTime): Flowable<List<SubscriptionEntity>>

    @Query(
        """
        SELECT s.* FROM subscriptions s

        INNER JOIN feed_group_subscription_join fgs
        ON s.uid = fgs.subscription_id AND fgs.group_id = :groupId

        LEFT JOIN feed_last_updated lu
        ON s.uid = lu.subscription_id

        WHERE lu.last_updated IS NULL OR lu.last_updated < :outdatedThreshold
        """
    )
    abstract fun getAllOutdatedForGroup(groupId: Long, outdatedThreshold: OffsetDateTime): Flowable<List<SubscriptionEntity>>

    @Query(
        """
        SELECT s.* FROM subscriptions s

        LEFT JOIN feed_last_updated lu
        ON s.uid = lu.subscription_id

        WHERE 
            (lu.last_updated IS NULL OR lu.last_updated < :outdatedThreshold)
            AND s.notification_mode = :notificationMode
        """
    )
    abstract fun getOutdatedWithNotificationMode(
        outdatedThreshold: OffsetDateTime,
        @NotificationMode notificationMode: Int
    ): Flowable<List<SubscriptionEntity>>
}
