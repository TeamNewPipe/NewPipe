/*
 * SPDX-FileCopyrightText: 2018-2022 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.playlist.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import io.reactivex.rxjava3.core.Flowable
import org.schabi.newpipe.database.BasicDAO
import org.schabi.newpipe.database.playlist.model.PlaylistEntity

@Dao
interface PlaylistDAO : BasicDAO<PlaylistEntity> {

    @Query("SELECT * FROM playlists")
    override fun getAll(): Flowable<List<PlaylistEntity>>

    @Query("DELETE FROM playlists")
    override fun deleteAll(): Int

    override fun listByService(serviceId: Int): Flowable<List<PlaylistEntity>> {
        throw UnsupportedOperationException()
    }

    @Query("SELECT * FROM playlists WHERE uid = :playlistId")
    fun getPlaylist(playlistId: Long): Flowable<MutableList<PlaylistEntity>>

    @Query("DELETE FROM playlists WHERE uid = :playlistId")
    fun deletePlaylist(playlistId: Long): Int

    @get:Query("SELECT COUNT(*) FROM playlists")
    val count: Flowable<Long>

    @Transaction
    fun upsertPlaylist(playlist: PlaylistEntity): Long {
        if (playlist.uid == -1L) {
            // This situation is probably impossible.
            return insert(playlist)
        } else {
            update(playlist)
            return playlist.uid
        }
    }
}
