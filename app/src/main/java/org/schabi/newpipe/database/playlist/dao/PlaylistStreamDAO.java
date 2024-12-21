package org.schabi.newpipe.database.playlist.dao;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.RewriteQueriesToDropUnusedColumns;
import androidx.room.Transaction;

import org.schabi.newpipe.database.BasicDAO;
import org.schabi.newpipe.database.playlist.PlaylistDuplicatesEntry;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry;
import org.schabi.newpipe.database.playlist.model.PlaylistEntity;
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity;

import java.util.List;

import io.reactivex.rxjava3.core.Flowable;

import static org.schabi.newpipe.database.playlist.PlaylistDuplicatesEntry.PLAYLIST_TIMES_STREAM_IS_CONTAINED;
import static org.schabi.newpipe.database.playlist.PlaylistMetadataEntry.PLAYLIST_STREAM_COUNT;
import static org.schabi.newpipe.database.playlist.model.PlaylistEntity.PLAYLIST_DISPLAY_INDEX;
import static org.schabi.newpipe.database.playlist.model.PlaylistEntity.DEFAULT_THUMBNAIL;
import static org.schabi.newpipe.database.playlist.model.PlaylistEntity.PLAYLIST_ID;
import static org.schabi.newpipe.database.playlist.model.PlaylistEntity.PLAYLIST_NAME;
import static org.schabi.newpipe.database.playlist.model.PlaylistEntity.PLAYLIST_TABLE;
import static org.schabi.newpipe.database.playlist.model.PlaylistEntity.PLAYLIST_THUMBNAIL_PERMANENT;
import static org.schabi.newpipe.database.playlist.model.PlaylistEntity.PLAYLIST_THUMBNAIL_STREAM_ID;
import static org.schabi.newpipe.database.playlist.model.PlaylistEntity.PLAYLIST_THUMBNAIL_URL;
import static org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity.JOIN_INDEX;
import static org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity.JOIN_PLAYLIST_ID;
import static org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity.JOIN_STREAM_ID;
import static org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity.PLAYLIST_STREAM_JOIN_TABLE;
import static org.schabi.newpipe.database.stream.model.StreamEntity.STREAM_ID;
import static org.schabi.newpipe.database.stream.model.StreamEntity.STREAM_TABLE;
import static org.schabi.newpipe.database.stream.model.StreamEntity.STREAM_THUMBNAIL_URL;
import static org.schabi.newpipe.database.stream.model.StreamEntity.STREAM_URL;
import static org.schabi.newpipe.database.stream.model.StreamStateEntity.JOIN_STREAM_ID_ALIAS;
import static org.schabi.newpipe.database.stream.model.StreamStateEntity.STREAM_PROGRESS_MILLIS;
import static org.schabi.newpipe.database.stream.model.StreamStateEntity.STREAM_STATE_TABLE;

@Dao
public interface PlaylistStreamDAO extends BasicDAO<PlaylistStreamEntity> {
    @Override
    @Query("SELECT * FROM " + PLAYLIST_STREAM_JOIN_TABLE)
    Flowable<List<PlaylistStreamEntity>> getAll();

    @Override
    @Query("DELETE FROM " + PLAYLIST_STREAM_JOIN_TABLE)
    int deleteAll();

    @Override
    default Flowable<List<PlaylistStreamEntity>> listByService(final int serviceId) {
        throw new UnsupportedOperationException();
    }

    @Query("DELETE FROM " + PLAYLIST_STREAM_JOIN_TABLE
            + " WHERE " + JOIN_PLAYLIST_ID + " = :playlistId")
    void deleteBatch(long playlistId);

    @Query("SELECT COALESCE(MAX(" + JOIN_INDEX + "), -1)"
            + " FROM " + PLAYLIST_STREAM_JOIN_TABLE
            + " WHERE " + JOIN_PLAYLIST_ID + " = :playlistId")
    Flowable<Integer> getMaximumIndexOf(long playlistId);

    @Query("SELECT CASE WHEN COUNT(*) != 0 then " + STREAM_ID
            + " ELSE " + PlaylistEntity.DEFAULT_THUMBNAIL_ID + " END"
            + " FROM " + STREAM_TABLE
            + " LEFT JOIN " + PLAYLIST_STREAM_JOIN_TABLE
            + " ON " + STREAM_ID + " = " + JOIN_STREAM_ID
            + " WHERE " + JOIN_PLAYLIST_ID + " = :playlistId "
            + " LIMIT 1"
    )
    Flowable<Long> getAutomaticThumbnailStreamId(long playlistId);

    @RewriteQueriesToDropUnusedColumns
    @Transaction
    @Query("SELECT * FROM " + STREAM_TABLE + " INNER JOIN "
            // get ids of streams of the given playlist
            + "(SELECT " + JOIN_STREAM_ID + "," + JOIN_INDEX
            + " FROM " + PLAYLIST_STREAM_JOIN_TABLE
            + " WHERE " + JOIN_PLAYLIST_ID + " = :playlistId)"

            // then merge with the stream metadata
            + " ON " + STREAM_ID + " = " + JOIN_STREAM_ID

            + " LEFT JOIN "
            + "(SELECT " + JOIN_STREAM_ID + " AS " + JOIN_STREAM_ID_ALIAS + ", "
            + STREAM_PROGRESS_MILLIS
            + " FROM " + STREAM_STATE_TABLE + " )"
            + " ON " + STREAM_ID + " = " + JOIN_STREAM_ID_ALIAS

            + " ORDER BY " + JOIN_INDEX + " ASC")
    Flowable<List<PlaylistStreamEntry>> getOrderedStreamsOf(long playlistId);

