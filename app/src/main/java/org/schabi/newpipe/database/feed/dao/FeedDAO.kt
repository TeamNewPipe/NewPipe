package org.schabi.newpipe.database.feed.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.Flowable
import org.schabi.newpipe.database.feed.model.FeedEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import java.util.*

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
}
