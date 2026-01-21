/*
 * SPDX-FileCopyrightText: 2018-2024 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.playlist.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import io.reactivex.rxjava3.core.Flowable
import org.schabi.newpipe.database.BasicDAO
import org.schabi.newpipe.database.playlist.PlaylistDuplicatesEntry
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.database.playlist.model.PlaylistEntity.Companion.DEFAULT_THUMBNAIL_ID
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity

@Dao
interface PlaylistStreamDAO : BasicDAO<PlaylistStreamEntity> {

    @Query("SELECT * FROM playlist_stream_join")
    override fun getAll(): Flowable<List<PlaylistStreamEntity>>

    @Query("DELETE FROM playlist_stream_join")
    override fun deleteAll(): Int

    override fun listByService(serviceId: Int): Flowable<List<PlaylistStreamEntity>> {
        throw UnsupportedOperationException()
    }

    @Query("DELETE FROM playlist_stream_join WHERE playlist_id = :playlistId")
    fun deleteBatch(playlistId: Long)

    @Query("SELECT COALESCE(MAX(join_index), -1) FROM playlist_stream_join WHERE playlist_id = :playlistId")
    fun getMaximumIndexOf(playlistId: Long): Flowable<Int>

    @Query(
        """
        SELECT CASE WHEN COUNT(*) != 0 then stream_id ELSE $DEFAULT_THUMBNAIL_ID END
        FROM streams

        LEFT JOIN playlist_stream_join
        ON uid = stream_id

        WHERE playlist_id = :playlistId LIMIT 1
        """
    )
    fun getAutomaticThumbnailStreamId(playlistId: Long): Flowable<Long>

    // get ids of streams of the given playlist then merge with the stream metadata
    @RewriteQueriesToDropUnusedColumns
    @Transaction
    @Query(
        """
        SELECT * FROM streams

        INNER JOIN (SELECT stream_id, join_index FROM playlist_stream_join WHERE playlist_id = :playlistId)
        ON uid = stream_id

        LEFT JOIN (SELECT stream_id AS stream_id_alias, progress_time FROM stream_state )
        ON uid = stream_id_alias

        ORDER BY join_index ASC
        """
    )
    fun getOrderedStreamsOf(playlistId: Long): Flowable<MutableList<PlaylistStreamEntry>>

    @Transaction
    @Query(
        """
        SELECT uid, name,
        (SELECT thumbnail_url FROM streams WHERE streams.uid = thumbnail_stream_id) AS thumbnail_url,
        display_index, is_thumbnail_permanent, thumbnail_stream_id, folder_id,
        COALESCE(COUNT(playlist_id), 0) AS streamCount FROM playlists

        LEFT JOIN playlist_stream_join
        ON playlists.uid = playlist_id

        GROUP BY uid
        ORDER BY display_index
        """
    )
    fun getPlaylistMetadata(): Flowable<MutableList<PlaylistMetadataEntry>>

    @RewriteQueriesToDropUnusedColumns
    @Transaction
    @Query(
        """
        SELECT *, MIN(join_index) FROM streams

        INNER JOIN (SELECT stream_id, join_index FROM playlist_stream_join WHERE playlist_id = :playlistId)
        ON uid = stream_id

        LEFT JOIN (SELECT stream_id AS stream_id_alias, progress_time FROM stream_state )
        ON uid = stream_id_alias

        GROUP BY uid
        ORDER BY MIN(join_index) ASC
        """
    )
    fun getStreamsWithoutDuplicates(playlistId: Long): Flowable<MutableList<PlaylistStreamEntry>>

    @Transaction
    @Query(
        """
        SELECT playlists.uid, name,
        (SELECT thumbnail_url FROM streams WHERE streams.uid = thumbnail_stream_id) AS thumbnail_url,
        display_index, is_thumbnail_permanent, thumbnail_stream_id, folder_id,

        COALESCE(COUNT(playlist_id), 0) AS streamCount,
        COALESCE(SUM(url = :streamUrl), 0) AS timesStreamIsContained FROM playlists

        LEFT JOIN playlist_stream_join
        ON playlists.uid = playlist_id

        LEFT JOIN streams
        ON streams.uid = stream_id AND :streamUrl = :streamUrl

        GROUP BY playlist_id
        ORDER BY display_index, name
        """
    )
    fun getPlaylistDuplicatesMetadata(streamUrl: String): Flowable<MutableList<PlaylistDuplicatesEntry>>
}
