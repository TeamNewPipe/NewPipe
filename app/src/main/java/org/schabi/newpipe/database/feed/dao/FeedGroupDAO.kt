package org.schabi.newpipe.database.feed.dao

import androidx.room.*
import io.reactivex.Flowable
import org.schabi.newpipe.database.feed.model.FeedGroupEntity

@Dao
abstract class FeedGroupDAO {
    @Query("DELETE FROM feed_group")
    abstract fun deleteAll(): Int

    @Query("SELECT * FROM feed_group")
    abstract fun getAll(): Flowable<List<FeedGroupEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract fun insert(feedEntity: FeedGroupEntity)
}
