package org.schabi.newpipe.database;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.RoomDatabase;
import android.arch.persistence.room.TypeConverters;

import org.schabi.newpipe.database.history.Converters;
import org.schabi.newpipe.database.history.dao.SearchHistoryDAO;
import org.schabi.newpipe.database.history.dao.WatchHistoryDAO;
import org.schabi.newpipe.database.history.model.SearchHistoryEntry;
import org.schabi.newpipe.database.history.model.WatchHistoryEntry;
import org.schabi.newpipe.database.subscription.SubscriptionDAO;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;

@TypeConverters({Converters.class})
@Database(entities = {SubscriptionEntity.class, WatchHistoryEntry.class, SearchHistoryEntry.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public static final String DATABASE_NAME = "newpipe.db";

    public abstract SubscriptionDAO subscriptionDAO();

    public abstract WatchHistoryDAO watchHistoryDAO();

    public abstract SearchHistoryDAO searchHistoryDAO();
}
