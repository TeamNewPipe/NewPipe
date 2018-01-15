package org.schabi.newpipe.database.playlist.dao;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Transaction;

import org.schabi.newpipe.database.BasicDAO;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity;
import org.schabi.newpipe.database.stream.model.StreamEntity;

import java.util.List;

import io.reactivex.Flowable;

import static org.schabi.newpipe.database.playlist.PlaylistMetadataEntry.PLAYLIST_STREAM_COUNT;
import static org.schabi.newpipe.database.playlist.model.PlaylistEntity.*;
import static org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity.*;
import static org.schabi.newpipe.database.stream.model.StreamEntity.*;

@Dao
public abstract class PlaylistStreamDAO implements BasicDAO<PlaylistStreamEntity> {
    @Override
    @Query("SELECT * FROM " + PLAYLIST_STREAM_JOIN_TABLE)
    public abstract Flowable<List<PlaylistStreamEntity>> getAll();

    @Override
    @Query("DELETE FROM " + PLAYLIST_STREAM_JOIN_TABLE)
    public abstract int deleteAll();

    @Override
    public Flowable<List<PlaylistStreamEntity>> listByService(int serviceId) {
        throw new UnsupportedOperationException();
    }

    @Query("DELETE FROM " + PLAYLIST_STREAM_JOIN_TABLE +
            " WHERE " + JOIN_PLAYLIST_ID + " = :playlistId")
    public abstract void deleteBatch(final long playlistId);

    @Query("SELECT MAX(" + JOIN_INDEX + ")" +
            " FROM " + PLAYLIST_STREAM_JOIN_TABLE +
            " WHERE " + JOIN_PLAYLIST_ID + " = :playlistId")
    public abstract Flowable<Integer> getMaximumIndexOf(final long playlistId);

    @Transaction
    @Query("SELECT " + STREAM_ID + ", " + STREAM_SERVICE_ID + ", " + STREAM_URL + ", " +
            STREAM_TITLE + ", " + STREAM_TYPE + ", " + STREAM_UPLOADER + ", " +
            STREAM_DURATION + ", " + STREAM_THUMBNAIL_URL +

            " FROM " + STREAM_TABLE + " INNER JOIN " +
            // get ids of streams of the given playlist
            "(SELECT " + JOIN_STREAM_ID + "," + JOIN_INDEX +
            " FROM " + PLAYLIST_STREAM_JOIN_TABLE + " WHERE "
            + JOIN_PLAYLIST_ID + " = :playlistId)" +

            // then merge with the stream metadata
            " ON " + STREAM_ID + " = " + JOIN_STREAM_ID +
            " ORDER BY " + JOIN_INDEX + " ASC")
    public abstract Flowable<List<StreamEntity>> getOrderedStreamsOf(long playlistId);

    @Transaction
    @Query("SELECT " + PLAYLIST_ID + ", " + PLAYLIST_NAME + ", " +
            PLAYLIST_THUMBNAIL_URL + ", COUNT(*) AS " + PLAYLIST_STREAM_COUNT +

            " FROM " + PLAYLIST_TABLE + " LEFT JOIN " + PLAYLIST_STREAM_JOIN_TABLE +
            " ON " + PLAYLIST_TABLE + "." + PLAYLIST_ID + " = " + PLAYLIST_STREAM_JOIN_TABLE + "." + JOIN_PLAYLIST_ID +
            " GROUP BY " + JOIN_PLAYLIST_ID)
    public abstract Flowable<List<PlaylistMetadataEntry>> getPlaylistMetadata();
}
