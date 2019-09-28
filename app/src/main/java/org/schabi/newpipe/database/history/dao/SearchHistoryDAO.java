package org.schabi.newpipe.database.history.dao;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Transaction;
import android.support.annotation.Nullable;

import org.schabi.newpipe.database.history.model.SearchHistoryEntry;

import java.util.Collection;
import java.util.List;

import io.reactivex.Flowable;

import static org.schabi.newpipe.database.history.model.SearchHistoryEntry.CREATION_DATE;
import static org.schabi.newpipe.database.history.model.SearchHistoryEntry.SEARCH;
import static org.schabi.newpipe.database.history.model.SearchHistoryEntry.SEARCH_HISTORY_ID;
import static org.schabi.newpipe.database.history.model.SearchHistoryEntry.SERVICE_ID;
import static org.schabi.newpipe.database.history.model.SearchHistoryEntry.TABLE_NAME;

@Dao
public abstract class SearchHistoryDAO implements HistoryDAO<SearchHistoryEntry> {

    private final static String ORDER_BY_CREATION_DATE = " ORDER BY " + CREATION_DATE + " DESC";

    @Query("SELECT * FROM " + TABLE_NAME +
            " WHERE " + SEARCH_HISTORY_ID + " = (SELECT MAX(" + SEARCH_HISTORY_ID + ") FROM " + TABLE_NAME + ")")
    @Nullable
    public abstract SearchHistoryEntry getLatestEntry();

    @Query("DELETE FROM " + TABLE_NAME)
    @Override
    public abstract int deleteAll();

    @Query("DELETE FROM " + TABLE_NAME + " WHERE " + SEARCH + " = :query")
    public abstract int deleteAllWhereQuery(String query);

    @Query("SELECT * FROM " + TABLE_NAME + ORDER_BY_CREATION_DATE)
    @Override
    public abstract Flowable<List<SearchHistoryEntry>> getAll();

    @Query("SELECT * FROM " + TABLE_NAME + " GROUP BY " + SEARCH + ORDER_BY_CREATION_DATE + " LIMIT :limit")
    public abstract Flowable<List<SearchHistoryEntry>> getUniqueEntries(int limit);

    @Query("SELECT * FROM " + TABLE_NAME + " WHERE " + SERVICE_ID + " = :serviceId" + ORDER_BY_CREATION_DATE)
    @Override
    public abstract Flowable<List<SearchHistoryEntry>> listByService(int serviceId);

    @Query("SELECT * FROM " + TABLE_NAME + " WHERE " + SEARCH + " LIKE :query || '%' GROUP BY " + SEARCH + " LIMIT :limit")
    public abstract Flowable<List<SearchHistoryEntry>> getSimilarEntries(String query, int limit);

    @Override
    @Transaction
    public void destroyAndRefill(Collection<SearchHistoryEntry> entities) {
        deleteAll();
        insertAll(entities);
    }
}
