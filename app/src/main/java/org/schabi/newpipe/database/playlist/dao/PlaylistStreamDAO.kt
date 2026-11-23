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
import org.schabi.newpipe.database.stream.model.StreamEntity

data class QuickAddStreamKey(val serviceId: Int, val url: String)

@Dao
interface PlaylistStreamDAO : BasicDAO<PlaylistStreamEntity> {

    @Query("SELECT s.service_id AS serviceId, s.url FROM streams s INNER JOIN playlist_stream_join j ON j.stream_id = s.uid INNER JOIN playlists p ON p.uid = j.playlist_id WHERE p.profile_id = :profileId AND p.uid = :playlistId")
    fun observeQuickAddStreams(profileId: String, playlistId: Long): Flowable<List<QuickAddStreamKey>>

    @Query("SELECT * FROM playlist_stream_join WHERE playlist_id = :playlistId ORDER BY join_index")
    fun getQuickAddEntries(playlistId: Long): List<PlaylistStreamEntity>

    @Query("SELECT * FROM playlist_stream_join")
    override fun getAll(): Flowable<List<PlaylistStreamEntity>>

    @Query("DELETE FROM playlist_stream_join")
    override fun deleteAll(): Int

    override fun listByService(serviceId: Int): Flowable<List<PlaylistStreamEntity>> {
        throw UnsupportedOperationException()
    }

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM playlists
            WHERE profile_id = :profileId AND uid = :playlistId
        )
        """
    )
    fun playlistBelongsToProfile(profileId: String, playlistId: Long): Boolean

    @Query("DELETE FROM playlist_stream_join WHERE playlist_id = :playlistId")
    fun deleteBatch(playlistId: Long)

    @Query(
        """
        DELETE FROM playlist_stream_join
        WHERE playlist_id IN (
            SELECT uid FROM playlists
            WHERE profile_id = :profileId AND uid = :playlistId
        )
        """
    )
    fun deleteBatchForProfile(profileId: String, playlistId: Long): Int

    @Query(
        """
        SELECT COALESCE(MAX(join_index), -1)
        FROM playlist_stream_join
        WHERE playlist_id = :playlistId
        """
    )
    fun getMaximumIndexOf(playlistId: Long): Flowable<Int>

    @Query(
        """
        SELECT COALESCE(MAX(psj.join_index), -1)
        FROM playlist_stream_join psj
        INNER JOIN playlists p ON p.uid = psj.playlist_id
        WHERE p.profile_id = :profileId AND psj.playlist_id = :playlistId
        """
    )
    fun getMaximumIndexOfForProfile(
        profileId: String,
        playlistId: Long
    ): Flowable<Int>

    @Query(
        """
        SELECT COALESCE(MAX(join_index), -1)
        FROM playlist_stream_join
        WHERE playlist_id = :playlistId
        """
    )
    fun getMaximumIndexDirect(playlistId: Long): Int

    @Query("SELECT stream_id FROM playlist_stream_join WHERE playlist_id = :playlistId ORDER BY join_index LIMIT 1")
    fun getFirstStreamIdDirect(playlistId: Long): Long?

    @Query(
        """
        SELECT CASE
            WHEN COUNT(*) != 0 THEN stream_id
            ELSE $DEFAULT_THUMBNAIL_ID
        END
        FROM streams
        LEFT JOIN playlist_stream_join ON uid = stream_id
        WHERE playlist_id = :playlistId
        LIMIT 1
        """
    )
    fun getAutomaticThumbnailStreamId(playlistId: Long): Flowable<Long>

    @Query(
        """
        SELECT CASE
            WHEN COUNT(*) != 0 THEN psj.stream_id
            ELSE $DEFAULT_THUMBNAIL_ID
        END
        FROM playlist_stream_join psj
        INNER JOIN playlists p ON p.uid = psj.playlist_id
        WHERE p.profile_id = :profileId AND psj.playlist_id = :playlistId
        LIMIT 1
        """
    )
    fun getAutomaticThumbnailStreamIdForProfile(
        profileId: String,
        playlistId: Long
    ): Flowable<Long>

    @RewriteQueriesToDropUnusedColumns
    @Transaction
    @Query(
        """
        SELECT * FROM streams
        INNER JOIN (
            SELECT stream_id, join_index
            FROM playlist_stream_join
            WHERE playlist_id = :playlistId
        )
        ON uid = stream_id
        LEFT JOIN (
            SELECT stream_id AS stream_id_alias, progress_time
            FROM stream_state
        )
        ON uid = stream_id_alias
        ORDER BY join_index ASC
        """
    )
    fun getOrderedStreamsOf(playlistId: Long): Flowable<MutableList<PlaylistStreamEntry>>

    @RewriteQueriesToDropUnusedColumns
    @Transaction
    @Query(
        """
        SELECT * FROM streams
        INNER JOIN (
            SELECT psj.stream_id, psj.join_index
            FROM playlist_stream_join psj
            INNER JOIN playlists p ON p.uid = psj.playlist_id
            WHERE p.profile_id = :profileId
            AND psj.playlist_id = :playlistId
        )
        ON uid = stream_id
        LEFT JOIN (
            SELECT stream_id AS stream_id_alias, progress_time
            FROM stream_state
            WHERE profile_id = :profileId
        )
        ON uid = stream_id_alias
        ORDER BY join_index ASC
        """
    )
    fun getOrderedStreamsOfForProfile(
        profileId: String,
        playlistId: Long
    ): Flowable<MutableList<PlaylistStreamEntry>>

    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT streams.*
        FROM streams
        INNER JOIN playlist_stream_join ON streams.uid = stream_id
        WHERE playlist_id = :playlistId
        ORDER BY join_index ASC
        """
    )
    fun getOrderedStreamsDirect(playlistId: Long): List<StreamEntity>

    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT streams.*
        FROM streams
        INNER JOIN playlist_stream_join psj ON streams.uid = psj.stream_id
        INNER JOIN playlists p ON p.uid = psj.playlist_id
        WHERE p.profile_id = :profileId AND psj.playlist_id = :playlistId
        ORDER BY psj.join_index ASC
        """
    )
    fun getOrderedStreamsDirectForProfile(
        profileId: String,
        playlistId: Long
    ): List<StreamEntity>

    @Transaction
    @Query(
        """
        SELECT uid, name, is_thumbnail_permanent, thumbnail_stream_id, display_index,
        (
            SELECT thumbnail_url
            FROM streams
            WHERE streams.uid = thumbnail_stream_id
        ) AS thumbnail_url,
        COALESCE(COUNT(playlist_id), 0) AS streamCount
        FROM playlists
        LEFT JOIN playlist_stream_join ON playlists.uid = playlist_id
        GROUP BY uid
        ORDER BY display_index
        """
    )
    fun getPlaylistMetadata(): Flowable<MutableList<PlaylistMetadataEntry>>

    @Transaction
    @Query(
        """
        SELECT playlists.uid, name, is_thumbnail_permanent, thumbnail_stream_id,
        display_index,
        (
            SELECT thumbnail_url
            FROM streams
            WHERE streams.uid = thumbnail_stream_id
        ) AS thumbnail_url,
        COALESCE(COUNT(playlist_id), 0) AS streamCount
        FROM playlists
        LEFT JOIN playlist_stream_join ON playlists.uid = playlist_id
        WHERE playlists.profile_id = :profileId
        GROUP BY playlists.uid
        ORDER BY display_index
        """
    )
    fun getPlaylistMetadataForProfile(
        profileId: String
    ): Flowable<MutableList<PlaylistMetadataEntry>>

    @RewriteQueriesToDropUnusedColumns
    @Transaction
    @Query(
        """
        SELECT *, MIN(join_index) FROM streams
        INNER JOIN (
            SELECT stream_id, join_index
            FROM playlist_stream_join
            WHERE playlist_id = :playlistId
        )
        ON uid = stream_id
        LEFT JOIN (
            SELECT stream_id AS stream_id_alias, progress_time
            FROM stream_state
        )
        ON uid = stream_id_alias
        GROUP BY uid
        ORDER BY MIN(join_index) ASC
        """
    )
    fun getStreamsWithoutDuplicates(playlistId: Long): Flowable<MutableList<PlaylistStreamEntry>>

    @RewriteQueriesToDropUnusedColumns
    @Transaction
    @Query(
        """
        SELECT *, MIN(join_index) FROM streams
        INNER JOIN (
            SELECT psj.stream_id, psj.join_index
            FROM playlist_stream_join psj
            INNER JOIN playlists p ON p.uid = psj.playlist_id
            WHERE p.profile_id = :profileId
            AND psj.playlist_id = :playlistId
        )
        ON uid = stream_id
        LEFT JOIN (
            SELECT stream_id AS stream_id_alias, progress_time
            FROM stream_state
            WHERE profile_id = :profileId
        )
        ON uid = stream_id_alias
        GROUP BY uid
        ORDER BY MIN(join_index) ASC
        """
    )
    fun getStreamsWithoutDuplicatesForProfile(
        profileId: String,
        playlistId: Long
    ): Flowable<MutableList<PlaylistStreamEntry>>

    @Transaction
    @Query(
        """
        SELECT playlists.uid, name, is_thumbnail_permanent, thumbnail_stream_id,
        display_index,
        (
            SELECT thumbnail_url
            FROM streams
            WHERE streams.uid = thumbnail_stream_id
        ) AS thumbnail_url,
        COALESCE(COUNT(playlist_id), 0) AS streamCount,
        COALESCE(SUM(url = :streamUrl), 0) AS timesStreamIsContained
        FROM playlists
        LEFT JOIN playlist_stream_join ON playlists.uid = playlist_id
        LEFT JOIN streams ON streams.uid = stream_id AND :streamUrl = :streamUrl
        GROUP BY playlists.uid
        ORDER BY display_index, name
        """
    )
    fun getPlaylistDuplicatesMetadata(
        streamUrl: String
    ): Flowable<MutableList<PlaylistDuplicatesEntry>>

    @Transaction
    @Query(
        """
        SELECT playlists.uid, name, is_thumbnail_permanent, thumbnail_stream_id,
        display_index,
        (
            SELECT thumbnail_url
            FROM streams
            WHERE streams.uid = thumbnail_stream_id
        ) AS thumbnail_url,
        COALESCE(COUNT(playlist_id), 0) AS streamCount,
        COALESCE(SUM(url = :streamUrl), 0) AS timesStreamIsContained
        FROM playlists
        LEFT JOIN playlist_stream_join ON playlists.uid = playlist_id
        LEFT JOIN streams ON streams.uid = stream_id AND :streamUrl = :streamUrl
        WHERE playlists.profile_id = :profileId
        GROUP BY playlists.uid
        ORDER BY display_index, name
        """
    )
    fun getPlaylistDuplicatesMetadataForProfile(
        profileId: String,
        streamUrl: String
    ): Flowable<MutableList<PlaylistDuplicatesEntry>>

    @Transaction
    fun insertAllForProfile(
        profileId: String,
        playlistId: Long,
        entities: Collection<PlaylistStreamEntity>
    ): List<Long> {
        if (!playlistBelongsToProfile(profileId, playlistId) ||
            entities.any { it.playlistUid != playlistId }
        ) {
            return emptyList()
        }
        return insertAll(entities)
    }
}
