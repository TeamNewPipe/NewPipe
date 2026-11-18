package org.schabi.newpipe.local.history;

/*
 * Copyright (C) Mauricio Colli 2018
 * HistoryRecordManager.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

import org.schabi.newpipe.recommendations.LocalRecommendations;

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
import org.schabi.newpipe.profiles.ProfileManager;
import org.schabi.newpipe.sync.HistorySyncRecorder;
import org.schabi.newpipe.util.ExtractorHelper;

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
    private final HistorySyncRecorder historySyncRecorder;
    private final String profileId;
    private final SharedPreferences sharedPreferences;
    private final String searchHistoryKey;
    private final String streamHistoryKey;

    private final Context recommendationContext;

    public HistoryRecordManager(final Context context) {
        this(context, ProfileManager.getActiveProfileId(context));
    }

    public HistoryRecordManager(final Context context, @NonNull final String profileId) {
        recommendationContext = context.getApplicationContext();
        database = NewPipeDatabase.getInstance(context);
        streamTable = database.streamDAO();
        streamHistoryTable = database.streamHistoryDAO();
        searchHistoryTable = database.searchHistoryDAO();
        streamStateTable = database.streamStateDAO();
        historySyncRecorder = HistorySyncRecorder.get(context);
        this.profileId = profileId;
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        searchHistoryKey = context.getString(R.string.enable_search_history_key);
        streamHistoryKey = context.getString(R.string.enable_watch_history_key);
    }

    ///////////////////////////////////////////////////////
    // Watch History
    ///////////////////////////////////////////////////////

    /**
     * Marks a stream item as watched such that it is hidden from the feed if watched videos are
     * hidden. Adds a history entry and updates the stream progress to 100%.
     *
     * @see FeedViewModel#setSaveShowPlayedItems
     * @param info the item to mark as watched
     * @return a Maybe containing the ID of the item if successful
     */
    public Maybe<Long> markAsWatched(final StreamInfoItem info) {
        if (!isStreamHistoryEnabled()) {
            return Maybe.empty();
        }

        final OffsetDateTime currentTime = OffsetDateTime.now(ZoneOffset.UTC);
        return Maybe.fromCallable(() -> {
            final StreamEntity stream;
            final long duration;
            // Duration will not exist if the item was loaded with fast mode, so fetch it if empty
            if (info.getDuration() < 0) {
                final StreamInfo completeInfo = ExtractorHelper.getStreamInfo(
                        info.getServiceId(),
                        info.getUrl(),
                        false
                )
                        .subscribeOn(Schedulers.io())
                        .blockingGet();
                duration = completeInfo.getDuration();
                stream = new StreamEntity(completeInfo);
            } else {
                duration = info.getDuration();
                stream = new StreamEntity(info);
            }

            // Resolve metadata before taking the database write lock.
            return database.runInTransaction(() -> {
                if (IncognitoMode.isEnabled(sharedPreferences)) {
                    return null;
                }
                final long streamId = streamTable.upsert(stream);
                // Update the stream progress to the full duration of the video
                final StreamStateEntity entity = new StreamStateEntity(
                        streamId,
                        duration * 1000,
                        profileId
                );
                recordProgress(
                        streamId,
                        entity.getProgressMillis(),
                        currentTime.toInstant().toEpochMilli()
                );
                streamStateTable.upsert(entity);

                // Add a history entry
                final StreamHistoryEntity latestEntry =
                        streamHistoryTable.getLatestEntryForProfile(profileId, streamId);
                if (latestEntry == null) {
                    // never actually viewed: add history entry but with 0 views
                    recordWatchEvent(
                            streamId,
                            currentTime.toInstant().toEpochMilli(),
                            0
                    );
                    return streamHistoryTable.insert(
                            new StreamHistoryEntity(streamId, currentTime, 0, profileId));
                } else {
                    return 0L;
                }
            });
        }).subscribeOn(Schedulers.io());
    }

    /**
     * Marks a local item watched without syncing its device-scoped content URI.
     *
     * @param item the device-local queue item to mark as watched
     * @return the inserted history row ID, or an empty result when history is disabled
     */
    public Maybe<Long> markAsWatched(@NonNull final PlayQueueItem item) {
        if (!item.isLocalMedia() || !isStreamHistoryEnabled()) {
            return Maybe.empty();
        }
        final OffsetDateTime currentTime = OffsetDateTime.now(ZoneOffset.UTC);
        return Maybe.fromCallable(() -> database.runInTransaction(() -> {
                if (IncognitoMode.isEnabled(sharedPreferences)) {
                    return null;
                }
            final long streamId = streamTable.upsert(new StreamEntity(item));
            streamStateTable.upsert(
                    new StreamStateEntity(streamId,
                            Math.max(0L, item.getDuration()) * 1000, profileId));
            if (streamHistoryTable.getLatestEntryForProfile(profileId, streamId) == null) {
                return streamHistoryTable.insert(
                        new StreamHistoryEntity(streamId, currentTime, 0, profileId));
            }
            return 0L;
        })).subscribeOn(Schedulers.io());
    }

    public Maybe<Long> onViewed(final StreamInfo info) {
        if (!isStreamHistoryEnabled()) {
            return Maybe.empty();
        }

        final OffsetDateTime currentTime = OffsetDateTime.now(ZoneOffset.UTC);
        return Maybe.fromCallable(() -> database.runInTransaction(() -> {
                if (IncognitoMode.isEnabled(sharedPreferences)) {
                    return null;
                }
            final long streamId = streamTable.upsert(new StreamEntity(info));
            recordWatchEvent(
                    streamId,
                    currentTime.toInstant().toEpochMilli(),
                    1
            );
            final StreamHistoryEntity latestEntry =
                    streamHistoryTable.getLatestEntryForProfile(profileId, streamId);

            if (latestEntry != null) {
                streamHistoryTable.delete(latestEntry);
                latestEntry.setAccessDate(currentTime);
                latestEntry.setRepeatCount(latestEntry.getRepeatCount() + 1);
                return streamHistoryTable.insert(latestEntry);
            } else {
                // just viewed for the first time: set 1 view
                return streamHistoryTable.insert(
                        new StreamHistoryEntity(streamId, currentTime, 1, profileId));
            }
        })).subscribeOn(Schedulers.io());
    }

    /**
     * Records device-local playback without adding a non-portable URI to sync.
     *
     * @param item the device-local queue item that was viewed
     * @return the inserted or updated history row ID, or an empty result when history is disabled
     */
    public Maybe<Long> onViewed(@NonNull final PlayQueueItem item) {
        if (!item.isLocalMedia() || !isStreamHistoryEnabled()) {
            return Maybe.empty();
        }
        final OffsetDateTime currentTime = OffsetDateTime.now(ZoneOffset.UTC);
        return Maybe.fromCallable(() -> database.runInTransaction(() -> {
                if (IncognitoMode.isEnabled(sharedPreferences)) {
                    return null;
                }
            final long streamId = streamTable.upsert(new StreamEntity(item));
            final StreamHistoryEntity latestEntry =
                    streamHistoryTable.getLatestEntryForProfile(profileId, streamId);
            if (latestEntry != null) {
                streamHistoryTable.delete(latestEntry);
                latestEntry.setAccessDate(currentTime);
                latestEntry.setRepeatCount(latestEntry.getRepeatCount() + 1);
                return streamHistoryTable.insert(latestEntry);
            }
            return streamHistoryTable.insert(
                    new StreamHistoryEntity(streamId, currentTime, 1, profileId));
        })).subscribeOn(Schedulers.io());
    }

    /**
     * Clears the watched marker and saved playback position for one stream.
     *
     * @param info the stream to mark as unwatched
     * @return an operation that completes after the stream history and state are removed
     */
    public Completable markAsUnwatched(final StreamInfoItem info) {
        return Completable.fromAction(() -> database.runInTransaction(() -> {
            final long streamId = streamTable.upsert(new StreamEntity(info));
            recordWatchStreamDelete(streamId);
            streamStateTable.deleteStateForProfile(profileId, streamId);
            streamHistoryTable.deleteStreamHistoryForProfile(profileId, streamId);
        })).subscribeOn(Schedulers.io());
    }

    public Completable markLocalMediaAsUnwatched(@NonNull final PlayQueueItem item) {
        return Completable.fromAction(() -> database.runInTransaction(() -> {
            if (!item.isLocalMedia() || IncognitoMode.isEnabled(sharedPreferences)) {
                return;
            }
            final StreamEntity stream = streamTable.getStreamDirect(
                    item.getServiceId(), item.getUrl());
            if (stream != null) {
                streamStateTable.deleteStateForProfile(profileId, stream.getUid());
                streamHistoryTable.deleteStreamHistoryForProfile(profileId, stream.getUid());
            }
        })).subscribeOn(Schedulers.io());
    }

    public Completable deleteStreamHistoryAndState(final long streamId) {
        return Completable.fromAction(() -> database.runInTransaction(() -> {
            recordWatchStreamDelete(streamId);
            streamStateTable.deleteStateForProfile(profileId, streamId);
            streamHistoryTable.deleteStreamHistoryForProfile(profileId, streamId);
        })).subscribeOn(Schedulers.io());
    }

    public Single<Integer> deleteWholeStreamHistory() {
        return Single.fromCallable(() -> database.runInTransaction(() -> {
            recordWatchAllDelete();
            return streamHistoryTable.deleteAllForProfile(profileId);
        }))
                .doOnSuccess(deleted ->
                        org.schabi.newpipe.player.recovery.QueueRecovery.clear(
                                recommendationContext, profileId))
                .subscribeOn(Schedulers.io());
    }

    public Single<Integer> deleteCompleteStreamStateHistory() {
        return Single.fromCallable(() -> database.runInTransaction(() -> {
            recordProgressAllDelete();
            return streamStateTable.deleteAllForProfile(profileId);
        }))
                .doOnSuccess(deleted ->
                        org.schabi.newpipe.player.recovery.QueueRecovery.clear(
                                recommendationContext, profileId))
                .subscribeOn(Schedulers.io());
    }

    public Flowable<List<StreamHistoryEntry>> getStreamHistorySortedById() {
        return streamHistoryTable.getHistorySortedByIdForProfile(profileId)
                .subscribeOn(Schedulers.io());
    }

    public Flowable<List<StreamStatisticsEntry>> getStreamStatistics() {
        return streamHistoryTable.getStatisticsForProfile(profileId)
                .subscribeOn(Schedulers.io());
    }

    private boolean isStreamHistoryEnabled() {
        return !IncognitoMode.isEnabled(sharedPreferences)
                && sharedPreferences.getBoolean(streamHistoryKey, false);
    }

    ///////////////////////////////////////////////////////
    // Search History
    ///////////////////////////////////////////////////////

    public Maybe<Long> onSearched(final int serviceId, final String search) {
        if (!isSearchHistoryEnabled()) {
            return Maybe.empty();
        }

        final String canonicalSearch = search.trim();
        if (canonicalSearch.isEmpty()) {
            return Maybe.empty();
        }
        final OffsetDateTime currentTime = OffsetDateTime.now(ZoneOffset.UTC);
        LocalRecommendations.searched(
                recommendationContext, serviceId, canonicalSearch, profileId);
        final SearchHistoryEntry newEntry = new SearchHistoryEntry(
                currentTime,
                serviceId,
                canonicalSearch,
                0L,
                profileId
        );

        return Maybe.fromCallable(() -> database.runInTransaction(() -> {
                if (IncognitoMode.isEnabled(sharedPreferences)) {
                    return null;
                }
            historySyncRecorder.recordSearchForProfile(
                    profileId,
                    serviceId,
                    canonicalSearch,
                    currentTime.toInstant().toEpochMilli()
            );
            return searchHistoryTable.insert(newEntry);
        })).subscribeOn(Schedulers.io());
    }

    public Single<Integer> deleteSearchHistory(final String search) {
        return Single.fromCallable(() -> database.runInTransaction(() -> {
            historySyncRecorder.recordSearchDeleteForProfile(profileId, search);
            return searchHistoryTable.deleteAllWhereQueryForProfile(profileId, search);
        }))
                .subscribeOn(Schedulers.io());
    }

    public Single<Integer> deleteCompleteSearchHistory() {
        return Single.fromCallable(() -> database.runInTransaction(() -> {
            historySyncRecorder.recordSearchAllDeleteForProfile(profileId);
            return searchHistoryTable.deleteAllForProfile(profileId);
        }))
                .subscribeOn(Schedulers.io());
    }

    public Flowable<List<String>> getRelatedSearches(final String query,
                                                     final int similarQueryLimit,
                                                     final int uniqueQueryLimit) {
        return query.length() > 0
                ? searchHistoryTable.getSimilarEntriesForProfile(
                        profileId, query, similarQueryLimit)
                : searchHistoryTable.getUniqueEntriesForProfile(profileId, uniqueQueryLimit);
    }

    private boolean isSearchHistoryEnabled() {
        return !IncognitoMode.isEnabled(sharedPreferences)
                && sharedPreferences.getBoolean(searchHistoryKey, false);
    }

    ///////////////////////////////////////////////////////
    // Stream State History
    ///////////////////////////////////////////////////////

    public Maybe<StreamStateEntity> loadStreamState(final PlayQueueItem queueItem) {
        if (IncognitoMode.isEnabled(sharedPreferences)) {
            return Maybe.empty();
        }
        final Single<Long> streamId = queueItem.isLocalMedia()
                ? Single.fromCallable(() -> streamTable.upsert(new StreamEntity(queueItem)))
                : queueItem.getStream().map(info -> streamTable.upsert(new StreamEntity(info)));
        return streamId
                .flatMapPublisher(streamUid ->
                        streamStateTable.getStateForProfile(profileId, streamUid))
                .firstElement()
                .flatMap(list -> list.isEmpty() ? Maybe.empty() : Maybe.just(list.get(0)))
                .filter(state -> state.isValid(queueItem.getDuration()))
                .subscribeOn(Schedulers.io());
    }

    public Maybe<StreamStateEntity> loadStreamState(final StreamInfo info) {
        if (IncognitoMode.isEnabled(sharedPreferences)) {
            return Maybe.empty();
        }
        return Single.fromCallable(() -> streamTable.upsert(new StreamEntity(info)))
                .flatMapPublisher(streamId ->
                        streamStateTable.getStateForProfile(profileId, streamId))
                .firstElement()
                .flatMap(list -> list.isEmpty() ? Maybe.empty() : Maybe.just(list.get(0)))
                .filter(state -> state.isValid(info.getDuration()))
                .subscribeOn(Schedulers.io());
    }

    public Completable saveStreamState(@NonNull final StreamInfo info, final long progressMillis) {
        if (IncognitoMode.isEnabled(sharedPreferences)) {
            return Completable.complete();
        }
        return Completable.fromAction(() -> database.runInTransaction(() -> {
            if (IncognitoMode.isEnabled(sharedPreferences)) {
                return;
            }
            final long streamId = streamTable.upsert(new StreamEntity(info));
            final StreamStateEntity state =
                    new StreamStateEntity(streamId, progressMillis, profileId);
            if (state.isValid(info.getDuration())) {
                recordProgress(
                        streamId,
                        progressMillis,
                        System.currentTimeMillis()
                );
                streamStateTable.upsert(state);
            }
        })).subscribeOn(Schedulers.io());
    }

    /**
     * Saves local resume progress only on this device.
     *
     * @param item the device-local queue item whose progress should be saved
     * @param progressMillis the current playback position in milliseconds
     * @return a completable that finishes after the local state is stored
     */
    public Completable saveStreamState(@NonNull final PlayQueueItem item,
                                       final long progressMillis) {
        if (IncognitoMode.isEnabled(sharedPreferences)) {
            return Completable.complete();
        }
        return Completable.fromAction(() -> database.runInTransaction(() -> {
            if (IncognitoMode.isEnabled(sharedPreferences)) {
                return;
            }
            final long streamId = streamTable.upsert(new StreamEntity(item));
            final StreamStateEntity state =
                    new StreamStateEntity(streamId, progressMillis, profileId);
            if (state.isValid(item.getDuration())) {
                streamStateTable.upsert(state);
            }
        })).subscribeOn(Schedulers.io());
    }

    public Single<StreamStateEntity[]> loadStreamState(final InfoItem info) {
        return Single.fromCallable(() -> {
            final StreamEntity stream = streamTable.getStreamDirect(
                    info.getServiceId(), info.getUrl());
            return new StreamStateEntity[]{stream == null ? null
                    : streamStateTable.getStateDirectForProfile(profileId, stream.getUid())};
        }).subscribeOn(Schedulers.io());
    }

    public Single<List<StreamStateEntity>> loadLocalStreamStateBatch(
            final List<? extends LocalItem> items) {
        return Single.fromCallable(() -> {
            final List<StreamStateEntity> result = new ArrayList<>(items.size());
            for (final LocalItem item : items) {
                final long streamId;
                if (item instanceof StreamStatisticsEntry) {
                    streamId = ((StreamStatisticsEntry) item).getStreamId();
                } else if (item instanceof PlaylistStreamEntity) {
                    streamId = ((PlaylistStreamEntity) item).getStreamUid();
                } else if (item instanceof PlaylistStreamEntry) {
                    streamId = ((PlaylistStreamEntry) item).getStreamId();
                } else {
                    result.add(null);
                    continue;
                }
                final List<StreamStateEntity> states = streamStateTable
                        .getStateForProfile(profileId, streamId)
                        .blockingFirst();
                if (states.isEmpty()) {
                    result.add(null);
                } else {
                    result.add(states.get(0));
                }
            }
            return result;
        }).subscribeOn(Schedulers.io());
    }

    public Single<List<StreamStateEntity>> loadStreamStateBatch(
            final List<? extends InfoItem> items) {
        return Single.fromCallable(() -> {
            final List<StreamStateEntity> result = new ArrayList<>(items.size());
            for (final InfoItem item : items) {
                final List<StreamEntity> entities = streamTable
                        .getStream(item.getServiceId(), item.getUrl()).blockingFirst();
                if (entities.isEmpty()) {
                    result.add(null);
                    continue;
                }

                final List<StreamStateEntity> states = streamStateTable
                        .getStateForProfile(profileId, entities.get(0).getUid()).blockingFirst();
                result.add(states.isEmpty() ? null : states.get(0));
            }
            return result;
        }).subscribeOn(Schedulers.io());
    }

    private boolean shouldSyncWatchHistory() {
        return ProfileManager.DEFAULT_PROFILE_ID.equals(profileId);
    }

    private void recordWatchEvent(final long streamId,
                                  final long watchedAtEpochMillis,
                                  final long repeatCount) {
        if (shouldSyncWatchHistory()) {
            historySyncRecorder.recordWatchEvent(streamId, watchedAtEpochMillis, repeatCount);
        }
    }

    private void recordProgress(final long streamId,
                                final long progressMillis,
                                final long updatedAtEpochMillis) {
        if (shouldSyncWatchHistory()) {
            historySyncRecorder.recordProgress(streamId, progressMillis, updatedAtEpochMillis);
        }
    }

    private void recordWatchStreamDelete(final long streamId) {
        if (shouldSyncWatchHistory()) {
            historySyncRecorder.recordWatchStreamDelete(streamId);
        }
    }

    private void recordWatchAllDelete() {
        if (shouldSyncWatchHistory()) {
            historySyncRecorder.recordWatchAllDelete();
        }
    }

    private void recordProgressAllDelete() {
        if (shouldSyncWatchHistory()) {
            historySyncRecorder.recordProgressAllDelete();
        }
    }

    ///////////////////////////////////////////////////////
    // Utility
    ///////////////////////////////////////////////////////

    public Single<Integer> removeOrphanedRecords() {
        return Single.fromCallable(streamTable::deleteOrphans).subscribeOn(Schedulers.io());
    }

}
