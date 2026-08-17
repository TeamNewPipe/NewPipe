/*
 * SPDX-FileCopyrightText: 2018-2025 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.playlist.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.schabi.newpipe.database.BasicDAO
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity

@Dao
interface PlaylistRemoteDAO : BasicDAO<PlaylistRemoteEntity> {

    @Query("SELECT * FROM remote_playlists")
    override fun getAll(): Flow<List<PlaylistRemoteEntity>>

    @Query("DELETE FROM remote_playlists")
    override suspend fun deleteAll(): Int

    @Query("SELECT * FROM remote_playlists WHERE service_id = :serviceId")
    override fun listByService(serviceId: Int): Flow<List<PlaylistRemoteEntity>>

    @Query("SELECT * FROM remote_playlists WHERE uid = :playlistId")
    fun getPlaylist(playlistId: Long): Flow<PlaylistRemoteEntity>

    @Query("SELECT * FROM remote_playlists WHERE url = :url AND service_id = :serviceId")
    fun getPlaylist(serviceId: Long, url: String?): Flow<List<PlaylistRemoteEntity>>

    @Query("SELECT * FROM remote_playlists ORDER BY display_index")
    fun getPlaylists(): Flow<List<PlaylistRemoteEntity>>

    @Query("SELECT uid FROM remote_playlists WHERE url = :url AND service_id = :serviceId")
    suspend fun getPlaylistIdInternal(serviceId: Long, url: String?): Long?

    @Transaction
    suspend fun upsert(playlist: PlaylistRemoteEntity): Long {
        val playlistId = getPlaylistIdInternal(playlist.serviceId.toLong(), playlist.url)

        if (playlistId == null) {
            return insert(playlist)
        } else {
            playlist.uid = playlistId
            update(playlist)
            return playlistId
        }
    }

    @Query("DELETE FROM remote_playlists WHERE uid = :playlistId")
    suspend fun deletePlaylist(playlistId: Long): Int
}
