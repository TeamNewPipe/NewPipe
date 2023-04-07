package org.schabi.newpipe.local.playlist;

import androidx.annotation.Nullable;

import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.playlist.PlaylistDuplicatesEntry;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry;
import org.schabi.newpipe.database.playlist.dao.PlaylistDAO;
import org.schabi.newpipe.database.playlist.dao.PlaylistStreamDAO;
import org.schabi.newpipe.database.playlist.model.PlaylistEntity;
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity;
import org.schabi.newpipe.database.stream.dao.StreamDAO;
import org.schabi.newpipe.database.stream.model.StreamEntity;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class LocalPlaylistManager {
    private static final long THUMBNAIL_ID_LEAVE_UNCHANGED = -2;

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
        if (streams.isEmpty()) {
            return Maybe.empty();
        }

        return Maybe.fromCallable(() -> database.runInTransaction(() -> {
                    final List<Long> streamIds = streamTable.upsertAll(streams);
                    final PlaylistEntity newPlaylist = new PlaylistEntity(name, false,
                            streamIds.get(0));

                    return insertJoinEntities(playlistTable.insert(newPlaylist),
                            streamIds, 0);
                }
        )).subscribeOn(Schedulers.io());
    }

    public Maybe<List<Long>> appendToPlaylist(final long playlistId,
                                              final List<StreamEntity> streams) {
        return playlistStreamTable.getMaximumIndexOf(playlistId)
                .firstElement()
                .map(maxJoinIndex -> database.runInTransaction(() -> {
                            final List<Long> streamIds = streamTable.upsertAll(streams);
                            return insertJoinEntities(playlistId, streamIds, maxJoinIndex + 1);
                        }
                )).subscribeOn(Schedulers.io());
    }

    private List<Long> insertJoinEntities(final long playlistId, final List<Long> streamIds,
                                          final int indexOffset) {

        final List<PlaylistStreamEntity> joinEntities = new ArrayList<>(streamIds.size());

        for (int index = 0; index < streamIds.size(); index++) {
            joinEntities.add(new PlaylistStreamEntity(playlistId, streamIds.get(index),
                    index + indexOffset));
        }
        return playlistStreamTable.insertAll(joinEntities);
    }

    public Completable updateJoin(final long playlistId, final List<Long> streamIds) {
        final List<PlaylistStreamEntity> joinEntities = new ArrayList<>(streamIds.size());
        for (int i = 0; i < streamIds.size(); i++) {
            joinEntities.add(new PlaylistStreamEntity(playlistId, streamIds.get(i), i));
        }

        return Completable.fromRunnable(() -> database.runInTransaction(() -> {
            playlistStreamTable.deleteBatch(playlistId);
            playlistStreamTable.insertAll(joinEntities);
        })).subscribeOn(Schedulers.io());
    }

    public Flowable<List<PlaylistMetadataEntry>> getPlaylists() {
        return playlistStreamTable.getPlaylistMetadata().subscribeOn(Schedulers.io());
    }

    public Flowable<List<PlaylistStreamEntry>> getDistinctPlaylistStreams(final long playlistId) {
        return playlistStreamTable
                .getStreamsWithoutDuplicates(playlistId).subscribeOn(Schedulers.io());
    }

    /**
     * Get playlists with attached information about how many times the provided stream is already
     * contained in each playlist.
     *
     * @param streamUrl the stream url for which to check for duplicates
     * @return a list of {@link PlaylistDuplicatesEntry}
     */
    public Flowable<List<PlaylistDuplicatesEntry>> getPlaylistDuplicates(final String streamUrl) {
        return playlistStreamTable.getPlaylistDuplicatesMetadata(streamUrl)
                .subscribeOn(Schedulers.io());
    }

    public Flowable<List<PlaylistStreamEntry>> getPlaylistStreams(final long playlistId) {
        return playlistStreamTable.getOrderedStreamsOf(playlistId).subscribeOn(Schedulers.io());
    }

    public Single<Integer> deletePlaylist(final long playlistId) {
        return Single.fromCallable(() -> playlistTable.deletePlaylist(playlistId))
                .subscribeOn(Schedulers.io());
    }

    public Maybe<Integer> renamePlaylist(final long playlistId, final String name) {
        return modifyPlaylist(playlistId, name, THUMBNAIL_ID_LEAVE_UNCHANGED, false);
    }

    public Maybe<Integer> changePlaylistThumbnail(final long playlistId,
                                                  final long thumbnailStreamId,
                                                  final boolean isPermanent) {
        return modifyPlaylist(playlistId, null, thumbnailStreamId, isPermanent);
    }

    public long getPlaylistThumbnailStreamId(final long playlistId) {
        return playlistTable.getPlaylist(playlistId).blockingFirst().get(0).getThumbnailStreamId();
    }

    public boolean getIsPlaylistThumbnailPermanent(final long playlistId) {
        return playlistTable.getPlaylist(playlistId).blockingFirst().get(0)
                .getIsThumbnailPermanent();
    }

    public long getAutomaticPlaylistThumbnailStreamId(final long playlistId) {
        final long streamId = playlistStreamTable.getAutomaticThumbnailStreamId(playlistId)
                .blockingFirst();
        if (streamId < 0) {
            return PlaylistEntity.DEFAULT_THUMBNAIL_ID;
        }
        return streamId;
    }

    private Maybe<Integer> modifyPlaylist(final long playlistId,
                                          @Nullable final String name,
                                          final long thumbnailStreamId,
                                          final boolean isPermanent) {
        return playlistTable.getPlaylist(playlistId)
                .firstElement()
                .filter(playlistEntities -> !playlistEntities.isEmpty())
                .map(playlistEntities -> {
                    final PlaylistEntity playlist = playlistEntities.get(0);
                    if (name != null) {
                        playlist.setName(name);
                    }
                    if (thumbnailStreamId != THUMBNAIL_ID_LEAVE_UNCHANGED) {
                        playlist.setThumbnailStreamId(thumbnailStreamId);
                        playlist.setIsThumbnailPermanent(isPermanent);
                    }
                    return playlistTable.update(playlist);
                }).subscribeOn(Schedulers.io());
    }

    public Maybe<Boolean> hasPlaylists() {
        return playlistTable.getCount()
                .firstElement()
                .map(count -> count > 0)
                .subscribeOn(Schedulers.io());
    }
}
