package org.schabi.newpipe.database.feed.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import java.time.OffsetDateTime
import org.schabi.newpipe.database.feed.model.FeedEntity
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.feed.model.FeedLastUpdatedEntity
import org.schabi.newpipe.database.stream.StreamWithState
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity

@Dao
abstract class FeedDAO {
    @Query("DELETE FROM feed")
    abstract fun deleteAll(): Int

    @Query(
        """
        DELETE FROM feed
        WHERE subscription_id IN (
            SELECT uid FROM subscriptions WHERE profile_id = :profileId
        )
        """
    )
    abstract fun deleteAllForProfile(profileId: String): Int

    @Query("DELETE FROM feed_last_updated")
    abstract fun deleteAllLastUpdated(): Int

    @Query(
        """
        DELETE FROM feed_last_updated
        WHERE subscription_id IN (
            SELECT uid FROM subscriptions WHERE profile_id = :profileId
        )
        """
    )
    abstract fun deleteAllLastUpdatedForProfile(profileId: String): Int

    /**
     * Publication ordering prioritizes source-dated items before applying the 500-item limit.
     * Synthetic first-seen timestamps must not displace published videos from the result.
     *
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
        ON s.uid = sst.stream_id AND sst.profile_id = :profileId
        
        LEFT JOIN stream_history sh
        ON s.uid = sh.stream_id AND sh.profile_id = :profileId
        
        INNER JOIN feed f
        ON s.uid = f.stream_id

        INNER JOIN subscriptions sub
        ON sub.uid = f.subscription_id

        LEFT JOIN feed_group_subscription_join fgs
        ON (
            :groupId <> ${FeedGroupEntity.GROUP_ALL_ID}
            AND fgs.subscription_id = f.subscription_id
        )

        WHERE (
            :groupId = ${FeedGroupEntity.GROUP_ALL_ID}
            OR fgs.group_id = :groupId
        )
        AND sub.profile_id = :profileId
        AND (:serviceId = -1 OR sub.service_id = :serviceId)
        AND (
            :serviceId <> ${SubscriptionEntity.YOUTUBE_SERVICE_ID}
            OR (sub.youtube_mode_mask & :youtubeModeMask) <> 0
        )
        AND (
            :serviceId <> ${SubscriptionEntity.YOUTUBE_SERVICE_ID}
            OR (f.youtube_mode_mask & :youtubeModeMask) <> 0
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
            OR s.upload_date < CASE WHEN :useCurrentTime
                THEN (julianday('now') - 2440587.5) * 86400000
                ELSE :uploadDateBefore END
        )

        GROUP BY s.uid
        ORDER BY CASE WHEN :sortByDiscovery THEN MIN(f.first_discovered_at) ELSE 0 END DESC,
            CASE WHEN NOT :sortByDiscovery
                AND s.service_id = ${SubscriptionEntity.YOUTUBE_SERVICE_ID}
                AND s.is_upload_date_approximation = 1
                AND TRIM(COALESCE(s.textual_upload_date, ''), CHAR(9, 10, 11, 12, 13, 32)) = ''
                THEN 1 ELSE 0 END ASC,
            s.upload_date IS NULL DESC, s.upload_date DESC, s.uploader ASC, s.uid DESC
        LIMIT :limit OFFSET :offset
        """
    )
    abstract fun observeStreams(
        profileId: String,
        groupId: Long,
        includePlayed: Boolean,
        includePartiallyPlayed: Boolean,
        uploadDateBefore: OffsetDateTime?,
        serviceId: Int,
        youtubeModeMask: Int,
        sortByDiscovery: Boolean = false,
        useCurrentTime: Boolean = false,
        limit: Int = 500,
        offset: Int = 0
    ): Flowable<List<StreamWithState>>

    fun getStreams(
        profileId: String,
        groupId: Long,
        includePlayed: Boolean,
        includePartiallyPlayed: Boolean,
        uploadDateBefore: OffsetDateTime?,
        serviceId: Int,
        youtubeModeMask: Int,
        sortByDiscovery: Boolean = false,
        limit: Int = 500,
        offset: Int = 0
    ): Maybe<List<StreamWithState>> = observeStreams(
        profileId,
        groupId,
        includePlayed,
        includePartiallyPlayed,
        uploadDateBefore,
        serviceId,
        youtubeModeMask,
        sortByDiscovery,
        limit = limit,
        offset = offset
    ).firstElement()

    /**
     * Remove links to streams that are older than the given date
     * **but keep at least one stream per uploader**.
     *
     * One stream per uploader is kept because it is needed as reference
     * when fetching new streams to check if they are new or not.
     * @param offsetDateTime the newest date to keep, older streams are removed
     */
    @Query(
        """
        DELETE FROM feed
        WHERE feed.stream_id IN (SELECT uid from (
              SELECT s.uid,
              (SELECT MAX(upload_date)
                    FROM streams s1
                    INNER JOIN feed f1
                    ON s1.uid = f1.stream_id
                    WHERE f1.subscription_id = f.subscription_id) max_upload_date
              FROM streams s
              INNER JOIN feed f
              ON s.uid = f.stream_id
        
              WHERE s.upload_date < :offsetDateTime
              AND   s.upload_date <> max_upload_date))
        """
    )
    abstract fun unlinkStreamsOlderThan(offsetDateTime: OffsetDateTime)

