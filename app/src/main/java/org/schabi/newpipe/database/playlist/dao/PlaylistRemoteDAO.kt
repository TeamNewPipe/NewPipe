package org.schabi.newpipe.database.playlist.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import io.reactivex.rxjava3.core.Flowable
import org.schabi.newpipe.database.BasicDAO
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity

@Dao
open interface PlaylistRemoteDAO : BasicDAO<PlaylistRemoteEntity?> {
    @Query("SELECT * FROM " + PlaylistRemoteEntity.Companion.REMOTE_PLAYLIST_TABLE)
    public override fun getAll(): Flowable<List<PlaylistRemoteEntity?>?>?
    @Query("DELETE FROM " + PlaylistRemoteEntity.Companion.REMOTE_PLAYLIST_TABLE)
    public override fun deleteAll(): Int

    @Query(("SELECT * FROM " + PlaylistRemoteEntity.Companion.REMOTE_PLAYLIST_TABLE
            + " WHERE " + PlaylistRemoteEntity.Companion.REMOTE_PLAYLIST_SERVICE_ID + " = :serviceId"))
    public override fun listByService(serviceId: Int): Flowable<List<PlaylistRemoteEntity?>?>?

    @Query(("SELECT * FROM " + PlaylistRemoteEntity.Companion.REMOTE_PLAYLIST_TABLE + " WHERE "
            + PlaylistRemoteEntity.Companion.REMOTE_PLAYLIST_ID + " = :playlistId"))
    fun getPlaylist(playlistId: Long): Flowable<List<PlaylistRemoteEntity?>?>?

    @Query(("SELECT * FROM " + PlaylistRemoteEntity.Companion.REMOTE_PLAYLIST_TABLE + " WHERE "
            + PlaylistRemoteEntity.Companion.REMOTE_PLAYLIST_URL + " = :url AND " + PlaylistRemoteEntity.Companion.REMOTE_PLAYLIST_SERVICE_ID + " = :serviceId"))
    fun getPlaylist(serviceId: Long, url: String?): Flowable<List<PlaylistRemoteEntity?>?>

    @Query(("SELECT * FROM " + PlaylistRemoteEntity.Companion.REMOTE_PLAYLIST_TABLE
            + " ORDER BY " + PlaylistRemoteEntity.Companion.REMOTE_PLAYLIST_DISPLAY_INDEX))
    fun getPlaylists(): Flowable<List<PlaylistRemoteEntity?>?>

    @Query(("SELECT " + PlaylistRemoteEntity.Companion.REMOTE_PLAYLIST_ID + " FROM " + PlaylistRemoteEntity.Companion.REMOTE_PLAYLIST_TABLE
            + " WHERE " + PlaylistRemoteEntity.Companion.REMOTE_PLAYLIST_URL + " = :url "
            + "AND " + PlaylistRemoteEntity.Companion.REMOTE_PLAYLIST_SERVICE_ID + " = :serviceId"))
    fun getPlaylistIdInternal(serviceId: Long, url: String?): Long

    @Transaction
    fun upsert(playlist: PlaylistRemoteEntity): Long {
        val playlistId: Long = getPlaylistIdInternal(playlist.getServiceId().toLong(), playlist.getUrl())
        if (playlistId == null) {
            return insert(playlist)
        } else {
            playlist.setUid(playlistId)
            update(playlist)
            return playlistId
        }
    }

    @Query(("DELETE FROM " + PlaylistRemoteEntity.Companion.REMOTE_PLAYLIST_TABLE
            + " WHERE " + PlaylistRemoteEntity.Companion.REMOTE_PLAYLIST_ID + " = :playlistId"))
    fun deletePlaylist(playlistId: Long): Int
}
