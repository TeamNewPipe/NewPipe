/*
 * SPDX-FileCopyrightText: 2018-2025 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.playlist.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import io.reactivex.rxjava3.core.Flowable
import org.schabi.newpipe.database.BasicDAO
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity

@Dao
interface PlaylistRemoteDAO : BasicDAO<PlaylistRemoteEntity> {

    @Query("SELECT * FROM remote_playlists")
    override fun getAll(): Flowable<List<PlaylistRemoteEntity>>

    @Query("DELETE FROM remote_playlists")
    override fun deleteAll(): Int

    @Query("SELECT * FROM remote_playlists WHERE service_id = :serviceId")
    override fun listByService(serviceId: Int): Flowable<List<PlaylistRemoteEntity>>

    @Query("SELECT * FROM remote_playlists WHERE uid = :playlistId")
    fun getPlaylist(playlistId: Long): Flowable<PlaylistRemoteEntity>

    @Query("SELECT * FROM remote_playlists WHERE url = :url AND uid = :serviceId")
    fun getPlaylist(serviceId: Long, url: String?): Flowable<MutableList<PlaylistRemoteEntity>>

    @get:Query("SELECT * FROM remote_playlists ORDER BY display_index")
    val playlists: Flowable<MutableList<PlaylistRemoteEntity>>

    @Query("SELECT uid FROM remote_playlists WHERE url = :url AND service_id = :serviceId")
    fun getPlaylistIdInternal(serviceId: Long, url: String?): Long?

    @Transaction
    fun upsert(playlist: PlaylistRemoteEntity): Long {
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
    fun deletePlaylist(playlistId: Long): Int
}
