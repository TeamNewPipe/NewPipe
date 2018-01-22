package org.schabi.newpipe.fragments.local;

import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.database.playlist.dao.PlaylistDAO;
import org.schabi.newpipe.database.playlist.dao.PlaylistStreamDAO;
import org.schabi.newpipe.database.playlist.model.PlaylistEntity;
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity;
import org.schabi.newpipe.database.stream.dao.StreamDAO;
import org.schabi.newpipe.database.stream.model.StreamEntity;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

public class LocalPlaylistManager {

    private final AppDatabase database;
    private final StreamDAO streamTable;
    private final PlaylistDAO playlistTable;
    private final PlaylistStreamDAO playlistStreamTable;

    public LocalPlaylistManager(final AppDatabase db) {
        database = db;
        streamTable = db.streamDAO();
        playlistTable = db.playlistDAO();
        playlistStreamTable = db.playlistStreamDAO();
    }

    public Maybe<List<Long>> createPlaylist(final String name, final List<StreamEntity> streams) {
        // Disallow creation of empty playlists
        if (streams.isEmpty()) return Maybe.empty();
        final StreamEntity defaultStream = streams.get(0);
        final PlaylistEntity newPlaylist =
                new PlaylistEntity(name, defaultStream.getThumbnailUrl());

        return Maybe.fromCallable(() -> database.runInTransaction(() ->
                upsertStreams(playlistTable.insert(newPlaylist), streams, 0))
        ).subscribeOn(Schedulers.io());
    }

    public Maybe<List<Long>> appendToPlaylist(final long playlistId,
                                              final List<StreamEntity> streams) {
        return playlistStreamTable.getMaximumIndexOf(playlistId)
                .firstElement()
                .map(maxJoinIndex -> database.runInTransaction(() ->
                        upsertStreams(playlistId, streams, maxJoinIndex + 1))
                ).subscribeOn(Schedulers.io());
    }

    private List<Long> upsertStreams(final long playlistId,
                                     final List<StreamEntity> streams,
                                     final int indexOffset) {

        List<PlaylistStreamEntity> joinEntities = new ArrayList<>(streams.size());
        for (int index = 0; index < streams.size(); index++) {
            // Upsert streams and get their ids
            final long streamId = streamTable.upsert(streams.get(index));
            joinEntities.add(new PlaylistStreamEntity(playlistId, streamId,
                    index + indexOffset));
        }
        return playlistStreamTable.insertAll(joinEntities);
    }

    public Completable updateJoin(final long playlistId, final List<Long> streamIds) {
        List<PlaylistStreamEntity> joinEntities = new ArrayList<>(streamIds.size());
        for (int i = 0; i < streamIds.size(); i++) {
            joinEntities.add(new PlaylistStreamEntity(playlistId, streamIds.get(i), i));
        }

        return Completable.fromRunnable(() -> database.runInTransaction(() -> {
            playlistStreamTable.deleteBatch(playlistId);
            playlistStreamTable.insertAll(joinEntities);
        }));
    }

    public Flowable<List<PlaylistMetadataEntry>> getPlaylists() {
        return playlistStreamTable.getPlaylistMetadata().subscribeOn(Schedulers.io());
    }

    public Flowable<List<StreamEntity>> getPlaylist(final long playlistId) {
        return playlistStreamTable.getOrderedStreamsOf(playlistId).subscribeOn(Schedulers.io());
    }

    public Single<Integer> deletePlaylist(final long playlistId) {
        return Single.fromCallable(() -> playlistTable.deletePlaylist(playlistId))
                .subscribeOn(Schedulers.io());
    }
}
