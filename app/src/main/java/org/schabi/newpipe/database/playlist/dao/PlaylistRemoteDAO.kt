package org.schabi.newpipe.database.playlist.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.reactivex.rxjava3.core.Flowable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity

@Dao
interface PlaylistRemoteDAO {
    @Query("SELECT * FROM remote_playlists")
    fun getAll(): Flowable<List<PlaylistRemoteEntity>>

    @Query("SELECT * FROM remote_playlists WHERE uid = :playlistId")
    fun getPlaylist(playlistId: Long): Flowable<PlaylistRemoteEntity>

    @Query("SELECT * FROM remote_playlists WHERE url = :url AND service_id = :serviceId")
    fun getPlaylist(serviceId: Int, url: String): Flow<PlaylistRemoteEntity?>

    @Query("SELECT * FROM remote_playlists ORDER BY display_index")
    fun getPlaylists(): Flowable<List<PlaylistRemoteEntity>>

    @Insert
    suspend fun insert(playlist: PlaylistRemoteEntity): Long

    @Update
    suspend fun update(playlist: PlaylistRemoteEntity)

    @Transaction
    suspend fun upsert(playlist: PlaylistRemoteEntity) {
        val dbPlaylist = getPlaylist(playlist.serviceId, playlist.url).firstOrNull()

        if (dbPlaylist == null) {
            insert(playlist)
        } else {
            playlist.uid = dbPlaylist.uid
            update(playlist)
        }
    }

    @Query("DELETE FROM remote_playlists WHERE uid = :playlistId")
    suspend fun deletePlaylist(playlistId: Long): Int
}
