package org.schabi.newpipe.history;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.history.dao.SearchHistoryDAO;
import org.schabi.newpipe.database.history.dao.StreamHistoryDAO;
import org.schabi.newpipe.database.history.model.SearchHistoryEntry;
import org.schabi.newpipe.database.history.model.StreamHistoryEntity;
import org.schabi.newpipe.database.history.model.StreamHistoryEntry;
import org.schabi.newpipe.database.stream.StreamStatisticsEntry;
import org.schabi.newpipe.database.stream.dao.StreamDAO;
import org.schabi.newpipe.database.stream.dao.StreamStateDAO;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.database.stream.model.StreamStateEntity;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

public class HistoryRecordManager {

    private final AppDatabase database;
    private final StreamDAO streamTable;
    private final StreamHistoryDAO streamHistoryTable;
    private final SearchHistoryDAO searchHistoryTable;
    private final StreamStateDAO streamStateTable;
    private final SharedPreferences sharedPreferences;
    private final String searchHistoryKey;
    private final String streamHistoryKey;

    public HistoryRecordManager(final Context context) {
        database = NewPipeDatabase.getInstance(context);
        streamTable = database.streamDAO();
        streamHistoryTable = database.streamHistoryDAO();
        searchHistoryTable = database.searchHistoryDAO();
        streamStateTable = database.streamStateDAO();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        searchHistoryKey = context.getString(R.string.enable_search_history_key);
        streamHistoryKey = context.getString(R.string.enable_watch_history_key);
    }

    ///////////////////////////////////////////////////////
    // Watch History
    ///////////////////////////////////////////////////////

    public Maybe<Long> onViewed(final StreamInfo info) {
        if (!isStreamHistoryEnabled()) return Maybe.empty();

        final Date currentTime = new Date();
        return Maybe.fromCallable(() -> database.runInTransaction(() -> {
            final long streamId = streamTable.upsert(new StreamEntity(info));
            StreamHistoryEntity latestEntry = streamHistoryTable.getLatestEntry();

            if (latestEntry != null && latestEntry.getStreamUid() == streamId) {
                streamHistoryTable.delete(latestEntry);
                latestEntry.setAccessDate(currentTime);
                latestEntry.setRepeatCount(latestEntry.getRepeatCount() + 1);
                return streamHistoryTable.insert(latestEntry);
            } else {
                return streamHistoryTable.insert(new StreamHistoryEntity(streamId, currentTime));
            }
        })).subscribeOn(Schedulers.io());
    }

    public Single<Integer> deleteStreamHistory(final long streamId) {
        return Single.fromCallable(() -> streamHistoryTable.deleteStreamHistory(streamId))
                .subscribeOn(Schedulers.io());
    }

    public Flowable<List<StreamHistoryEntry>> getStreamHistory() {
        return streamHistoryTable.getHistory().subscribeOn(Schedulers.io());
    }

    public Flowable<List<StreamStatisticsEntry>> getStreamStatistics() {
        return streamHistoryTable.getStatistics().subscribeOn(Schedulers.io());
    }

    public Single<List<Long>> insertStreamHistory(final Collection<StreamHistoryEntry> entries) {
        List<StreamHistoryEntity> entities = new ArrayList<>(entries.size());
        for (final StreamHistoryEntry entry : entries) {
            entities.add(entry.toStreamHistoryEntity());
        }
        return Single.fromCallable(() -> streamHistoryTable.insertAll(entities))
                .subscribeOn(Schedulers.io());
    }

    public Single<Integer> deleteStreamHistory(final Collection<StreamHistoryEntry> entries) {
        List<StreamHistoryEntity> entities = new ArrayList<>(entries.size());
        for (final StreamHistoryEntry entry : entries) {
            entities.add(entry.toStreamHistoryEntity());
        }
        return Single.fromCallable(() -> streamHistoryTable.delete(entities))
                .subscribeOn(Schedulers.io());
    }

    private boolean isStreamHistoryEnabled() {
        return sharedPreferences.getBoolean(streamHistoryKey, false);
    }

    ///////////////////////////////////////////////////////
    // Search History
    ///////////////////////////////////////////////////////

    public Single<List<Long>> insertSearches(final Collection<SearchHistoryEntry> entries) {
        return Single.fromCallable(() -> searchHistoryTable.insertAll(entries))
                .subscribeOn(Schedulers.io());
    }

    public Single<Integer> deleteSearches(final Collection<SearchHistoryEntry> entries) {
        return Single.fromCallable(() -> searchHistoryTable.delete(entries))
                .subscribeOn(Schedulers.io());
    }

    public Flowable<List<SearchHistoryEntry>> getSearchHistory() {
        return searchHistoryTable.getAll();
    }

    public Maybe<Long> onSearched(final int serviceId, final String search) {
        if (!isSearchHistoryEnabled()) return Maybe.empty();

        final Date currentTime = new Date();
        final SearchHistoryEntry newEntry = new SearchHistoryEntry(currentTime, serviceId, search);

        return Maybe.fromCallable(() -> database.runInTransaction(() -> {
            SearchHistoryEntry latestEntry = searchHistoryTable.getLatestEntry();
            if (latestEntry != null && latestEntry.hasEqualValues(newEntry)) {
                latestEntry.setCreationDate(currentTime);
                return (long) searchHistoryTable.update(latestEntry);
            } else {
                return searchHistoryTable.insert(newEntry);
            }
        })).subscribeOn(Schedulers.io());
    }

    public Single<Integer> deleteSearchHistory(final String search) {
        return Single.fromCallable(() -> searchHistoryTable.deleteAllWhereQuery(search))
                .subscribeOn(Schedulers.io());
    }

    public Flowable<List<SearchHistoryEntry>> getRelatedSearches(final String query,
                                                                 final int similarQueryLimit,
                                                                 final int uniqueQueryLimit) {
        return query.length() > 0
                ? searchHistoryTable.getSimilarEntries(query, similarQueryLimit)
                : searchHistoryTable.getUniqueEntries(uniqueQueryLimit);
    }

    private boolean isSearchHistoryEnabled() {
        return sharedPreferences.getBoolean(searchHistoryKey, false);
    }

    ///////////////////////////////////////////////////////
    // Stream State History
    ///////////////////////////////////////////////////////

    @SuppressWarnings("unused")
    public Maybe<StreamStateEntity> loadStreamState(final StreamInfo info) {
        return Maybe.fromCallable(() -> streamTable.upsert(new StreamEntity(info)))
                .flatMap(streamId -> streamStateTable.getState(streamId).firstElement())
                .flatMap(states -> states.isEmpty() ? Maybe.empty() : Maybe.just(states.get(0)))
                .subscribeOn(Schedulers.io());
    }

    public Maybe<Long> saveStreamState(@NonNull final StreamInfo info, final long progressTime) {
        return Maybe.fromCallable(() -> database.runInTransaction(() -> {
            final long streamId = streamTable.upsert(new StreamEntity(info));
            return streamStateTable.upsert(new StreamStateEntity(streamId, progressTime));
        })).subscribeOn(Schedulers.io());
    }

    ///////////////////////////////////////////////////////
    // Utility
    ///////////////////////////////////////////////////////

    public Single<Integer> removeOrphanedRecords() {
        return Single.fromCallable(streamTable::deleteOrphans).subscribeOn(Schedulers.io());
    }
}
