package org.schabi.newpipe.database.learning.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.rxjava3.core.Flowable
import org.schabi.newpipe.database.learning.model.LearningSessionEntity

@Dao
interface LearningSessionDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(session: LearningSessionEntity)

    @Query("SELECT COALESCE(SUM(watched_duration_ms), 0) FROM learning_sessions")
    fun observeTotalWatchMillis(): Flowable<Long>

    @Query("SELECT COALESCE(SUM(watched_duration_ms), 0) FROM learning_sessions WHERE local_date = :localDate")
    fun observeWatchMillisForDate(localDate: String): Flowable<Long>

    @Query("SELECT COALESCE(SUM(watched_duration_ms), 0) FROM learning_sessions WHERE local_date >= :startDate AND local_date <= :endDate")
    fun observeWatchMillisBetween(startDate: String, endDate: String): Flowable<Long>
}
