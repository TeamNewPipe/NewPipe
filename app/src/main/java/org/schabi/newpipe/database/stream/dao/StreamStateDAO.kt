package org.schabi.newpipe.database.stream.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import org.schabi.newpipe.database.stream.model.StreamStateEntity

@Dao
interface StreamStateDAO {
    @Query("DELETE FROM " + StreamStateEntity.STREAM_STATE_TABLE)
    fun deleteAll(): Completable

    @Query("SELECT * FROM " + StreamStateEntity.STREAM_STATE_TABLE + " WHERE " + StreamStateEntity.JOIN_STREAM_ID + " = :streamId")
    fun getState(streamId: Long): Maybe<StreamStateEntity>

    @Query("DELETE FROM " + StreamStateEntity.STREAM_STATE_TABLE + " WHERE " + StreamStateEntity.JOIN_STREAM_ID + " = :streamId")
    fun deleteState(streamId: Long): Completable

    @Upsert
    fun upsert(stream: StreamStateEntity)
}
