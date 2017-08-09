package org.schabi.newpipe.database.history.dao;


import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Query;

import org.schabi.newpipe.database.history.model.SearchHistoryEntry;

import java.util.List;

import io.reactivex.Flowable;

@Dao
public interface SearchHistoryDAO extends HistoryDAO<SearchHistoryEntry> {
    @Query("DELETE FROM " + SearchHistoryEntry.TABLE_NAME)
    @Override
    void clearHistory();

    @Query("SELECT * FROM " + SearchHistoryEntry.TABLE_NAME)
    @Override
    Flowable<List<SearchHistoryEntry>> findAll();

    @Override
    @Query("SELECT * FROM " + SearchHistoryEntry.TABLE_NAME + " WHERE " + SearchHistoryEntry.SERVICE_ID + " = :serviceId")
    Flowable<List<SearchHistoryEntry>> listByService(int serviceId);
}
