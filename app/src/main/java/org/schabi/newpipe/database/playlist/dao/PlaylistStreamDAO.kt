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
import org.schabi.newpipe.database.playlist.model.PlaylistEntity
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity

@Dao
open interface PlaylistStreamDAO : BasicDAO<PlaylistStreamEntity?> {
    @Query("SELECT * FROM " + PlaylistStreamEntity.Companion.PLAYLIST_STREAM_JOIN_TABLE)
    public override fun getAll(): Flowable<List<PlaylistStreamEntity?>?>?
    @Query("DELETE FROM " + PlaylistStreamEntity.Companion.PLAYLIST_STREAM_JOIN_TABLE)
    public override fun deleteAll(): Int
    public override fun listByService(serviceId: Int): Flowable<List<PlaylistStreamEntity?>?>? {
        throw UnsupportedOperationException()
    }

    @Query(("DELETE FROM " + PlaylistStreamEntity.Companion.PLAYLIST_STREAM_JOIN_TABLE
            + " WHERE " + PlaylistStreamEntity.Companion.JOIN_PLAYLIST_ID + " = :playlistId"))
    fun deleteBatch(playlistId: Long)

    @Query(("SELECT COALESCE(MAX(" + PlaylistStreamEntity.Companion.JOIN_INDEX + "), -1)"
            + " FROM " + PlaylistStreamEntity.Companion.PLAYLIST_STREAM_JOIN_TABLE
            + " WHERE " + PlaylistStreamEntity.Companion.JOIN_PLAYLIST_ID + " = :playlistId"))
    fun getMaximumIndexOf(playlistId: Long): Flowable<Int?>

    @Query(("SELECT CASE WHEN COUNT(*) != 0 then " + StreamEntity.STREAM_ID
            + " ELSE " + PlaylistEntity.Companion.DEFAULT_THUMBNAIL_ID + " END"
            + " FROM " + StreamEntity.STREAM_TABLE
            + " LEFT JOIN " + PlaylistStreamEntity.Companion.PLAYLIST_STREAM_JOIN_TABLE
            + " ON " + StreamEntity.STREAM_ID + " = " + PlaylistStreamEntity.Companion.JOIN_STREAM_ID
            + " WHERE " + PlaylistStreamEntity.Companion.JOIN_PLAYLIST_ID + " = :playlistId "
            + " LIMIT 1"))
    fun getAutomaticThumbnailStreamId(playlistId: Long): Flowable<Long>

    @RewriteQueriesToDropUnusedColumns
    @Transaction
    @Query(("SELECT * FROM " + StreamEntity.STREAM_TABLE + " INNER JOIN " // get ids of streams of the given playlist
            + "(SELECT " + PlaylistStreamEntity.Companion.JOIN_STREAM_ID + "," + PlaylistStreamEntity.Companion.JOIN_INDEX
            + " FROM " + PlaylistStreamEntity.Companion.PLAYLIST_STREAM_JOIN_TABLE
            + " WHERE " + PlaylistStreamEntity.Companion.JOIN_PLAYLIST_ID + " = :playlistId)" // then merge with the stream metadata
            + " ON " + StreamEntity.STREAM_ID + " = " + PlaylistStreamEntity.Companion.JOIN_STREAM_ID
            + " LEFT JOIN "
            + "(SELECT " + PlaylistStreamEntity.Companion.JOIN_STREAM_ID + " AS " + StreamStateEntity.Companion.JOIN_STREAM_ID_ALIAS + ", "
            + StreamStateEntity.Companion.STREAM_PROGRESS_MILLIS
            + " FROM " + StreamStateEntity.Companion.STREAM_STATE_TABLE + " )"
            + " ON " + StreamEntity.STREAM_ID + " = " + StreamStateEntity.Companion.JOIN_STREAM_ID_ALIAS
            + " ORDER BY " + PlaylistStreamEntity.Companion.JOIN_INDEX + " ASC"))
    fun getOrderedStreamsOf(playlistId: Long): Flowable<List<PlaylistStreamEntry?>?>

    @Transaction
    @Query(("SELECT " + PlaylistEntity.Companion.PLAYLIST_ID + ", " + PlaylistEntity.Companion.PLAYLIST_NAME + ", "
            + PlaylistEntity.Companion.PLAYLIST_THUMBNAIL_PERMANENT + ", " + PlaylistEntity.Companion.PLAYLIST_THUMBNAIL_STREAM_ID + ", "
            + PlaylistEntity.Companion.PLAYLIST_DISPLAY_INDEX + ", "
            + " CASE WHEN " + PlaylistEntity.Companion.PLAYLIST_THUMBNAIL_STREAM_ID + " = "
            + PlaylistEntity.Companion.DEFAULT_THUMBNAIL_ID + " THEN " + "'" + PlaylistEntity.Companion.DEFAULT_THUMBNAIL + "'"
            + " ELSE (SELECT " + StreamEntity.STREAM_THUMBNAIL_URL
            + " FROM " + StreamEntity.STREAM_TABLE
            + " WHERE " + StreamEntity.STREAM_TABLE + "." + StreamEntity.STREAM_ID + " = " + PlaylistEntity.Companion.PLAYLIST_THUMBNAIL_STREAM_ID
            + " ) END AS " + PlaylistEntity.Companion.PLAYLIST_THUMBNAIL_URL + ", "
            + "COALESCE(COUNT(" + PlaylistStreamEntity.Companion.JOIN_PLAYLIST_ID + "), 0) AS " + PlaylistMetadataEntry.Companion.PLAYLIST_STREAM_COUNT
            + " FROM " + PlaylistEntity.Companion.PLAYLIST_TABLE
            + " LEFT JOIN " + PlaylistStreamEntity.Companion.PLAYLIST_STREAM_JOIN_TABLE
            + " ON " + PlaylistEntity.Companion.PLAYLIST_TABLE + "." + PlaylistEntity.Companion.PLAYLIST_ID + " = " + PlaylistStreamEntity.Companion.JOIN_PLAYLIST_ID
            + " GROUP BY " + PlaylistEntity.Companion.PLAYLIST_ID
            + " ORDER BY " + PlaylistEntity.Companion.PLAYLIST_DISPLAY_INDEX))
    fun getPlaylistMetadata(): Flowable<List<PlaylistMetadataEntry?>?>

