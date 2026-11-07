/*
 * SPDX-FileCopyrightText: 2018-2023 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.history

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.room3.withWriteTransaction
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.rx3.rxCompletable
import kotlinx.coroutines.rx3.rxMaybe
import kotlinx.coroutines.withContext
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.history.model.SearchHistoryEntry
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.history.model.StreamHistoryEntry
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity
import org.schabi.newpipe.database.stream.StreamStatisticsEntry
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.feed.FeedViewModel
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.util.ExtractorHelper

class HistoryRecordManager(context: Context) {

    private val database = NewPipeDatabase.getInstance(context)
    private val streamTable = database.streamDAO()
    private val streamHistoryTable = database.streamHistoryDAO()
    private val searchHistoryTable = database.searchHistoryDAO()
    private val streamStateTable = database.streamStateDAO()

    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val searchHistoryKey = context.getString(R.string.enable_search_history_key)
    private val streamHistoryKey = context.getString(R.string.enable_watch_history_key)

    private val isSearchHistoryEnabled: Boolean
        get() = sharedPreferences.getBoolean(searchHistoryKey, false)

    private val isStreamHistoryEnabled: Boolean
        get() = sharedPreferences.getBoolean(streamHistoryKey, false)

    // ///////////////////////////////////////////////////////
    // Watch History
    // ///////////////////////////////////////////////////////

    /**
     * Marks a stream item as watched such that it is hidden from the feed if watched videos are
     * hidden. Adds a history entry and updates the stream progress to 100%.
     *
     * @see FeedViewModel.setSaveShowPlayedItems
     * @param info the item to mark as watched
     * @return a Maybe containing the ID of the item if successful
     */
    fun markAsWatched(info: StreamInfoItem): Maybe<Long> {
        if (!isStreamHistoryEnabled) {
            return Maybe.empty()
        }

        val currentTime = OffsetDateTime.now(ZoneOffset.UTC)

        return rxMaybe(Dispatchers.IO) {
            database.withWriteTransaction {
                val streamId: Long
                val duration: Long

                // Duration will not exist if the item was loaded with fast mode, so fetch it
                // if empty
                if (info.duration < 0) {
                    val completeInfo = ExtractorHelper.getStreamInfo(
                        info.serviceId,
                        info.url,
                        false
                    ).subscribeOn(Schedulers.io()).blockingGet()

                    duration = completeInfo.duration
                    streamId = streamTable.upsert(StreamEntity(completeInfo))
                } else {
                    duration = info.duration
                    streamId = streamTable.upsert(StreamEntity(info))
                }

                // Update the stream progress to the full duration of the video
                val entity = StreamStateEntity(
                    streamId,
                    duration * 1000
                )
                streamStateTable.upsert(entity)

                // Add a history entry
                val latestEntry = streamHistoryTable.getLatestEntry(streamId)
                if (latestEntry == null) {
                    // never actually viewed: add history entry but with 0 views
                    streamHistoryTable.insert(StreamHistoryEntity(streamId, currentTime, 0))
                } else {
                    0L
                }
            }
        }
    }

    fun onViewed(info: StreamInfo): Maybe<Long> {
        if (!isStreamHistoryEnabled) {
            return Maybe.empty()
        }

        val currentTime = OffsetDateTime.now(ZoneOffset.UTC)
        return rxMaybe(Dispatchers.IO) {
            database.withWriteTransaction {
                val streamId = streamTable.upsert(StreamEntity(info))
                val latestEntry = streamHistoryTable.getLatestEntry(streamId)

                if (latestEntry != null) {
                    streamHistoryTable.delete(latestEntry)
                    latestEntry.accessDate = currentTime
                    latestEntry.repeatCount = latestEntry.repeatCount + 1
                    streamHistoryTable.insert(latestEntry)
                } else {
                    // just viewed for the first time: set 1 view
                    streamHistoryTable.insert(StreamHistoryEntity(streamId, currentTime, 1))
                }
            }
        }
    }

    fun deleteStreamHistoryAndState(streamId: Long): Completable {
        return Completable.fromAction {
            streamStateTable.deleteState(streamId)
            streamHistoryTable.deleteStreamHistory(streamId)
        }.subscribeOn(Schedulers.io())
    }

    fun deleteWholeStreamHistory(): Single<Int> {
        return Single.fromCallable { streamHistoryTable.deleteAll() }
            .subscribeOn(Schedulers.io())
    }

    fun deleteCompleteStreamStateHistory(): Single<Int> {
        return Single.fromCallable { streamStateTable.deleteAll() }
            .subscribeOn(Schedulers.io())
    }

    fun getStreamHistorySortedById(): Flowable<MutableList<StreamHistoryEntry>> {
        return streamHistoryTable.historySortedById.subscribeOn(Schedulers.io())
    }

    fun getStreamStatistics(): Flowable<MutableList<StreamStatisticsEntry>> {
        return streamHistoryTable.getStatistics().subscribeOn(Schedulers.io())
    }

    // ///////////////////////////////////////////////////////
    // Search History
    // ///////////////////////////////////////////////////////

    fun onSearched(serviceId: Int, search: String): Maybe<Long> {
        if (!isSearchHistoryEnabled) {
            return Maybe.empty()
        }

        val currentTime = OffsetDateTime.now(ZoneOffset.UTC)
        val newEntry = SearchHistoryEntry(currentTime, serviceId, search)

        // TODO: Do this in a viewmodel once UI is migrated to avoid cancellations
        return rxMaybe(Dispatchers.IO) {
            withContext(NonCancellable) {
                database.withWriteTransaction {
                    val latestEntry = searchHistoryTable.latestEntry
                    if (latestEntry != null && latestEntry.hasEqualValues(newEntry)) {
                        latestEntry.creationDate = currentTime
                        searchHistoryTable.update(latestEntry).toLong()
                    } else {
                        searchHistoryTable.insert(newEntry)
                    }
                }
            }
        }
    }

    fun deleteSearchHistory(search: String): Single<Int> {
        return Single.fromCallable { searchHistoryTable.deleteAllWhereQuery(search) }
            .subscribeOn(Schedulers.io())
    }

    fun deleteCompleteSearchHistory(): Single<Int> {
        return Single.fromCallable { searchHistoryTable.deleteAll() }
            .subscribeOn(Schedulers.io())
    }

    fun getRelatedSearches(
        query: String,
        similarQueryLimit: Int,
        uniqueQueryLimit: Int
    ): Flowable<MutableList<String>> {
        return if (query.isNotEmpty()) {
            searchHistoryTable.getSimilarEntries(query, similarQueryLimit)
        } else {
            searchHistoryTable.getUniqueEntries(uniqueQueryLimit)
        }
    }

    // ///////////////////////////////////////////////////////
    // Stream State History
    // ///////////////////////////////////////////////////////

    fun loadStreamState(queueItem: PlayQueueItem): Maybe<StreamStateEntity> {
        return queueItem.stream
            .map { info -> streamTable.upsert(StreamEntity(info)) }
            .flatMapPublisher { streamId -> streamStateTable.getState(streamId) }
            .firstElement()
            .flatMap { list -> if (list.isEmpty()) Maybe.empty() else Maybe.just(list[0]) }
            .filter { state -> state.isValid(queueItem.duration) }
            .subscribeOn(Schedulers.io())
    }

    fun loadStreamState(info: StreamInfo): Maybe<StreamStateEntity> {
        return Single.fromCallable { streamTable.upsert(StreamEntity(info)) }
            .flatMapPublisher { streamId -> streamStateTable.getState(streamId) }
            .firstElement()
            .flatMap { list -> if (list.isEmpty()) Maybe.empty() else Maybe.just(list[0]) }
            .filter { state -> state.isValid(info.duration) }
            .subscribeOn(Schedulers.io())
    }

    fun loadStreamState(info: InfoItem): Maybe<StreamStateEntity> {
        return Maybe.fromCallable {
            streamTable.getStream(info.serviceId.toLong(), info.url)
                .blockingFirst()
                .firstOrNull()
                ?.let { entity ->
                    streamStateTable.getState(entity.uid)
                        .blockingFirst()
                        .firstOrNull()
                }
        }.subscribeOn(Schedulers.io())
    }

    fun loadLocalStreamStateBatch(items: List<LocalItem>): Single<List<StreamStateEntity?>> {
        return Single.fromCallable {
            items.map { item ->
                val streamId = when (item) {
                    is StreamStatisticsEntry -> item.streamId
                    is PlaylistStreamEntity -> item.streamUid
                    is PlaylistStreamEntry -> item.streamId
                    else -> return@map null
                }

                streamStateTable.getState(streamId)
                    .blockingFirst()
                    .firstOrNull()
            }
        }.subscribeOn(Schedulers.io())
    }

    fun saveStreamState(info: StreamInfo, progressMillis: Long): Completable {
        return rxCompletable(Dispatchers.IO) {
            database.withWriteTransaction {
                val streamId = streamTable.upsert(StreamEntity(info))
                val state = StreamStateEntity(streamId, progressMillis)
                if (state.isValid(info.duration)) {
                    streamStateTable.upsert(state)
                }
            }
        }
    }

    // ///////////////////////////////////////////////////////
    // Utility
    // ///////////////////////////////////////////////////////

    fun removeOrphanedRecords(): Single<Int> {
        return Single.fromCallable { streamTable.deleteOrphans() }
            .subscribeOn(Schedulers.io())
    }
}
