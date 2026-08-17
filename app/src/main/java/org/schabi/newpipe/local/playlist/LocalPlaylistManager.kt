package org.schabi.newpipe.local.playlist

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
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

    companion object {
        private const val THUMBNAIL_ID_LEAVE_UNCHANGED = -2L
    }

    suspend fun createPlaylist(name: String, streams: List<StreamEntity>): List<Long>? {
        // Disallow creation of empty playlists
        if (streams.isEmpty()) {
            return null
        }

        // Save to the database directly.
        // Make sure the new playlist is always on the top of bookmark.
        // The index will be reassigned to non-negative number in BookmarkFragment.
        return database.withTransaction {
            val streamIds = streamTable.upsertAll(streams)
            val newPlaylist = PlaylistEntity(
                name = name,
                isThumbnailPermanent = false,
                thumbnailStreamId = streamIds[0],
                displayIndex = -1L
            )

            insertJoinEntities(
                playlistTable.insert(newPlaylist),
                streamIds,
                0
            )
        }
    }

    suspend fun appendToPlaylist(playlistId: Long, streams: List<StreamEntity>): List<Long>? {
        val maxJoinIndex = playlistStreamTable.getMaximumIndexOf(playlistId).firstOrNull() ?: -1
        return database.withTransaction {
            val streamIds = streamTable.upsertAll(streams)
            insertJoinEntities(playlistId, streamIds, maxJoinIndex + 1)
        }
    }

    private suspend fun insertJoinEntities(
        playlistId: Long,
        streamIds: List<Long>,
        indexOffset: Int
    ): List<Long> {
        val joinEntities = streamIds.mapIndexed { index, streamId ->
            PlaylistStreamEntity(playlistId, streamId, index + indexOffset)
        }
        return playlistStreamTable.insertAll(joinEntities)
    }

    suspend fun updateJoin(playlistId: Long, streamIds: List<Long>) {
        val joinEntities = streamIds.mapIndexed { index, streamId ->
            PlaylistStreamEntity(playlistId, streamId, index)
        }

        database.withTransaction {
            playlistStreamTable.deleteBatch(playlistId)
            playlistStreamTable.insertAll(joinEntities)
        }
    }

    suspend fun updatePlaylists(
        updateItems: List<PlaylistMetadataEntry>,
        deletedItems: List<Long>
    ) {
        val items = updateItems.map { PlaylistEntity(it) }
        database.withTransaction {
            for (uid in deletedItems) {
                playlistTable.deletePlaylist(uid)
            }
            for (item in items) {
                playlistTable.upsertPlaylist(item)
            }
        }
    }

    fun getDistinctPlaylistStreams(playlistId: Long): Flow<List<PlaylistStreamEntry>> {
        return playlistStreamTable.getStreamsWithoutDuplicates(playlistId)
    }

    /**
     * Get playlists with attached information about how many times the provided stream is already
     * contained in each playlist.
     *
     * @param streamUrl the stream url for which to check for duplicates
     * @return a list of {@link PlaylistDuplicatesEntry}
     */
    fun getPlaylistDuplicates(streamUrl: String): Flow<List<PlaylistDuplicatesEntry>> {
        return playlistStreamTable.getPlaylistDuplicatesMetadata(streamUrl)
    }

    fun getPlaylists(): Flow<List<PlaylistMetadataEntry>> {
        return playlistStreamTable.getPlaylistMetadata()
    }

    fun getPlaylistStreams(playlistId: Long): Flow<List<PlaylistStreamEntry>> {
        return playlistStreamTable.getOrderedStreamsOf(playlistId)
    }

    suspend fun renamePlaylist(playlistId: Long, name: String): Int? {
        return modifyPlaylist(playlistId, name, THUMBNAIL_ID_LEAVE_UNCHANGED, false)
    }

    suspend fun changePlaylistThumbnail(
        playlistId: Long,
        thumbnailStreamId: Long,
        isPermanent: Boolean
    ): Int? {
        return modifyPlaylist(playlistId, null, thumbnailStreamId, isPermanent)
    }

    suspend fun getPlaylistThumbnailStreamId(playlistId: Long): Long {
        return playlistTable.getPlaylist(playlistId).first()[0].thumbnailStreamId
    }

    suspend fun getIsPlaylistThumbnailPermanent(playlistId: Long): Boolean {
        return playlistTable.getPlaylist(playlistId).first()[0].isThumbnailPermanent
    }

    suspend fun getAutomaticPlaylistThumbnailStreamId(playlistId: Long): Long {
        val streamId = playlistStreamTable.getAutomaticThumbnailStreamId(playlistId).first().toLong()
        return if (streamId < 0) {
            PlaylistEntity.DEFAULT_THUMBNAIL_ID
        } else {
            streamId
        }
    }

    private suspend fun modifyPlaylist(
        playlistId: Long,
        name: String?,
        thumbnailStreamId: Long,
        isPermanent: Boolean
    ): Int? {
        val playlistEntities = playlistTable.getPlaylist(playlistId).firstOrNull()
        if (playlistEntities.isNullOrEmpty()) return null

        val playlist = playlistEntities[0]
        if (name != null) {
            playlist.name = name
        }
        if (thumbnailStreamId != THUMBNAIL_ID_LEAVE_UNCHANGED) {
            playlist.thumbnailStreamId = thumbnailStreamId
            playlist.isThumbnailPermanent = isPermanent
        }
        return playlistTable.update(playlist)
    }

    suspend fun hasPlaylists(): Boolean {
        val count = playlistTable.getCount().firstOrNull() ?: 0L
        return count > 0
    }
}
