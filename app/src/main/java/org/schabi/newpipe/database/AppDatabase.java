package org.schabi.newpipe.database;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import org.schabi.newpipe.database.feed.dao.FeedDAO;
import org.schabi.newpipe.database.feed.dao.FeedGroupDAO;
import org.schabi.newpipe.database.feed.model.FeedEntity;
import org.schabi.newpipe.database.feed.model.FeedGroupEntity;
import org.schabi.newpipe.database.feed.model.FeedGroupSubscriptionEntity;
import org.schabi.newpipe.database.feed.model.FeedLastUpdatedEntity;
import org.schabi.newpipe.database.history.dao.SearchHistoryDAO;
import org.schabi.newpipe.database.history.dao.StreamHistoryDAO;
import org.schabi.newpipe.database.history.model.SearchHistoryEntry;
import org.schabi.newpipe.database.history.model.StreamHistoryEntity;
import org.schabi.newpipe.database.playlist.dao.PlaylistDAO;
import org.schabi.newpipe.database.playlist.dao.PlaylistRemoteDAO;
import org.schabi.newpipe.database.playlist.dao.PlaylistStreamDAO;
import org.schabi.newpipe.database.playlist.model.PlaylistEntity;
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity;
import org.schabi.newpipe.database.stream.dao.StreamDAO;
import org.schabi.newpipe.database.stream.dao.StreamStateDAO;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.database.stream.model.StreamStateEntity;
import org.schabi.newpipe.database.subscription.SubscriptionDAO;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;

import static org.schabi.newpipe.database.Migrations.DB_VER_3;

@TypeConverters({Converters.class})
@Database(
        entities = {
                SubscriptionEntity.class, SearchHistoryEntry.class,
                StreamEntity.class, StreamHistoryEntity.class, StreamStateEntity.class,
                PlaylistEntity.class, PlaylistStreamEntity.class, PlaylistRemoteEntity.class,
                FeedEntity.class, FeedGroupEntity.class, FeedGroupSubscriptionEntity.class,
                FeedLastUpdatedEntity.class
        },
        version = DB_VER_3
)
public abstract class AppDatabase extends RoomDatabase {
    public static final String DATABASE_NAME = "newpipe.db";

    private static volatile AppDatabase instance;

    @NonNull
    public static AppDatabase getInstance(@NonNull final Context context) {
        AppDatabase result = instance;
        if (result == null) {
            synchronized (AppDatabase.class) {
                result = instance;
                if (result == null) {
                    instance = Room
                            .databaseBuilder(context, AppDatabase.class, AppDatabase.DATABASE_NAME)
                            .addMigrations(Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3)
                            .build();
                    result = instance;
                }
            }
        }
        return result;
    }

    @NonNull
    public abstract SearchHistoryDAO searchHistoryDAO();

    @NonNull
    public abstract StreamDAO streamDAO();

    @NonNull
    public abstract StreamHistoryDAO streamHistoryDAO();

    @NonNull
    public abstract StreamStateDAO streamStateDAO();

    @NonNull
    public abstract PlaylistDAO playlistDAO();

    @NonNull
    public abstract PlaylistStreamDAO playlistStreamDAO();

    @NonNull
    public abstract PlaylistRemoteDAO playlistRemoteDAO();

    @NonNull
    public abstract FeedDAO feedDAO();

    @NonNull
    public abstract FeedGroupDAO feedGroupDAO();

    @NonNull
    public abstract SubscriptionDAO subscriptionDAO();

    public void checkpoint() {
        final Cursor c = query("pragma wal_checkpoint(full)", null);
        if (c.moveToFirst() && c.getInt(0) == 1) {
            throw new RuntimeException("Checkpoint was blocked from completing");
        }
    }
}
