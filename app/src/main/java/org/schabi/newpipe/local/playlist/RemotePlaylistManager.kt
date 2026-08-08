/*
 * SPDX-FileCopyrightText: 2018-2025 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.playlist

import androidx.room.withTransaction
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.rx3.rxCompletable
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.extractor.playlist.PlaylistInfo

class RemotePlaylistManager(private val database: AppDatabase) {
    private val playlistRemoteTable = database.playlistRemoteDAO()

    val playlists: Flowable<List<PlaylistRemoteEntity>>
        get() = playlistRemoteTable.getPlaylists().subscribeOn(Schedulers.io())

    fun getPlaylist(playlistId: Long): Flowable<PlaylistRemoteEntity> {
        return playlistRemoteTable.getPlaylist(playlistId).subscribeOn(Schedulers.io())
    }

    fun updatePlaylists(
        updateItems: List<PlaylistRemoteEntity>,
        deletedItems: List<Long>
    ) = rxCompletable(Dispatchers.IO) {
        database.withTransaction {
            deletedItems.forEach { playlistRemoteTable.deletePlaylist(it) }
            updateItems.forEach { playlistRemoteTable.upsert(it) }
        }
    }
}