    @Query(
        """
        DELETE FROM feed
        WHERE feed.subscription_id IN (
            SELECT uid FROM subscriptions WHERE profile_id = :profileId
        )
        AND feed.stream_id IN (SELECT uid FROM (
            SELECT s.uid,
            (
                SELECT MAX(upload_date)
                FROM streams s1
                INNER JOIN feed f1 ON s1.uid = f1.stream_id
                WHERE f1.subscription_id = f.subscription_id
            ) max_upload_date
            FROM streams s
            INNER JOIN feed f ON s.uid = f.stream_id
            WHERE f.subscription_id IN (
                SELECT uid FROM subscriptions WHERE profile_id = :profileId
            )
            AND s.upload_date < :offsetDateTime
            AND s.upload_date <> max_upload_date
        ))
        """
    )
    abstract fun unlinkStreamsOlderThanForProfile(
        profileId: String,
        offsetDateTime: OffsetDateTime
    )

    @Query(
        """
        DELETE FROM feed
        
        WHERE feed.subscription_id = :subscriptionId
        AND (feed.youtube_mode_mask & :youtubeModeMask) <> 0

        AND feed.stream_id IN (
            SELECT s.uid FROM streams s

            INNER JOIN feed f
            ON s.uid = f.stream_id

            WHERE s.stream_type = "LIVE_STREAM" OR s.stream_type = "AUDIO_LIVE_STREAM"
        )
        """
    )
    abstract fun unlinkOldLivestreams(subscriptionId: Long, youtubeModeMask: Int)

    @Query("SELECT * FROM feed WHERE subscription_id = :subscriptionId")
    abstract fun getMemberships(subscriptionId: Long): List<FeedEntity>

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

        INNER JOIN subscriptions s
        ON s.uid = lu.subscription_id

        WHERE s.profile_id = :profileId
        AND (:serviceId = -1 OR s.service_id = :serviceId)
        AND (
            :serviceId <> ${SubscriptionEntity.YOUTUBE_SERVICE_ID}
            OR (s.youtube_mode_mask & :youtubeModeMask) <> 0
        )
        AND (
            :serviceId <> ${SubscriptionEntity.YOUTUBE_SERVICE_ID}
            OR (lu.youtube_mode_mask & :youtubeModeMask) <> 0
        )
        """
    )
    abstract fun oldestSubscriptionUpdate(
        profileId: String,
        groupId: Long,
        serviceId: Int,
        youtubeModeMask: Int
    ): Flowable<List<OffsetDateTime?>>

    @Query(
        """
        SELECT MIN(lu.last_updated)
        FROM feed_last_updated lu
        INNER JOIN subscriptions s ON s.uid = lu.subscription_id
        WHERE s.profile_id = :profileId
        AND (:serviceId = -1 OR s.service_id = :serviceId)
        AND (
            :serviceId <> ${SubscriptionEntity.YOUTUBE_SERVICE_ID}
            OR (s.youtube_mode_mask & :youtubeModeMask) <> 0
        )
        AND (
            :serviceId <> ${SubscriptionEntity.YOUTUBE_SERVICE_ID}
            OR (lu.youtube_mode_mask & :youtubeModeMask) <> 0
        )
        """
    )
    abstract fun oldestSubscriptionUpdateFromAll(
        profileId: String,
        serviceId: Int,
        youtubeModeMask: Int
    ): Flowable<List<OffsetDateTime?>>

    @Query(
        """
        SELECT COUNT(DISTINCT s.uid)
        FROM subscriptions s
        LEFT JOIN feed_last_updated lu
        ON s.uid = lu.subscription_id
        AND (
            :serviceId <> ${SubscriptionEntity.YOUTUBE_SERVICE_ID}
            OR (lu.youtube_mode_mask & :youtubeModeMask) <> 0
        )
        WHERE s.profile_id = :profileId
        AND (:serviceId = -1 OR s.service_id = :serviceId)
        AND lu.last_updated IS NULL
        AND (
            :serviceId <> ${SubscriptionEntity.YOUTUBE_SERVICE_ID}
            OR (s.youtube_mode_mask & :youtubeModeMask) <> 0
        )
        """
    )
    abstract fun notLoadedCount(
        profileId: String,
        serviceId: Int,
        youtubeModeMask: Int
    ): Flowable<Long>

    @Query(
        """
        SELECT COUNT(DISTINCT s.uid) FROM subscriptions s
        
