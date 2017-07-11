package org.schabi.newpipe.history;


import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.arch.persistence.room.TypeConverters;
import android.content.Context;

import org.schabi.newpipe.history.dao.SearchHistoryDAO;
import org.schabi.newpipe.history.dao.WatchHistoryDAO;
import org.schabi.newpipe.history.model.SearchHistoryEntry;
import org.schabi.newpipe.history.model.WatchHistoryEntry;

@TypeConverters({Converters.class})
@Database(entities = {WatchHistoryEntry.class, SearchHistoryEntry.class}, version = 1)
public abstract class HistoryDatabase extends RoomDatabase {
    private static HistoryDatabase INSTANCE;

    public static synchronized HistoryDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = Room.databaseBuilder(context.getApplicationContext(), HistoryDatabase.class, "history")
                    .allowMainThreadQueries()
                    .build();
        }
        return INSTANCE;
    }

    public abstract WatchHistoryDAO watchHistoryDAO();

    public abstract SearchHistoryDAO searchHistoryDAO();
}
