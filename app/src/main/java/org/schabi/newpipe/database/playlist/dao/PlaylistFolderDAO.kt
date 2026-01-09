package org.schabi.newpipe.database.playlist.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import androidx.room.Insert
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import org.schabi.newpipe.database.playlist.model.PlaylistFolderEntity

@Dao
interface PlaylistFolderDAO {
    @Query("SELECT * FROM playlist_folders ORDER BY sort_order ASC")
    fun getAll(): Flowable<List<PlaylistFolderEntity>>

    @Insert
    fun insert(folder: PlaylistFolderEntity): Long

    @Update
    fun update(folder: PlaylistFolderEntity): Int

    @Query("DELETE FROM playlist_folders WHERE uid = :folderId")
    fun delete(folderId: Long): Int

    @Query("SELECT * FROM playlist_folders WHERE uid = :folderId LIMIT 1")
    fun get(folderId: Long): Maybe<PlaylistFolderEntity>
}
