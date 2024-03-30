package org.schabi.newpipe.database.playlist.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import io.reactivex.rxjava3.core.Flowable
import org.schabi.newpipe.database.BasicDAO
import org.schabi.newpipe.database.playlist.model.PlaylistEntity

@Dao
open interface PlaylistDAO : BasicDAO<PlaylistEntity?> {
    @Query("SELECT * FROM " + PlaylistEntity.Companion.PLAYLIST_TABLE)
    public override fun getAll(): Flowable<List<PlaylistEntity?>?>?
    @Query("DELETE FROM " + PlaylistEntity.Companion.PLAYLIST_TABLE)
    public override fun deleteAll(): Int
    public override fun listByService(serviceId: Int): Flowable<List<PlaylistEntity?>?>? {
        throw UnsupportedOperationException()
    }

    @Query("SELECT * FROM " + PlaylistEntity.Companion.PLAYLIST_TABLE + " WHERE " + PlaylistEntity.Companion.PLAYLIST_ID + " = :playlistId")
    fun getPlaylist(playlistId: Long): Flowable<List<PlaylistEntity?>>

    @Query("DELETE FROM " + PlaylistEntity.Companion.PLAYLIST_TABLE + " WHERE " + PlaylistEntity.Companion.PLAYLIST_ID + " = :playlistId")
    fun deletePlaylist(playlistId: Long): Int

    @Query("SELECT COUNT(*) FROM " + PlaylistEntity.Companion.PLAYLIST_TABLE)
    fun getCount(): Flowable<Long?>

    @Transaction
    fun upsertPlaylist(playlist: PlaylistEntity): Long {
        val playlistId: Long = playlist.getUid()
        if (playlistId == -1L) {
            // This situation is probably impossible.
            return insert(playlist)
        } else {
            update(playlist)
            return playlistId
        }
    }
}
