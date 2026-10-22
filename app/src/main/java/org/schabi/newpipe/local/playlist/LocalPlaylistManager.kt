/*
 * SPDX-FileCopyrightText: 2018-2024 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025-2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.playlist

import androidx.room3.withWriteTransaction
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.rx3.rxMaybe
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.playlist.PlaylistDuplicatesEntry
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.database.stream.model.StreamEntity

class LocalPlaylistManager(private val database: AppDatabase) {

    private val streamTable = database.streamDAO()
    private val playlistTable = database.playlistDAO()
    private val playlistStreamTable = database.playlistStreamDAO()

    fun createPlaylist(name: String, streams: List<StreamEntity>): Maybe<List<Long>> {
        // Disallow creation of empty playlists
        if (streams.isEmpty()) {
            return Maybe.empty()
        }

        // Save to the database directly.
        // Make sure the new playlist is always on the top of bookmark.
        // The index will be reassigned to non-negative number in BookmarkFragment.
        return rxMaybe(Dispatchers.IO) {
            database.withWriteTransaction {
                val streamIds = streamTable.upsertAll(streams)
                val newPlaylist = PlaylistEntity(
                    name = name,
                    isThumbnailPermanent = false,
                    thumbnailStreamId = streamIds[0],
                    displayIndex = -1
                )
                insertJoinEntities(playlistTable.insert(newPlaylist), streamIds, 0)
            }
        }
    }

    fun appendToPlaylist(playlistId: Long, streams: List<StreamEntity>): Maybe<List<Long>> {
        return playlistStreamTable.getMaximumIndexOf(playlistId)
            .firstElement()
            .map { maxJoinIndex ->
                runBlocking {
                    database.withWriteTransaction<List<Long>> {
                        val streamIds = streamTable.upsertAll(streams)
                        insertJoinEntities(playlistId, streamIds, maxJoinIndex + 1)
                    }
                }
            }.subscribeOn(Schedulers.io())
    }

    private fun insertJoinEntities(
        playlistId: Long,
        streamIds: List<Long>,
        indexOffset: Int
    ): List<Long> {
        val entities = streamIds.mapIndexed { index, streamId ->
            PlaylistStreamEntity(playlistId, streamId, index + indexOffset)
        }
        return playlistStreamTable.insertAll(entities)
    }

    fun updateJoin(playlistId: Long, streamIds: List<Long>): Completable {
        val joinEntities = streamIds.mapIndexed { index, streamId ->
            PlaylistStreamEntity(playlistId, streamId, index)
        }

        return Completable.fromRunnable {
            playlistStreamTable.updatePlaylistStreams(playlistId, joinEntities)
        }.subscribeOn(Schedulers.io())
    }

    fun updatePlaylists(
        updateItems: List<PlaylistMetadataEntry>,
        deletedItems: List<Long>
    ): Completable {
        return Completable.fromRunnable {
            playlistTable.updatePlaylists(updateItems = updateItems, deletedItems = deletedItems)
        }.subscribeOn(Schedulers.io())
    }

    fun getDistinctPlaylistStreams(playlistId: Long): Flowable<MutableList<PlaylistStreamEntry>> {
        return playlistStreamTable
            .getStreamsWithoutDuplicates(playlistId)
            .subscribeOn(Schedulers.io())
    }

    /**
     * Get playlists with attached information about how many times the provided stream is already
     * contained in each playlist.
     *
     * @param streamUrl the stream url for which to check for duplicates
     * @return a list of [PlaylistDuplicatesEntry]
     */
    fun getPlaylistDuplicates(streamUrl: String): Flowable<MutableList<PlaylistDuplicatesEntry>> {
        return playlistStreamTable.getPlaylistDuplicatesMetadata(streamUrl)
            .subscribeOn(Schedulers.io())
    }

    fun getPlaylists(): Flowable<MutableList<PlaylistMetadataEntry>> {
        return playlistStreamTable.getPlaylistMetadata().subscribeOn(Schedulers.io())
    }

    fun getPlaylistStreams(playlistId: Long): Flowable<MutableList<PlaylistStreamEntry>> {
        return playlistStreamTable.getOrderedStreamsOf(playlistId).subscribeOn(Schedulers.io())
    }

    fun renamePlaylist(playlistId: Long, name: String): Maybe<Int> {
        return modifyPlaylist(playlistId, name, THUMBNAIL_ID_LEAVE_UNCHANGED, false)
    }

    fun changePlaylistThumbnail(
        playlistId: Long,
        thumbnailStreamId: Long,
        isPermanent: Boolean
    ): Maybe<Int> {
        return modifyPlaylist(playlistId, null, thumbnailStreamId, isPermanent)
    }

    fun getPlaylistThumbnailStreamId(playlistId: Long): Long {
        return playlistTable.getPlaylist(playlistId).blockingFirst()[0].thumbnailStreamId
    }

    fun getIsPlaylistThumbnailPermanent(playlistId: Long): Boolean {
        return playlistTable.getPlaylist(playlistId).blockingFirst()[0].isThumbnailPermanent
    }

    fun getAutomaticPlaylistThumbnailStreamId(playlistId: Long): Long {
        val streamId = playlistStreamTable.getAutomaticThumbnailStreamId(playlistId)
            .blockingFirst()
        return if (streamId < 0) PlaylistEntity.DEFAULT_THUMBNAIL_ID else streamId
    }

    private fun modifyPlaylist(
        playlistId: Long,
        name: String?,
        thumbnailStreamId: Long,
        isPermanent: Boolean
    ): Maybe<Int> {
        return playlistTable.getPlaylist(playlistId)
            .firstElement()
            .filter { playlistEntities -> playlistEntities.isNotEmpty() }
            .map { playlistEntities ->
                val playlist = playlistEntities[0]
                if (name != null) {
                    playlist.name = name
                }
                if (thumbnailStreamId != THUMBNAIL_ID_LEAVE_UNCHANGED) {
                    playlist.thumbnailStreamId = thumbnailStreamId
                    playlist.isThumbnailPermanent = isPermanent
                }
                playlistTable.update(playlist)
            }.subscribeOn(Schedulers.io())
    }

    fun hasPlaylists(): Maybe<Boolean> {
        return playlistTable.count
            .firstElement()
            .map { count -> count > 0 }
            .subscribeOn(Schedulers.io())
    }

    companion object {
        private const val THUMBNAIL_ID_LEAVE_UNCHANGED = -2L
    }
}
