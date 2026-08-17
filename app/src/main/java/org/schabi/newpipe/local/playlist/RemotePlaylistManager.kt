/*
 * SPDX-FileCopyrightText: 2018-2025 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.playlist

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.extractor.playlist.PlaylistInfo

class RemotePlaylistManager(private val database: AppDatabase) {
    private val playlistRemoteTable = database.playlistRemoteDAO()

    val playlists: Flow<List<PlaylistRemoteEntity>>
        get() = playlistRemoteTable.getPlaylists()

    fun getPlaylist(playlistId: Long): Flow<PlaylistRemoteEntity> {
        return playlistRemoteTable.getPlaylist(playlistId)
    }

    fun getPlaylist(info: PlaylistInfo): Flow<List<PlaylistRemoteEntity>> {
        return playlistRemoteTable.getPlaylist(info.serviceId.toLong(), info.url)
    }

    suspend fun deletePlaylist(playlistId: Long): Int {
        return playlistRemoteTable.deletePlaylist(playlistId)
    }

    suspend fun updatePlaylists(
        updateItems: List<PlaylistRemoteEntity>,
        deletedItems: List<Long>
    ) {
        database.withTransaction {
            deletedItems.forEach { playlistRemoteTable.deletePlaylist(it) }
            updateItems.forEach { playlistRemoteTable.upsert(it) }
        }
    }

    suspend fun onBookmark(playlistInfo: PlaylistInfo): Long {
        val playlist = PlaylistRemoteEntity(playlistInfo)
        return playlistRemoteTable.upsert(playlist)
    }

    suspend fun onUpdate(playlistId: Long, playlistInfo: PlaylistInfo): Int {
        val playlist = PlaylistRemoteEntity(playlistInfo).apply { uid = playlistId }
        return playlistRemoteTable.update(playlist)
    }
}
