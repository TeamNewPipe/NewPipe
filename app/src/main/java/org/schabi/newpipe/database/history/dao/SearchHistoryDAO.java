package org.schabi.newpipe.database.history.dao;


import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Query;

import org.schabi.newpipe.database.history.model.SearchHistoryEntry;

import java.util.List;

import io.reactivex.Flowable;

@Dao
public interface SearchHistoryDAO extends HistoryDAO<SearchHistoryEntry> {

    String ORDER_BY_CREATION_DATE = " ORDER BY " + SearchHistoryEntry.CREATION_DATE + " DESC";

    @Query("DELETE FROM " + SearchHistoryEntry.TABLE_NAME)
    @Override
    void clearHistory();

    @Query("SELECT * FROM " + SearchHistoryEntry.TABLE_NAME + ORDER_BY_CREATION_DATE)
    @Override
    Flowable<List<SearchHistoryEntry>> findAll();

    @Override
    @Query("SELECT * FROM " + SearchHistoryEntry.TABLE_NAME + " WHERE " + SearchHistoryEntry.SERVICE_ID + " = :serviceId" + ORDER_BY_CREATION_DATE)
    Flowable<List<SearchHistoryEntry>> listByService(int serviceId);
}
