package org.schabi.newpipe.fragments.playlist;

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
import io.reactivex.Maybe;
import io.reactivex.Scheduler;
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
        // Disallow creation of empty playlists until user is able to select thumbnail
        if (streams.isEmpty()) return Maybe.empty();
        final StreamEntity defaultStream = streams.get(0);
        final PlaylistEntity newPlaylist = new PlaylistEntity(name, defaultStream.getThumbnailUrl());

        return Maybe.fromCallable(() -> database.runInTransaction(() -> {
            final long playlistId = playlistTable.insert(newPlaylist);

            List<PlaylistStreamEntity> joinEntities = new ArrayList<>(streams.size());
            for (int index = 0; index < streams.size(); index++) {
                // Upsert streams and get their ids
                final long streamId = streamTable.upsert(streams.get(index));
                joinEntities.add(new PlaylistStreamEntity(playlistId, streamId, index));
            }

            return playlistStreamTable.insertAll(joinEntities);
        })).subscribeOn(Schedulers.io());
    }

    public Maybe<Long> appendToPlaylist(final long playlistId, final StreamEntity stream) {
        final Maybe<Long> streamIdFuture = Maybe.fromCallable(() -> streamTable.upsert(stream));
        final Maybe<Integer> joinIndexFuture =
                playlistStreamTable.getMaximumIndexOf(playlistId).firstElement();

        return Maybe.zip(streamIdFuture, joinIndexFuture, (streamId, currentMaxJoinIndex) ->
                playlistStreamTable.insert(new PlaylistStreamEntity(playlistId,
                        streamId, currentMaxJoinIndex + 1))
        ).subscribeOn(Schedulers.io());
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

    public Maybe<List<PlaylistMetadataEntry>> getPlaylists() {
        return playlistStreamTable.getPlaylistMetadata()
                .firstElement()
                .subscribeOn(Schedulers.io());
    }
}
