package org.schabi.newpipe.database.history.dao;


import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.support.annotation.NonNull;

import org.schabi.newpipe.database.history.model.WatchHistoryEntry;

@Dao
public interface WatchHistoryDAO extends HistoryDAO<WatchHistoryEntry> {
    @Query("DELETE FROM watch_history")
    @Override
    void clearHistory();

    @Insert
    @Override
    void addHistoryEntry(WatchHistoryEntry watchHistoryEntry);

    @Query("SELECT * FROM watch_history")
    @Override
    @NonNull
    WatchHistoryEntry[] loadAllHistoryEntries();


    @Delete
    @Override
    void removeHistoryEntry(WatchHistoryEntry historyEntry);
}
