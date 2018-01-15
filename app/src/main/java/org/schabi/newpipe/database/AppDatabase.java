package org.schabi.newpipe.database;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.RoomDatabase;
import android.arch.persistence.room.TypeConverters;

import org.schabi.newpipe.database.history.dao.SearchHistoryDAO;
import org.schabi.newpipe.database.history.dao.WatchHistoryDAO;
import org.schabi.newpipe.database.history.model.SearchHistoryEntry;
import org.schabi.newpipe.database.history.model.WatchHistoryEntry;
import org.schabi.newpipe.database.playlist.dao.PlaylistDAO;
import org.schabi.newpipe.database.playlist.dao.PlaylistStreamDAO;
import org.schabi.newpipe.database.stream.dao.StreamHistoryDAO;
import org.schabi.newpipe.database.stream.dao.StreamDAO;
import org.schabi.newpipe.database.playlist.model.PlaylistEntity;
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity;
import org.schabi.newpipe.database.stream.model.StreamHistoryEntity;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.database.subscription.SubscriptionDAO;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;

@TypeConverters({Converters.class})
@Database(
        entities = {
                SubscriptionEntity.class, WatchHistoryEntry.class, SearchHistoryEntry.class,
                StreamEntity.class, StreamHistoryEntity.class, PlaylistEntity.class,
                PlaylistStreamEntity.class
        },
        version = 1,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    public static final String DATABASE_NAME = "newpipe.db";

    public abstract SubscriptionDAO subscriptionDAO();

    public abstract WatchHistoryDAO watchHistoryDAO();

    public abstract SearchHistoryDAO searchHistoryDAO();

    public abstract StreamDAO streamDAO();

    public abstract StreamHistoryDAO streamHistoryDAO();

    public abstract PlaylistDAO playlistDAO();

    public abstract PlaylistStreamDAO playlistStreamDAO();
}
