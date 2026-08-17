/*
 * SPDX-FileCopyrightText: 2018-2022 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.playlist.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.schabi.newpipe.database.BasicDAO
import org.schabi.newpipe.database.playlist.model.PlaylistEntity

@Dao
interface PlaylistDAO : BasicDAO<PlaylistEntity> {

    @Query("SELECT * FROM playlists")
    override fun getAll(): Flow<List<PlaylistEntity>>

    @Query("DELETE FROM playlists")
    override suspend fun deleteAll(): Int

    override fun listByService(serviceId: Int): Flow<List<PlaylistEntity>> {
        throw UnsupportedOperationException()
    }

    @Query("SELECT * FROM playlists WHERE uid = :playlistId")
    fun getPlaylist(playlistId: Long): Flow<List<PlaylistEntity>>

    @Query("DELETE FROM playlists WHERE uid = :playlistId")
    suspend fun deletePlaylist(playlistId: Long): Int

    @Query("SELECT COUNT(*) FROM playlists")
    fun getCount(): Flow<Long>

    @Transaction
    suspend fun upsertPlaylist(playlist: PlaylistEntity): Long {
        if (playlist.uid == -1L) {
            // This situation is probably impossible.
            return insert(playlist)
        } else {
            update(playlist)
            return playlist.uid
        }
    }
}
