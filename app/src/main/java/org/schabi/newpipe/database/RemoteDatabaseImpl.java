package org.schabi.newpipe.database;

import android.content.Context;

import org.schabi.newpipe.database.history.dao.RemoteSearchHistoryDAO;
import org.schabi.newpipe.database.history.dao.RemoteStreamHistoryDAO;
import org.schabi.newpipe.database.history.dao.SearchHistoryDAO;
import org.schabi.newpipe.database.history.dao.StreamHistoryDAO;
import org.schabi.newpipe.database.history.model.SearchHistoryEntry;
import org.schabi.newpipe.database.history.model.StreamHistoryEntity;
import org.schabi.newpipe.database.playlist.dao.PlaylistDAO;
import org.schabi.newpipe.database.playlist.dao.PlaylistRemoteDAO;
import org.schabi.newpipe.database.playlist.dao.PlaylistStreamDAO;
import org.schabi.newpipe.database.playlist.dao.RemotePlaylistDAO;
import org.schabi.newpipe.database.playlist.dao.RemotePlaylistRemoteDAO;
import org.schabi.newpipe.database.playlist.dao.RemotePlaylistStreamDAO;
import org.schabi.newpipe.database.playlist.model.PlaylistEntity;
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity;
import org.schabi.newpipe.database.stream.dao.RemoteStreamDAO;
import org.schabi.newpipe.database.stream.dao.RemoteStreamStateDAO;
import org.schabi.newpipe.database.stream.dao.StreamDAO;
import org.schabi.newpipe.database.stream.dao.StreamStateDAO;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.database.stream.model.StreamStateEntity;
import org.schabi.newpipe.database.subscription.RemoteSubscriptionDAO;
import org.schabi.newpipe.database.subscription.SubscriptionDAO;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;

import java.util.List;

import io.reactivex.Completable;
import io.reactivex.schedulers.Schedulers;

public class RemoteDatabaseImpl extends RemoteDatabase {

    private final RemoteSubscriptionDAO subscriptionDAO;
    private final RemoteSearchHistoryDAO searchHistoryDAO;
    private final RemoteStreamDAO streamDAO;
    private final RemoteStreamHistoryDAO streamHistoryDAO;
    private final RemoteStreamStateDAO streamStateDAO;
    private final RemotePlaylistDAO playlistDAO;
    private final RemotePlaylistStreamDAO playlistStreamDAO;
    private final RemotePlaylistRemoteDAO playlistRemoteDAO;

    private final AppDatabase roomDb;

    public RemoteDatabaseImpl(AppDatabase roomDb, Context context) {
        super();
        this.roomDb = roomDb;
        subscriptionDAO = new RemoteSubscriptionDAO(roomDb, context);
        searchHistoryDAO = new RemoteSearchHistoryDAO(roomDb, context);
        streamDAO = new RemoteStreamDAO(roomDb, context);
        streamHistoryDAO = new RemoteStreamHistoryDAO(roomDb, context);
        streamStateDAO = new RemoteStreamStateDAO(roomDb, context);
        playlistStreamDAO = new RemotePlaylistStreamDAO(roomDb, context);
        playlistDAO = new RemotePlaylistDAO(roomDb, context);
        playlistRemoteDAO = new RemotePlaylistRemoteDAO(roomDb,context);
    }


    @Override
    public SubscriptionDAO subscriptionDAO() {
        return subscriptionDAO;
    }

    @Override
    public SearchHistoryDAO searchHistoryDAO() {
        return searchHistoryDAO;
    }

    @Override
    public StreamDAO streamDAO() {
        return streamDAO;
    }

    @Override
    public StreamHistoryDAO streamHistoryDAO() {
        return streamHistoryDAO;
    }

    @Override
    public StreamStateDAO streamStateDAO() {
        return streamStateDAO;
    }

    @Override
    public PlaylistDAO playlistDAO() {
        return playlistDAO;
    }

    @Override
    public PlaylistStreamDAO playlistStreamDAO() {
        return playlistStreamDAO;
    }

