package org.schabi.newpipe.database.history.dao;

import static org.schabi.newpipe.database.history.model.StreamHistoryEntity.JOIN_STREAM_ID;
import static org.schabi.newpipe.database.history.model.StreamHistoryEntity.STREAM_ACCESS_DATE;
import static org.schabi.newpipe.database.history.model.StreamHistoryEntity.STREAM_HISTORY_TABLE;
import static org.schabi.newpipe.database.history.model.StreamHistoryEntity.STREAM_REPEAT_COUNT;
import static org.schabi.newpipe.database.stream.StreamStatisticsEntry.STREAM_LATEST_DATE;
import static org.schabi.newpipe.database.stream.StreamStatisticsEntry.STREAM_WATCH_COUNT;
import static org.schabi.newpipe.database.stream.model.StreamEntity.STREAM_ID;
import static org.schabi.newpipe.database.stream.model.StreamEntity.STREAM_TABLE;
import static org.schabi.newpipe.database.stream.model.StreamStateEntity.JOIN_STREAM_ID_ALIAS;
import static org.schabi.newpipe.database.stream.model.StreamStateEntity.STREAM_PROGRESS_MILLIS;
import static org.schabi.newpipe.database.stream.model.StreamStateEntity.STREAM_STATE_TABLE;

import androidx.annotation.Nullable;
import androidx.paging.PagingSource;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteQuery;

import org.schabi.newpipe.database.history.model.StreamHistoryEntity;
import org.schabi.newpipe.database.history.model.StreamHistoryEntry;
import org.schabi.newpipe.database.stream.StreamStatisticsEntry;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.local.history.SortKey;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;

@Dao
public interface StreamHistoryDAO {
    String ORDERED_HISTORY_QUERY = "SELECT * FROM " + STREAM_TABLE
            + " INNER JOIN "
            + "(SELECT " + JOIN_STREAM_ID + ", "
            + "  MAX(" + STREAM_ACCESS_DATE + ") AS " + STREAM_LATEST_DATE + ", "
            + "  SUM(" + STREAM_REPEAT_COUNT + ") AS " + STREAM_WATCH_COUNT
            + " FROM " + STREAM_HISTORY_TABLE
            + " GROUP BY " + JOIN_STREAM_ID + ")"
            + " ON " + STREAM_ID + " = " + JOIN_STREAM_ID
            + " LEFT JOIN "
            + "(SELECT " + JOIN_STREAM_ID + " AS " + JOIN_STREAM_ID_ALIAS + ", "
            + STREAM_PROGRESS_MILLIS
            + " FROM " + STREAM_STATE_TABLE + " )"
            + " ON " + STREAM_ID + " = " + JOIN_STREAM_ID_ALIAS;

    @Insert
    long insert(StreamHistoryEntity entity);

    @Delete
    void delete(StreamHistoryEntity entity);

    @Query("DELETE FROM " + STREAM_HISTORY_TABLE)
    Completable deleteAll();

    @Query("SELECT * FROM " + STREAM_TABLE
            + " INNER JOIN " + STREAM_HISTORY_TABLE
            + " ON " + STREAM_ID + " = " + JOIN_STREAM_ID
            + " ORDER BY " + STREAM_ID + " ASC")
    Flowable<List<StreamHistoryEntry>> getHistorySortedById();

    @Query("SELECT * FROM " + STREAM_HISTORY_TABLE + " WHERE " + JOIN_STREAM_ID
            + " = :streamId ORDER BY " + STREAM_ACCESS_DATE + " DESC LIMIT 1")
    @Nullable
    StreamHistoryEntity getLatestEntry(long streamId);

    @Query("DELETE FROM " + STREAM_HISTORY_TABLE + " WHERE " + JOIN_STREAM_ID + " = :streamId")
    Completable deleteStreamHistory(long streamId);

    @Query("SELECT * FROM " + STREAM_TABLE
            + " INNER JOIN " + STREAM_HISTORY_TABLE
            + " ON " + STREAM_ID + " = " + JOIN_STREAM_ID
            + " ORDER BY " + STREAM_ACCESS_DATE + " DESC")
    Flowable<List<StreamHistoryEntry>> getHistory();

    @RawQuery(observedEntities = {StreamStatisticsEntry.class, StreamEntity.class,
            StreamHistoryEntity.class})
    PagingSource<Integer, StreamStatisticsEntry> getOrderedHistoryByRaw(SupportSQLiteQuery query);

    default PagingSource<Integer, StreamStatisticsEntry> getOrderedHistory(SortKey key) {
        final String orderBy = switch (key) {
            case LAST_PLAYED -> STREAM_LATEST_DATE;
            case MOST_PLAYED -> STREAM_WATCH_COUNT;
        };
        return getOrderedHistoryByRaw(new SimpleSQLiteQuery(ORDERED_HISTORY_QUERY + " ORDER BY "
                + orderBy + " DESC"));
    }
}
