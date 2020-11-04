package org.schabi.newpipe.database.playlist.dao;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Transaction;

import org.schabi.newpipe.database.BasicDAO;
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;

import java.util.List;

import io.reactivex.Flowable;

import static org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity.REMOTE_PLAYLIST_ID;
import static org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity.REMOTE_PLAYLIST_SERVICE_ID;
import static org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity.REMOTE_PLAYLIST_TABLE;
import static org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity.REMOTE_PLAYLIST_URL;

@Dao
public abstract class PlaylistRemoteDAO implements BasicDAO<PlaylistRemoteEntity> {
    @Override
    @Query("SELECT * FROM " + REMOTE_PLAYLIST_TABLE)
    public abstract Flowable<List<PlaylistRemoteEntity>> getAll();

    @Override
    @Query("DELETE FROM " + REMOTE_PLAYLIST_TABLE)
    public abstract int deleteAll();

    @Override
    @Query("SELECT * FROM " + REMOTE_PLAYLIST_TABLE
            + " WHERE " + REMOTE_PLAYLIST_SERVICE_ID + " = :serviceId")
    public abstract Flowable<List<PlaylistRemoteEntity>> listByService(int serviceId);

    @Query("SELECT * FROM " + REMOTE_PLAYLIST_TABLE + " WHERE "
            + REMOTE_PLAYLIST_URL + " = :url AND " + REMOTE_PLAYLIST_SERVICE_ID + " = :serviceId")
    public abstract Flowable<List<PlaylistRemoteEntity>> getPlaylist(long serviceId, String url);

    @Query("SELECT " + REMOTE_PLAYLIST_ID + " FROM " + REMOTE_PLAYLIST_TABLE
            + " WHERE " + REMOTE_PLAYLIST_URL + " = :url "
            + "AND " + REMOTE_PLAYLIST_SERVICE_ID + " = :serviceId")
    abstract Long getPlaylistIdInternal(long serviceId, String url);

    @Transaction
    public long upsert(final PlaylistRemoteEntity playlist) {
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
    public abstract int deletePlaylist(long playlistId);
}
