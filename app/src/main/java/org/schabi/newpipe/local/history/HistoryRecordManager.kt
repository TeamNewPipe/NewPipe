package org.schabi.newpipe.local.history

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.MaybeSource
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Action
import io.reactivex.rxjava3.functions.Function
import io.reactivex.rxjava3.functions.Predicate
import io.reactivex.rxjava3.schedulers.Schedulers
import org.reactivestreams.Publisher
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.history.dao.SearchHistoryDAO
import org.schabi.newpipe.database.history.dao.StreamHistoryDAO
import org.schabi.newpipe.database.history.model.SearchHistoryEntry
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.history.model.StreamHistoryEntry
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.database.stream.dao.StreamDAO
import org.schabi.newpipe.database.stream.dao.StreamStateDAO
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.util.ExtractorHelper
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.Callable

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
class HistoryRecordManager(context: Context?) {
    private val database: AppDatabase
    private val streamTable: StreamDAO?
    private val streamHistoryTable: StreamHistoryDAO?
    private val searchHistoryTable: SearchHistoryDAO?
    private val streamStateTable: StreamStateDAO?
    private val sharedPreferences: SharedPreferences
    private val searchHistoryKey: String
    private val streamHistoryKey: String

    init {
        database = NewPipeDatabase.getInstance((context)!!)
        streamTable = database.streamDAO()
        streamHistoryTable = database.streamHistoryDAO()
        searchHistoryTable = database.searchHistoryDAO()
        streamStateTable = database.streamStateDAO()
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences((context)!!)
        searchHistoryKey = context!!.getString(R.string.enable_search_history_key)
        streamHistoryKey = context.getString(R.string.enable_watch_history_key)
    }
    ///////////////////////////////////////////////////////
    // Watch History
    ///////////////////////////////////////////////////////
    /**
     * Marks a stream item as watched such that it is hidden from the feed if watched videos are
     * hidden. Adds a history entry and updates the stream progress to 100%.
     *
     * @see FeedViewModel.setSaveShowPlayedItems
     *
     * @param info the item to mark as watched
     * @return a Maybe containing the ID of the item if successful
     */
    fun markAsWatched(info: StreamInfoItem): Maybe<Long?> {
        if (!isStreamHistoryEnabled) {
            return Maybe.empty()
        }
        val currentTime: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)
        return Maybe.fromCallable<Long?>(Callable<Long?>({
            database.runInTransaction<Long>(Callable<Long>({
                val streamId: Long
                val duration: Long
                // Duration will not exist if the item was loaded with fast mode, so fetch it if empty
                if (info.getDuration() < 0) {
                    val completeInfo: StreamInfo = ExtractorHelper.getStreamInfo(
                            info.getServiceId(),
                            info.getUrl(),
                            false
                    )
                            .subscribeOn(Schedulers.io())
                            .blockingGet()
                    duration = completeInfo.getDuration()
                    streamId = streamTable!!.upsert(StreamEntity(completeInfo))
                } else {
                    duration = info.getDuration()
                    streamId = streamTable!!.upsert(StreamEntity(info))
                }

                // Update the stream progress to the full duration of the video
                val entity: StreamStateEntity = StreamStateEntity(
                        streamId,
                        duration * 1000
                )
                streamStateTable!!.upsert(entity)

                // Add a history entry
                val latestEntry: StreamHistoryEntity? = streamHistoryTable!!.getLatestEntry(streamId)
                if (latestEntry == null) {
                    // never actually viewed: add history entry but with 0 views
                    return@runInTransaction streamHistoryTable.insert(StreamHistoryEntity(streamId, currentTime, 0))
                } else {
                    return@runInTransaction 0L
                }
            }))
        })).subscribeOn(Schedulers.io())
    }

    fun onViewed(info: StreamInfo?): Maybe<Long?> {
        if (!isStreamHistoryEnabled) {
            return Maybe.empty()
        }
        val currentTime: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)
        return Maybe.fromCallable<Long?>(Callable<Long?>({
            database.runInTransaction<Long>(Callable<Long>({
                val streamId: Long = streamTable!!.upsert(StreamEntity((info)!!))
                val latestEntry: StreamHistoryEntity? = streamHistoryTable!!.getLatestEntry(streamId)
                if (latestEntry != null) {
                    streamHistoryTable.delete(latestEntry)
                    latestEntry.setAccessDate(currentTime)
                    latestEntry.setRepeatCount(latestEntry.getRepeatCount() + 1)
                    return@runInTransaction streamHistoryTable.insert(latestEntry)
                } else {
                    // just viewed for the first time: set 1 view
                    return@runInTransaction streamHistoryTable.insert(StreamHistoryEntity(streamId, currentTime, 1))
                }
            }))
        })).subscribeOn(Schedulers.io())
    }

    fun deleteStreamHistoryAndState(streamId: Long): Completable {
        return Completable.fromAction(Action({
            streamStateTable!!.deleteState(streamId)
            streamHistoryTable!!.deleteStreamHistory(streamId)
        })).subscribeOn(Schedulers.io())
    }

    fun deleteWholeStreamHistory(): Single<Int?> {
        return Single.fromCallable(Callable({ streamHistoryTable!!.deleteAll() }))
                .subscribeOn(Schedulers.io())
    }

    fun deleteCompleteStreamStateHistory(): Single<Int?> {
        return Single.fromCallable(Callable({ streamStateTable!!.deleteAll() }))
                .subscribeOn(Schedulers.io())
    }

    val streamHistorySortedById: Flowable<List<StreamHistoryEntry?>?>
        get() {
            return streamHistoryTable!!.getHistorySortedById().subscribeOn(Schedulers.io())
        }
    val streamStatistics: Flowable<List<StreamStatisticsEntry?>?>
        get() {
            return streamHistoryTable!!.getStatistics().subscribeOn(Schedulers.io())
        }
    private val isStreamHistoryEnabled: Boolean
        private get() {
            return sharedPreferences.getBoolean(streamHistoryKey, false)
        }

    ///////////////////////////////////////////////////////
    // Search History
    ///////////////////////////////////////////////////////
    fun onSearched(serviceId: Int, search: String?): Maybe<Long?> {
        if (!isSearchHistoryEnabled) {
            return Maybe.empty()
        }
        val currentTime: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)
        val newEntry: SearchHistoryEntry = SearchHistoryEntry(currentTime, serviceId, search)
        return Maybe.fromCallable<Long?>(Callable<Long?>({
            database.runInTransaction<Long>(Callable<Long>({
                val latestEntry: SearchHistoryEntry? = searchHistoryTable!!.getLatestEntry()
                if (latestEntry != null && latestEntry.hasEqualValues(newEntry)) {
                    latestEntry.creationDate = currentTime
                    return@runInTransaction searchHistoryTable.update(latestEntry).toLong()
                } else {
                    return@runInTransaction searchHistoryTable.insert(newEntry)
                }
            }))
        })).subscribeOn(Schedulers.io())
    }

    fun deleteSearchHistory(search: String?): Single<Int?> {
        return Single.fromCallable(Callable({ searchHistoryTable!!.deleteAllWhereQuery(search) }))
                .subscribeOn(Schedulers.io())
    }

    fun deleteCompleteSearchHistory(): Single<Int?> {
        return Single.fromCallable(Callable({ searchHistoryTable!!.deleteAll() }))
                .subscribeOn(Schedulers.io())
    }

    fun getRelatedSearches(query: String,
                           similarQueryLimit: Int,
                           uniqueQueryLimit: Int): Flowable<List<String?>?>? {
        return if (query.length > 0) searchHistoryTable!!.getSimilarEntries(query, similarQueryLimit) else searchHistoryTable!!.getUniqueEntries(uniqueQueryLimit)
    }

    private val isSearchHistoryEnabled: Boolean
        private get() {
            return sharedPreferences.getBoolean(searchHistoryKey, false)
        }

    ///////////////////////////////////////////////////////
    // Stream State History
    ///////////////////////////////////////////////////////
    fun loadStreamState(queueItem: PlayQueueItem?): Maybe<StreamStateEntity?> {
        return queueItem.getStream()
                .map(Function({ info: StreamInfo? -> streamTable!!.upsert(StreamEntity((info)!!)) }))
                .flatMapPublisher<List<StreamStateEntity?>?>(Function<Long, Publisher<out List<StreamStateEntity?>?>?>({ streamId: Long -> streamStateTable!!.getState(streamId) }))
                .firstElement()
                .flatMap(Function<List<StreamStateEntity?>?, MaybeSource<out StreamStateEntity?>>({ list: List<StreamStateEntity?>? -> if (list!!.isEmpty()) Maybe.empty<StreamStateEntity?>() else Maybe.just<StreamStateEntity?>(list.get(0)) }))
                .filter(Predicate({ state: StreamStateEntity? -> state!!.isValid(queueItem.getDuration()) }))
                .subscribeOn(Schedulers.io())
    }

    fun loadStreamState(info: StreamInfo): Maybe<StreamStateEntity?> {
        return Single.fromCallable(Callable({ streamTable!!.upsert(StreamEntity(info)) }))
                .flatMapPublisher<List<StreamStateEntity?>?>(Function<Long, Publisher<out List<StreamStateEntity?>?>?>({ streamId: Long -> streamStateTable!!.getState(streamId) }))
                .firstElement()
                .flatMap(Function<List<StreamStateEntity?>?, MaybeSource<out StreamStateEntity?>>({ list: List<StreamStateEntity?>? -> if (list!!.isEmpty()) Maybe.empty<StreamStateEntity?>() else Maybe.just<StreamStateEntity?>(list.get(0)) }))
                .filter(Predicate({ state: StreamStateEntity? -> state!!.isValid(info.getDuration()) }))
                .subscribeOn(Schedulers.io())
    }

    fun saveStreamState(info: StreamInfo, progressMillis: Long): Completable {
        return Completable.fromAction(Action({
            database.runInTransaction(Runnable({
                val streamId: Long = streamTable!!.upsert(StreamEntity(info))
                val state: StreamStateEntity = StreamStateEntity(streamId, progressMillis)
                if (state.isValid(info.getDuration())) {
                    streamStateTable!!.upsert(state)
                }
            }))
        })).subscribeOn(Schedulers.io())
    }

    fun loadStreamState(info: InfoItem): Single<Array<StreamStateEntity?>> {
        return Single.fromCallable<Array<StreamStateEntity?>>(Callable<Array<StreamStateEntity?>>({
            val entities: List<StreamEntity> = streamTable
                    .getStream(info.getServiceId().toLong(), info.getUrl()).blockingFirst()
            if (entities.isEmpty()) {
                return@fromCallable arrayOf<StreamStateEntity?>(null)
            }
            val states: List<StreamStateEntity?>? = streamStateTable
                    .getState(entities.get(0).uid).blockingFirst()
            if (states!!.isEmpty()) {
                return@fromCallable arrayOf<StreamStateEntity?>(null)
            }
            arrayOf<StreamStateEntity?>(states.get(0))
        })).subscribeOn(Schedulers.io())
    }

    fun loadLocalStreamStateBatch(
            items: List<LocalItem?>?): Single<List<StreamStateEntity?>> {
        return Single.fromCallable(Callable<List<StreamStateEntity?>>({
            val result: MutableList<StreamStateEntity?> = ArrayList(items!!.size)
            for (item: LocalItem? in items) {
                val streamId: Long
                if (item is StreamStatisticsEntry) {
                    streamId = item.streamId
                } else if (item is PlaylistStreamEntity) {
                    streamId = (item as PlaylistStreamEntity).getStreamUid()
                } else if (item is PlaylistStreamEntry) {
                    streamId = item.streamId
                } else {
                    result.add(null)
                    continue
                }
                val states: List<StreamStateEntity?>? = streamStateTable!!.getState(streamId)
                        .blockingFirst()
                if (states!!.isEmpty()) {
                    result.add(null)
                } else {
                    result.add(states.get(0))
                }
            }
            result
        })).subscribeOn(Schedulers.io())
    }

    ///////////////////////////////////////////////////////
    // Utility
    ///////////////////////////////////////////////////////
    fun removeOrphanedRecords(): Single<Int?> {
        return Single.fromCallable(Callable({ streamTable!!.deleteOrphans() })).subscribeOn(Schedulers.io())
    }
}