    @RewriteQueriesToDropUnusedColumns
    @Transaction
    @Query(("SELECT *, MIN(" + PlaylistStreamEntity.Companion.JOIN_INDEX + ")"
            + " FROM " + StreamEntity.STREAM_TABLE + " INNER JOIN"
            + " (SELECT " + PlaylistStreamEntity.Companion.JOIN_STREAM_ID + "," + PlaylistStreamEntity.Companion.JOIN_INDEX
            + " FROM " + PlaylistStreamEntity.Companion.PLAYLIST_STREAM_JOIN_TABLE
            + " WHERE " + PlaylistStreamEntity.Companion.JOIN_PLAYLIST_ID + " = :playlistId)"
            + " ON " + StreamEntity.STREAM_ID + " = " + PlaylistStreamEntity.Companion.JOIN_STREAM_ID
            + " LEFT JOIN "
            + "(SELECT " + PlaylistStreamEntity.Companion.JOIN_STREAM_ID + " AS " + StreamStateEntity.Companion.JOIN_STREAM_ID_ALIAS + ", "
            + StreamStateEntity.Companion.STREAM_PROGRESS_MILLIS
            + " FROM " + StreamStateEntity.Companion.STREAM_STATE_TABLE + " )"
            + " ON " + StreamEntity.STREAM_ID + " = " + StreamStateEntity.Companion.JOIN_STREAM_ID_ALIAS
            + " GROUP BY " + StreamEntity.STREAM_ID
            + " ORDER BY MIN(" + PlaylistStreamEntity.Companion.JOIN_INDEX + ") ASC"))
    fun getStreamsWithoutDuplicates(playlistId: Long): Flowable<List<PlaylistStreamEntry?>?>

    @Transaction
    @Query(("SELECT " + PlaylistEntity.Companion.PLAYLIST_TABLE + "." + PlaylistEntity.Companion.PLAYLIST_ID + ", " + PlaylistEntity.Companion.PLAYLIST_NAME + ", "
            + PlaylistEntity.Companion.PLAYLIST_THUMBNAIL_PERMANENT + ", " + PlaylistEntity.Companion.PLAYLIST_THUMBNAIL_STREAM_ID + ", "
            + PlaylistEntity.Companion.PLAYLIST_DISPLAY_INDEX + ", "
            + " CASE WHEN " + PlaylistEntity.Companion.PLAYLIST_THUMBNAIL_STREAM_ID + " = "
            + PlaylistEntity.Companion.DEFAULT_THUMBNAIL_ID + " THEN " + "'" + PlaylistEntity.Companion.DEFAULT_THUMBNAIL + "'"
            + " ELSE (SELECT " + StreamEntity.STREAM_THUMBNAIL_URL
            + " FROM " + StreamEntity.STREAM_TABLE
            + " WHERE " + StreamEntity.STREAM_TABLE + "." + StreamEntity.STREAM_ID + " = " + PlaylistEntity.Companion.PLAYLIST_THUMBNAIL_STREAM_ID
            + " ) END AS " + PlaylistEntity.Companion.PLAYLIST_THUMBNAIL_URL + ", "
            + "COALESCE(COUNT(" + PlaylistStreamEntity.Companion.JOIN_PLAYLIST_ID + "), 0) AS " + PlaylistMetadataEntry.Companion.PLAYLIST_STREAM_COUNT + ", "
            + "COALESCE(SUM(" + StreamEntity.STREAM_URL + " = :streamUrl), 0) AS "
            + PlaylistDuplicatesEntry.Companion.PLAYLIST_TIMES_STREAM_IS_CONTAINED
            + " FROM " + PlaylistEntity.Companion.PLAYLIST_TABLE
            + " LEFT JOIN " + PlaylistStreamEntity.Companion.PLAYLIST_STREAM_JOIN_TABLE
            + " ON " + PlaylistEntity.Companion.PLAYLIST_TABLE + "." + PlaylistEntity.Companion.PLAYLIST_ID + " = " + PlaylistStreamEntity.Companion.JOIN_PLAYLIST_ID
            + " LEFT JOIN " + StreamEntity.STREAM_TABLE
            + " ON " + StreamEntity.STREAM_TABLE + "." + StreamEntity.STREAM_ID + " = " + PlaylistStreamEntity.Companion.JOIN_STREAM_ID
            + " AND :streamUrl = :streamUrl"
            + " GROUP BY " + PlaylistStreamEntity.Companion.JOIN_PLAYLIST_ID
            + " ORDER BY " + PlaylistEntity.Companion.PLAYLIST_DISPLAY_INDEX))
    fun getPlaylistDuplicatesMetadata(streamUrl: String?): Flowable<List<PlaylistDuplicatesEntry?>?>
}
