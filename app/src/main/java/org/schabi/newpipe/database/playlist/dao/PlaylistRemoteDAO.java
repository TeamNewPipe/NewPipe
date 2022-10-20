package org.schabi.newpipe.database.playlist.dao;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.RewriteQueriesToDropUnusedColumns;
import androidx.room.Transaction;

import org.schabi.newpipe.database.BasicDAO;
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;
import org.schabi.newpipe.database.stream.model.StreamEntity;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.core.Flowable;

import static org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity.REMOTE_PLAYLIST_ID;
import static org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity.REMOTE_PLAYLIST_SERVICE_ID;
import static org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity.REMOTE_PLAYLIST_TABLE;
import static org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity.REMOTE_PLAYLIST_URL;
import static org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity.JOIN_INDEX;
import static org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity.JOIN_PLAYLIST_ID;
import static org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity.JOIN_STREAM_ID;
import static org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity.PLAYLIST_STREAM_JOIN_TABLE;
import static org.schabi.newpipe.database.stream.model.StreamEntity.STREAM_ID;
import static org.schabi.newpipe.database.stream.model.StreamEntity.STREAM_TABLE;
import static org.schabi.newpipe.database.stream.model.StreamStateEntity.JOIN_STREAM_ID_ALIAS;
import static org.schabi.newpipe.database.stream.model.StreamStateEntity.STREAM_PROGRESS_MILLIS;
import static org.schabi.newpipe.database.stream.model.StreamStateEntity.STREAM_STATE_TABLE;

@Dao
public interface PlaylistRemoteDAO extends BasicDAO<PlaylistRemoteEntity> {
    @Override
    @Query("SELECT * FROM " + REMOTE_PLAYLIST_TABLE)
    Flowable<List<PlaylistRemoteEntity>> getAll();

    @Override
    @Query("DELETE FROM " + REMOTE_PLAYLIST_TABLE)
    int deleteAll();

    @Override
    @Query("SELECT * FROM " + REMOTE_PLAYLIST_TABLE
            + " WHERE " + REMOTE_PLAYLIST_SERVICE_ID + " = :serviceId")
    Flowable<List<PlaylistRemoteEntity>> listByService(int serviceId);

    @Query("SELECT * FROM " + REMOTE_PLAYLIST_TABLE + " WHERE "
            + REMOTE_PLAYLIST_URL + " = :url AND " + REMOTE_PLAYLIST_SERVICE_ID + " = :serviceId")
    Flowable<List<PlaylistRemoteEntity>> getPlaylist(long serviceId, String url);

    @Query("SELECT " + REMOTE_PLAYLIST_ID + " FROM " + REMOTE_PLAYLIST_TABLE
            + " WHERE " + REMOTE_PLAYLIST_URL + " = :url "
            + "AND " + REMOTE_PLAYLIST_SERVICE_ID + " = :serviceId")
    Long getPlaylistIdInternal(long serviceId, String url);

    @Transaction
    default long upsert(final PlaylistRemoteEntity playlist) {
        final Long playlistId = getPlaylistIdInternal(playlist.getServiceId(), playlist.getUrl());

        if (playlistId == null) {
            return insert(playlist);
        } else {
            playlist.setUid(playlistId);
            update(playlist);
            return playlistId;
        }
    }

    @Query("DELETE FROM " + REMOTE_PLAYLIST_TABLE
            + " WHERE " + REMOTE_PLAYLIST_ID + " = :playlistId")
    int deletePlaylist(long playlistId);

    @Query("DELETE FROM " + REMOTE_PLAYLIST_TABLE + " WHERE " + REMOTE_PLAYLIST_ID
            + " IN(:playlistIds)")
    int deleteMultiplePlaylists(ArrayList<Long> playlistIds);

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
    Flowable<List<StreamEntity>> getOrderedStreamsOfEntity(long playlistId);
}
