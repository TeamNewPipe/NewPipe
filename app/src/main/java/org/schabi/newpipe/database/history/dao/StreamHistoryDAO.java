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
import androidx.room.RewriteQueriesToDropUnusedColumns;

import org.schabi.newpipe.database.history.model.StreamHistoryEntity;
import org.schabi.newpipe.database.history.model.StreamHistoryEntry;
import org.schabi.newpipe.database.stream.StreamStatisticsEntry;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;

@Dao
public interface StreamHistoryDAO {
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

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM " + STREAM_TABLE
            // Select the latest entry and watch count for each stream id on history table
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
            + " ON " + STREAM_ID + " = " + JOIN_STREAM_ID_ALIAS

            + " ORDER BY " + STREAM_LATEST_DATE + " DESC"
    )
    PagingSource<Integer, StreamStatisticsEntry> getHistoryOrderedByLastWatched();

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM " + STREAM_TABLE
            // Select the latest entry and watch count for each stream id on history table
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
            + " ON " + STREAM_ID + " = " + JOIN_STREAM_ID_ALIAS

            + " ORDER BY " + STREAM_WATCH_COUNT + " DESC"
    )
    PagingSource<Integer, StreamStatisticsEntry> getHistoryOrderedByViewCount();
}
