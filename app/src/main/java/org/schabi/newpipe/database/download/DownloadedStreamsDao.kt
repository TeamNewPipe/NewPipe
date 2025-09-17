package org.schabi.newpipe.database.download

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe

@Dao
interface DownloadedStreamsDao {
    @Query("SELECT * FROM downloaded_streams WHERE stream_uid = :streamUid LIMIT 1")
    fun observeByStreamUid(streamUid: Long): Flowable<List<DownloadedStreamEntity>>

    @Query("SELECT * FROM downloaded_streams WHERE stream_uid = :streamUid LIMIT 1")
    fun getByStreamUid(streamUid: Long): Maybe<DownloadedStreamEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(entity: DownloadedStreamEntity): Long

    @Update
    fun update(entity: DownloadedStreamEntity): Int

    @Query("SELECT * FROM downloaded_streams WHERE stream_uid = :streamUid LIMIT 1")
    fun findEntityByStreamUid(streamUid: Long): DownloadedStreamEntity?

    @Query("SELECT * FROM downloaded_streams WHERE id = :id LIMIT 1")
    fun findEntityById(id: Long): DownloadedStreamEntity?

    @Transaction
    fun insertOrUpdate(entity: DownloadedStreamEntity): Long {
        val newId = insert(entity)
        if (newId != -1L) {
            entity.id = newId
            return newId
        }
        update(entity)
        return entity.id
    }

    @Query("UPDATE downloaded_streams SET status = :status, last_checked_at = :lastCheckedAt, missing_since = :missingSince WHERE id = :id")
    fun updateStatus(id: Long, status: DownloadedStreamStatus, lastCheckedAt: Long?, missingSince: Long?)

    @Query("UPDATE downloaded_streams SET file_uri = :fileUri WHERE id = :id")
    fun updateFileUri(id: Long, fileUri: String)

    @Delete
    fun delete(entity: DownloadedStreamEntity)

    @Query("DELETE FROM downloaded_streams WHERE stream_uid = :streamUid")
    fun deleteByStreamUid(streamUid: Long): Int

    @Query("SELECT * FROM downloaded_streams WHERE status = :status ORDER BY last_checked_at ASC LIMIT :limit")
    fun listByStatus(status: DownloadedStreamStatus, limit: Int): List<DownloadedStreamEntity>
}