    @Override
    public PlaylistRemoteDAO playlistRemoteDAO() {
        return playlistRemoteDAO;
    }

    @Override
    public Completable sync() {

        return Completable.fromAction(() -> {
            //TODO make these calls parallel
            List<StreamEntity> streamEntities = streamDAO.fetchAll();
            List<StreamHistoryEntity> streamHistoryEntities = streamHistoryDAO.fetchAll();
            List<StreamStateEntity> streamStateEntities = streamStateDAO.fetchAll();
            List<PlaylistEntity> playlistEntities = playlistDAO.fetchAll();
            List<PlaylistStreamEntity> playlistStreamEntities = playlistStreamDAO.fetchAll();
            List<PlaylistRemoteEntity> playlistRemoteEntities = playlistRemoteDAO.fetchAll();
            List<SearchHistoryEntry> searchHistoryEntries = searchHistoryDAO.fetchAll();
            List<SubscriptionEntity> subscriptionEntities = subscriptionDAO.fetchAll();

            roomDb.runInTransaction(() -> {
                roomDb.streamDAO().destroyAndRefill(streamEntities);
                roomDb.streamHistoryDAO().destroyAndRefill(streamHistoryEntities);
                roomDb.streamStateDAO().destroyAndRefill(streamStateEntities);
                roomDb.playlistDAO().destroyAndRefill(playlistEntities);
                roomDb.playlistStreamDAO().destroyAndRefill(playlistStreamEntities);
                roomDb.playlistRemoteDAO().destroyAndRefill(playlistRemoteEntities);
                roomDb.searchHistoryDAO().destroyAndRefill(searchHistoryEntries);
                roomDb.subscriptionDAO().destroyAndRefill(subscriptionEntities);
            });
        }).subscribeOn(Schedulers.io());
    }

    @Override
    public Completable refreshSubscriptions() {
        return Completable.fromAction(() -> {
            List<SubscriptionEntity> subscriptionEntities = subscriptionDAO.fetchAll();
            roomDb.runInTransaction(() -> roomDb.subscriptionDAO().destroyAndRefill(subscriptionEntities));
        }).subscribeOn(Schedulers.io());
    }

    @Override
    public Completable refreshPlaylists() {
        return Completable.fromAction(() -> {
            //TODO make these calls parallel
            List<StreamEntity> streamEntities = streamDAO.fetchAll();
            List<PlaylistEntity> playlistEntities = playlistDAO.fetchAll();
            List<PlaylistStreamEntity> playlistStreamEntities = playlistStreamDAO.fetchAll();
            List<PlaylistRemoteEntity> playlistRemoteEntities = playlistRemoteDAO.fetchAll();
            roomDb.runInTransaction(() -> {
                roomDb.streamDAO().upsertAll(streamEntities);
                roomDb.playlistDAO().destroyAndRefill(playlistEntities);
                roomDb.playlistStreamDAO().destroyAndRefill(playlistStreamEntities);
                roomDb.playlistRemoteDAO().destroyAndRefill(playlistRemoteEntities);
            });
        }).subscribeOn(Schedulers.io());
    }

    @Override
    public Completable refreshHistory() {
        return Completable.fromAction(() -> {
            //TODO make these calls parallel
            List<StreamEntity> streamEntities = streamDAO.fetchAll();
            List<StreamHistoryEntity> streamHistoryEntities = streamHistoryDAO.fetchAll();
            List<StreamStateEntity> streamStateEntities = streamStateDAO.fetchAll();
            List<SearchHistoryEntry> searchHistoryEntries = searchHistoryDAO.fetchAll();

            roomDb.runInTransaction(() -> {
                roomDb.streamDAO().destroyAndRefill(streamEntities);
                roomDb.streamHistoryDAO().destroyAndRefill(streamHistoryEntities);
                roomDb.streamStateDAO().destroyAndRefill(streamStateEntities);
                roomDb.searchHistoryDAO().destroyAndRefill(searchHistoryEntries);
            });
        }).subscribeOn(Schedulers.io());
    }
}