    @Transaction
    @Query("SELECT " + PLAYLIST_ID + ", " + PLAYLIST_NAME + ", "
            + PLAYLIST_THUMBNAIL_PERMANENT + ", " + PLAYLIST_THUMBNAIL_STREAM_ID + ", "
            + PLAYLIST_DISPLAY_INDEX + ", "

            + " CASE WHEN " + PLAYLIST_THUMBNAIL_STREAM_ID + " = "
            + PlaylistEntity.DEFAULT_THUMBNAIL_ID + " THEN " + "'" + DEFAULT_THUMBNAIL + "'"
            + " ELSE (SELECT " + STREAM_THUMBNAIL_URL
            + " FROM " + STREAM_TABLE
            + " WHERE " + STREAM_TABLE + "." + STREAM_ID + " = " + PLAYLIST_THUMBNAIL_STREAM_ID
            + " ) END AS " + PLAYLIST_THUMBNAIL_URL + ", "

            + "COALESCE(COUNT(" + JOIN_PLAYLIST_ID + "), 0) AS " + PLAYLIST_STREAM_COUNT
            + " FROM " + PLAYLIST_TABLE
            + " LEFT JOIN " + PLAYLIST_STREAM_JOIN_TABLE
            + " ON " + PLAYLIST_TABLE + "." + PLAYLIST_ID + " = " + JOIN_PLAYLIST_ID
            + " GROUP BY " + PLAYLIST_ID
            + " ORDER BY " + PLAYLIST_DISPLAY_INDEX)
    Flowable<List<PlaylistMetadataEntry>> getPlaylistMetadata();

    @RewriteQueriesToDropUnusedColumns
    @Transaction
    @Query("SELECT *, MIN(" + JOIN_INDEX + ")"
            + " FROM " + STREAM_TABLE + " INNER JOIN"
            + " (SELECT " + JOIN_STREAM_ID + "," + JOIN_INDEX
            + " FROM " + PLAYLIST_STREAM_JOIN_TABLE
            + " WHERE " + JOIN_PLAYLIST_ID + " = :playlistId)"
            + " ON " + STREAM_ID + " = " + JOIN_STREAM_ID
            + " LEFT JOIN "
            + "(SELECT " + JOIN_STREAM_ID + " AS " + JOIN_STREAM_ID_ALIAS + ", "
            + STREAM_PROGRESS_MILLIS
            + " FROM " + STREAM_STATE_TABLE + " )"
            + " ON " + STREAM_ID + " = " + JOIN_STREAM_ID_ALIAS
            + " GROUP BY " + STREAM_ID
            + " ORDER BY MIN(" + JOIN_INDEX + ") ASC")
    Flowable<List<PlaylistStreamEntry>> getStreamsWithoutDuplicates(long playlistId);

    @Transaction
    @Query("SELECT " + PLAYLIST_TABLE + "." + PLAYLIST_ID + ", " + PLAYLIST_NAME + ", "
            + PLAYLIST_THUMBNAIL_PERMANENT + ", " + PLAYLIST_THUMBNAIL_STREAM_ID + ", "
            + PLAYLIST_DISPLAY_INDEX + ", "

            + " CASE WHEN " + PLAYLIST_THUMBNAIL_STREAM_ID + " = "
            + PlaylistEntity.DEFAULT_THUMBNAIL_ID + " THEN " + "'" + DEFAULT_THUMBNAIL + "'"
            + " ELSE (SELECT " + STREAM_THUMBNAIL_URL
            + " FROM " + STREAM_TABLE
            + " WHERE " + STREAM_TABLE + "." + STREAM_ID + " = " + PLAYLIST_THUMBNAIL_STREAM_ID
            + " ) END AS " + PLAYLIST_THUMBNAIL_URL + ", "

            + "COALESCE(COUNT(" + JOIN_PLAYLIST_ID + "), 0) AS " + PLAYLIST_STREAM_COUNT + ", "
            + "COALESCE(SUM(" + STREAM_URL + " = :streamUrl), 0) AS "
                + PLAYLIST_TIMES_STREAM_IS_CONTAINED

            + " FROM " + PLAYLIST_TABLE
            + " LEFT JOIN " + PLAYLIST_STREAM_JOIN_TABLE
            + " ON " + PLAYLIST_TABLE + "." + PLAYLIST_ID + " = " + JOIN_PLAYLIST_ID

            + " LEFT JOIN " + STREAM_TABLE
            + " ON " + STREAM_TABLE + "." + STREAM_ID + " = " + JOIN_STREAM_ID
            + " AND :streamUrl = :streamUrl"

            + " GROUP BY " + JOIN_PLAYLIST_ID
            + " ORDER BY " + PLAYLIST_DISPLAY_INDEX + ", " + PLAYLIST_NAME)
    Flowable<List<PlaylistDuplicatesEntry>> getPlaylistDuplicatesMetadata(String streamUrl);
}
