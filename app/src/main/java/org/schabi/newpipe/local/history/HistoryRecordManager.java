package org.schabi.newpipe.local.history;

import static org.schabi.newpipe.util.ExtractorHelper.getStreamInfo;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.history.dao.SearchHistoryDAO;
import org.schabi.newpipe.database.history.dao.StreamHistoryDAO;
import org.schabi.newpipe.database.history.model.SearchHistoryEntry;
import org.schabi.newpipe.database.history.model.StreamHistoryEntity;
import org.schabi.newpipe.database.history.model.StreamHistoryEntry;
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry;
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity;
import org.schabi.newpipe.database.stream.StreamStatisticsEntry;
import org.schabi.newpipe.database.stream.dao.StreamDAO;
import org.schabi.newpipe.database.stream.dao.StreamStateDAO;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.database.stream.model.StreamStateEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.local.feed.FeedViewModel;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

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

    public Completable markAsWatched(final StreamInfoItem info) {
        if (!isStreamHistoryEnabled()) {
            return Completable.complete();
        }

        return getStreamInfo(info.getServiceId(), info.getUrl(), false)
                .map(streamInfo -> {
                    long duration = streamInfo.getDuration();
                    long streamId = streamTable.upsert(new StreamEntity(streamInfo));

                    final StreamStateEntity entity = new StreamStateEntity(streamId, duration * 1000);
                    streamStateTable.upsert(entity);

                    final StreamHistoryEntity latestEntry = streamHistoryTable.getLatestEntry(streamId);
                    if (latestEntry == null) {
                        final OffsetDateTime currentTime = OffsetDateTime.now(ZoneOffset.UTC);
                        streamHistoryTable.insert(new StreamHistoryEntity(streamId, currentTime, 0));
                    }
                    return true;
                })
                .ignoreElement()
                .subscribeOn(Schedulers.io());
    }

    public Maybe<Long> onViewed(final StreamInfo info) {
        if (!isStreamHistoryEnabled()) {
            return Maybe.empty();
        }

        final OffsetDateTime currentTime = OffsetDateTime.now(ZoneOffset.UTC);
        return Maybe.fromCallable(() -> database.runInTransaction(() -> {
            final long streamId = streamTable.upsert(new StreamEntity(info));
            final StreamHistoryEntity latestEntry = streamHistoryTable.getLatestEntry(streamId);

            if (latestEntry != null) {
                streamHistoryTable.delete(latestEntry);
                latestEntry.setAccessDate(currentTime);
                latestEntry.setRepeatCount(latestEntry.getRepeatCount() + 1);
                return streamHistoryTable.insert(latestEntry);
            } else {
                return streamHistoryTable.insert(new StreamHistoryEntity(streamId, currentTime, 1));
            }
        })).subscribeOn(Schedulers.io());
    }

    ///////////////////////////////////////////////////////
    // Search History
    ///////////////////////////////////////////////////////

    public Maybe<Long> onSearched(final int serviceId, final String search) {
        if (!isSearchHistoryEnabled()) {
            return Maybe.empty();
        }

        final OffsetDateTime currentTime = OffsetDateTime.now(ZoneOffset.UTC);
        final SearchHistoryEntry newEntry = new SearchHistoryEntry(currentTime, serviceId, search);

        return Maybe.fromCallable(() -> database.runInTransaction(() -> {
            final SearchHistoryEntry latestEntry = searchHistoryTable.getLatestEntry();
            if (latestEntry != null && latestEntry.hasEqualValues(newEntry)) {
                latestEntry.setCreationDate(currentTime);
                return (long) searchHistoryTable.update(latestEntry);
            } else {
                final long insertedId = searchHistoryTable.insert(newEntry);

                boolean infiniteEnabled = sharedPreferences.getBoolean("infinite_search_history", false);
                if (!infiniteEnabled) {
                    List<SearchHistoryEntry> allEntries = searchHistoryTable.getAllEntries();
                    int maxSize = 25;
                    if (allEntries.size() > maxSize) {
                        for (int i = maxSize; i < allEntries.size(); i++) {
                            searchHistoryTable.delete(allEntries.get(i));
                        }
                    }
                }
                return insertedId;
            }
        })).subscribeOn(Schedulers.io());
    }

    public Flowable<List<String>> getRelatedSearches(
            final String query,
            final int similarQueryLimit,
            final int uniqueQueryLimit) {

        boolean infiniteEnabled = sharedPreferences.getBoolean("infinite_search_history", false);
        int largeLimit = 100000;

        int sLimit = infiniteEnabled ? largeLimit : similarQueryLimit;
        int uLimit = infiniteEnabled ? largeLimit : uniqueQueryLimit;

        return query.isEmpty()
                ? searchHistoryTable.getUniqueEntries(uLimit)
                : searchHistoryTable.getSimilarEntries(query, sLimit);
    }

    ///////////////////////////////////////////////////////
    // Utility
    ///////////////////////////////////////////////////////

    private boolean isSearchHistoryEnabled() {
        return sharedPreferences.getBoolean(searchHistoryKey, false);
    }

    private boolean isStreamHistoryEnabled() {
        return sharedPreferences.getBoolean(streamHistoryKey, false);
    }

    public Single<Integer> removeOrphanedRecords() {
        return Single.fromCallable(streamTable::deleteOrphans).subscribeOn(Schedulers.io());
    }
}