        INNER JOIN feed_group_subscription_join fgs
        ON s.uid = fgs.subscription_id AND fgs.group_id = :groupId

        LEFT JOIN feed_last_updated lu
        ON s.uid = lu.subscription_id
        AND (
            :serviceId <> ${SubscriptionEntity.YOUTUBE_SERVICE_ID}
            OR (lu.youtube_mode_mask & :youtubeModeMask) <> 0
        )

        WHERE lu.last_updated IS NULL
        AND s.profile_id = :profileId
        AND (:serviceId = -1 OR s.service_id = :serviceId)
        AND (
            :serviceId <> ${SubscriptionEntity.YOUTUBE_SERVICE_ID}
            OR (s.youtube_mode_mask & :youtubeModeMask) <> 0
        )
        """
    )
    abstract fun notLoadedCountForGroup(
        profileId: String,
        groupId: Long,
        serviceId: Int,
        youtubeModeMask: Int
    ): Flowable<Long>

    @Query(
        """
        SELECT DISTINCT s.* FROM subscriptions s

        LEFT JOIN feed_last_updated lu
        ON s.uid = lu.subscription_id 

        WHERE s.profile_id = :profileId
        AND (lu.last_updated IS NULL OR lu.last_updated < :outdatedThreshold)
        """
    )
    abstract fun getAllOutdated(
        profileId: String,
        outdatedThreshold: OffsetDateTime
    ): Flowable<List<SubscriptionEntity>>

    @Query(
        """
        SELECT DISTINCT s.* FROM subscriptions s

        LEFT JOIN feed_last_updated lu
        ON s.uid = lu.subscription_id
        AND (
            :serviceId <> ${SubscriptionEntity.YOUTUBE_SERVICE_ID}
            OR (lu.youtube_mode_mask & :youtubeModeMask) <> 0
        )

        WHERE s.profile_id = :profileId
        AND (lu.last_updated IS NULL OR lu.last_updated < :outdatedThreshold)
        AND (:serviceId = -1 OR s.service_id = :serviceId)
        AND (
            :serviceId <> ${SubscriptionEntity.YOUTUBE_SERVICE_ID}
            OR (s.youtube_mode_mask & :youtubeModeMask) <> 0
        )
        """
    )
    abstract fun getAllOutdatedForScope(
        profileId: String,
        serviceId: Int,
        youtubeModeMask: Int,
        outdatedThreshold: OffsetDateTime
    ): Flowable<List<SubscriptionEntity>>

    @Query(
        """
        SELECT DISTINCT s.* FROM subscriptions s

        INNER JOIN feed_group_subscription_join fgs
        ON s.uid = fgs.subscription_id AND fgs.group_id = :groupId

        LEFT JOIN feed_last_updated lu
        ON s.uid = lu.subscription_id

        WHERE s.profile_id = :profileId
        AND (lu.last_updated IS NULL OR lu.last_updated < :outdatedThreshold)
        """
    )
    abstract fun getAllOutdatedForGroup(
        profileId: String,
        groupId: Long,
        outdatedThreshold: OffsetDateTime
    ): Flowable<List<SubscriptionEntity>>

    @Query(
        """
        SELECT DISTINCT s.* FROM subscriptions s

        INNER JOIN feed_group_subscription_join fgs
        ON s.uid = fgs.subscription_id AND fgs.group_id = :groupId

        LEFT JOIN feed_last_updated lu
        ON s.uid = lu.subscription_id
        AND (
            :serviceId <> ${SubscriptionEntity.YOUTUBE_SERVICE_ID}
            OR (lu.youtube_mode_mask & :youtubeModeMask) <> 0
        )

        WHERE s.profile_id = :profileId
        AND (lu.last_updated IS NULL OR lu.last_updated < :outdatedThreshold)
        AND (:serviceId = -1 OR s.service_id = :serviceId)
        AND (
            :serviceId <> ${SubscriptionEntity.YOUTUBE_SERVICE_ID}
            OR (s.youtube_mode_mask & :youtubeModeMask) <> 0
        )
        """
    )
    abstract fun getAllOutdatedForGroupAndScope(
        profileId: String,
        groupId: Long,
        serviceId: Int,
        youtubeModeMask: Int,
        outdatedThreshold: OffsetDateTime
    ): Flowable<List<SubscriptionEntity>>

    @Query(
        """
        SELECT DISTINCT s.* FROM subscriptions s

        LEFT JOIN feed_last_updated lu
        ON s.uid = lu.subscription_id

        WHERE s.profile_id = :profileId
            AND (lu.last_updated IS NULL OR lu.last_updated < :outdatedThreshold)
            AND s.notification_mode IN (:notificationModes)
        """
    )
    abstract fun getOutdatedWithNotificationModes(
        profileId: String,
        outdatedThreshold: OffsetDateTime,
        notificationModes: List<Int>
    ): Flowable<List<SubscriptionEntity>>
}
