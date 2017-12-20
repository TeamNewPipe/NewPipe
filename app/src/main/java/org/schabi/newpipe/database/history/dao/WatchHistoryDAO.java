package org.schabi.newpipe.database.history.dao;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Query;

import org.schabi.newpipe.database.history.model.WatchHistoryEntry;

import java.util.List;

import io.reactivex.Flowable;

import static org.schabi.newpipe.database.history.model.WatchHistoryEntry.CREATION_DATE;
import static org.schabi.newpipe.database.history.model.WatchHistoryEntry.ID;
import static org.schabi.newpipe.database.history.model.WatchHistoryEntry.SERVICE_ID;
import static org.schabi.newpipe.database.history.model.WatchHistoryEntry.TABLE_NAME;

@Dao
public interface WatchHistoryDAO extends HistoryDAO<WatchHistoryEntry> {

    String ORDER_BY_CREATION_DATE = " ORDER BY " + CREATION_DATE + " DESC";

    @Query("SELECT * FROM " + TABLE_NAME + " WHERE " + ID + " = (SELECT MAX(" + ID + ") FROM " + TABLE_NAME + ")")
    @Override
    WatchHistoryEntry getLatestEntry();

    @Query("DELETE FROM " + TABLE_NAME)
    @Override
    int deleteAll();

    @Query("SELECT * FROM " + TABLE_NAME + ORDER_BY_CREATION_DATE)
    @Override
    Flowable<List<WatchHistoryEntry>> getAll();

    @Query("SELECT * FROM " + TABLE_NAME + " WHERE " + SERVICE_ID + " = :serviceId" + ORDER_BY_CREATION_DATE)
    @Override
    Flowable<List<WatchHistoryEntry>> listByService(int serviceId);
}
