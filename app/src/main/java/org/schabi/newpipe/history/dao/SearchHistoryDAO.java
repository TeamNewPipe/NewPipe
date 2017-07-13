package org.schabi.newpipe.history.dao;


import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.support.annotation.NonNull;

import org.schabi.newpipe.history.model.SearchHistoryEntry;

@Dao
public interface SearchHistoryDAO extends HistoryDAO<SearchHistoryEntry> {
    @Query("DELETE FROM search_history")
    @Override
    void clearHistory();

    @Insert
    @Override
    void addHistoryEntry(SearchHistoryEntry historyEntries);

    @NonNull
    @Query("SELECT * FROM search_history")
    @Override
    SearchHistoryEntry[] loadAllHistoryEntries();

    @Delete
    @Override
    void removeHistoryEntry(SearchHistoryEntry entry);
}
