package org.schabi.newpipe.local.playlist;

import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.playlist.dao.PlaylistRemoteDAO;
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;

import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

public class RemotePlaylistManager {

    private final PlaylistRemoteDAO playlistRemoteTable;

    public RemotePlaylistManager(final AppDatabase db) {
        playlistRemoteTable = db.playlistRemoteDAO();
    }

    public Flowable<List<PlaylistRemoteEntity>> getPlaylists() {
        return playlistRemoteTable.getAll().subscribeOn(Schedulers.io());
    }

    public Flowable<List<PlaylistRemoteEntity>> getPlaylist(final PlaylistInfo info) {
        return playlistRemoteTable.getPlaylist(info.getServiceId(), info.getUrl())
                .subscribeOn(Schedulers.io());
    }

    public Single<Integer> deletePlaylist(final long playlistId) {
        return Single.fromCallable(() -> playlistRemoteTable.deletePlaylist(playlistId))
                .subscribeOn(Schedulers.io());
    }

    public Single<Long> onBookmark(final PlaylistInfo playlistInfo) {
        return Single.fromCallable(() -> {
            final PlaylistRemoteEntity playlist = new PlaylistRemoteEntity(playlistInfo);
            return playlistRemoteTable.upsert(playlist);
        }).subscribeOn(Schedulers.io());
    }

    public Single<Integer> onUpdate(final long playlistId, final PlaylistInfo playlistInfo) {
        return Single.fromCallable(() -> {
            PlaylistRemoteEntity playlist = new PlaylistRemoteEntity(playlistInfo);
            playlist.setUid(playlistId);
            return playlistRemoteTable.update(playlist);
        }).subscribeOn(Schedulers.io());
    }
}
