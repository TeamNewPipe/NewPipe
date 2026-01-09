package org.schabi.newpipe.local.playlist;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.playlist.model.PlaylistFolderEntity;

public class PlaylistFolderManager {
    private final AppDatabase database;

    public PlaylistFolderManager(final AppDatabase db) {
        this.database = db;
    }

    public Flowable<java.util.List<PlaylistFolderEntity>> getFolders() {
        return database.playlistFolderDAO().getAll();
    }

    public Maybe<Long> createFolder(final String name) {
        return Maybe.fromCallable(() -> database.runInTransaction(() -> {
            final PlaylistFolderEntity entity = new PlaylistFolderEntity(0, name, 0);
            return database.playlistFolderDAO().insert(entity);
        }));
    }

    public Completable updateFolder(final PlaylistFolderEntity folder) {
        return Completable.fromAction(() -> database.playlistFolderDAO().update(folder));
    }

    public Completable deleteFolder(final long folderId) {
        return Completable.fromAction(() -> database.playlistFolderDAO().delete(folderId));
    }
}
