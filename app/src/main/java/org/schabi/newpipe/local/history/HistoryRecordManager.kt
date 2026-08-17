package org.schabi.newpipe.local.history

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.room.withTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
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
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.util.ExtractorHelper

class HistoryRecordManager(context: Context) {
    private val database = NewPipeDatabase.getInstance(context)
    private val streamTable = database.streamDAO()
    private val streamHistoryTable = database.streamHistoryDAO()
    private val searchHistoryTable = database.searchHistoryDAO()
    private val streamStateTable = database.streamStateDAO()
    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val searchHistoryKey: String = context.getString(R.string.enable_search_history_key)
    private val streamHistoryKey: String = context.getString(R.string.enable_watch_history_key)

    // /////////////////////////////////////////////////////
    // Watch History
    // /////////////////////////////////////////////////////

    /**
     * Marks a stream item as watched such that it is hidden from the feed if watched videos are
     * hidden. Adds a history entry and updates the stream progress to 100%.
     *
     * @param info the item to mark as watched
     * @return the ID of the item if successful
     */
    suspend fun markAsWatched(info: StreamInfoItem): Long? {
        if (!isStreamHistoryEnabled()) {
            return null
        }

        val currentTime = OffsetDateTime.now(ZoneOffset.UTC)
        return database.withTransaction {
            val streamId: Long
            val duration: Long
            // Duration will not exist if the item was loaded with fast mode, so fetch it if empty
            if (info.duration < 0) {
                val completeInfo = ExtractorHelper.getStreamInfo(
                    info.serviceId,
                    info.url,
                    false
                )
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
            streamId
        }
    }

    suspend fun onViewed(info: StreamInfo): Long? {
        if (!isStreamHistoryEnabled()) {
            return null
        }

        val currentTime = OffsetDateTime.now(ZoneOffset.UTC)
        return database.withTransaction {
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
            streamId
        }
    }

    suspend fun deleteStreamHistoryAndState(streamId: Long) {
        streamStateTable.deleteState(streamId)
        streamHistoryTable.deleteStreamHistory(streamId)
    }

    suspend fun deleteWholeStreamHistory(): Int {
        return streamHistoryTable.deleteAll()
    }

    suspend fun deleteCompleteStreamStateHistory(): Int {
        return streamStateTable.deleteAll()
    }

    fun getStreamHistorySortedById(): Flow<List<StreamHistoryEntry>> {
        return streamHistoryTable.getHistorySortedById()
    }

    fun getStreamStatistics(): Flow<List<StreamStatisticsEntry>> {
        return streamHistoryTable.getStatistics()
    }

    private fun isStreamHistoryEnabled(): Boolean {
        return sharedPreferences.getBoolean(streamHistoryKey, true)
    }

    // /////////////////////////////////////////////////////
    // Search History
    // /////////////////////////////////////////////////////

    suspend fun onSearched(serviceId: Int, search: String): Long? {
        if (!isSearchHistoryEnabled()) {
            return null
        }

        val currentTime = OffsetDateTime.now(ZoneOffset.UTC)
        val newEntry = SearchHistoryEntry(currentTime, serviceId, search)

        return database.withTransaction {
            val latestEntry = searchHistoryTable.getLatestEntry()
            if (latestEntry != null && latestEntry.hasEqualValues(newEntry)) {
                latestEntry.creationDate = currentTime
                searchHistoryTable.update(latestEntry).toLong()
            } else {
                searchHistoryTable.insert(newEntry)
            }
        }
    }

    suspend fun deleteSearchHistory(search: String): Int {
        return searchHistoryTable.deleteAllWhereQuery(search)
    }

    suspend fun deleteCompleteSearchHistory(): Int {
        return searchHistoryTable.deleteAll()
    }

    fun getRelatedSearches(
        query: String,
        similarQueryLimit: Int,
        uniqueQueryLimit: Int
    ): Flow<List<String>> {
        return if (query.isNotEmpty()) {
            searchHistoryTable.getSimilarEntries(query, similarQueryLimit)
        } else {
            searchHistoryTable.getUniqueEntries(uniqueQueryLimit)
        }
    }

    private fun isSearchHistoryEnabled(): Boolean {
        return sharedPreferences.getBoolean(searchHistoryKey, true)
    }

    // /////////////////////////////////////////////////////
    // Stream State History
    // /////////////////////////////////////////////////////

    suspend fun loadStreamState(queueItem: PlayQueueItem): StreamStateEntity? {
        val info = queueItem.getStream()
        val streamId = streamTable.upsert(StreamEntity(info))
        val state = streamStateTable.getState(streamId).firstOrNull()?.firstOrNull()
        return if (state != null && state.isValid(queueItem.duration)) state else null
    }

    suspend fun loadStreamState(info: StreamInfo): StreamStateEntity? {
        val streamId = streamTable.upsert(StreamEntity(info))
        val state = streamStateTable.getState(streamId).firstOrNull()?.firstOrNull()
        return if (state != null && state.isValid(info.duration)) state else null
    }

    suspend fun saveStreamState(info: StreamInfo, progressMillis: Long) {
        database.withTransaction {
            val streamId = streamTable.upsert(StreamEntity(info))
            val state = StreamStateEntity(streamId, progressMillis)
            if (state.isValid(info.duration)) {
                streamStateTable.upsert(state)
            }
        }
    }

    suspend fun loadStreamState(info: InfoItem): Array<StreamStateEntity?> {
        val entities = streamTable.getStream(info.serviceId.toLong(), info.url).first()
        if (entities.isEmpty()) {
            return arrayOf(null)
        }
        val states = streamStateTable.getState(entities[0].uid).first()
        return if (states.isEmpty()) arrayOf(null) else arrayOf(states[0])
    }

    suspend fun loadLocalStreamStateBatch(items: List<LocalItem>): List<StreamStateEntity?> {
        val result = ArrayList<StreamStateEntity?>(items.size)
        for (item in items) {
            val streamId = when (item) {
                is StreamStatisticsEntry -> item.streamId

                is PlaylistStreamEntity -> item.streamUid

                is PlaylistStreamEntry -> item.streamId

                else -> {
                    result.add(null)
                    continue
                }
            }
            val states = streamStateTable.getState(streamId).first()
            if (states.isEmpty()) {
                result.add(null)
            } else {
                result.add(states[0])
            }
        }
        return result
    }

    // /////////////////////////////////////////////////////
    // Utility
    // /////////////////////////////////////////////////////

    suspend fun removeOrphanedRecords(): Int {
        return streamTable.deleteOrphans()
    }
}
