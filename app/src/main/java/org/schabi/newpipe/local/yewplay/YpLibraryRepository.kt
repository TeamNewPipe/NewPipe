/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.yewplay

import android.content.Context
import io.github.yewplay.core.data.LibraryRepository
import io.github.yewplay.core.model.YpPlaylist
import io.github.yewplay.core.model.YpVideo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.local.playlist.LocalPlaylistManager
import org.schabi.newpipe.local.playlist.RemotePlaylistManager

/**
 * YewPlay: implements the Library contract on top of NewPipe's database (history and playlists),
 * so the Compose screen never touches the database directly.
 */
class YpLibraryRepository(context: Context) : LibraryRepository {
    private val history = HistoryRecordManager(context)
    private val database = NewPipeDatabase.getInstance(context)
    private val localPlaylists = LocalPlaylistManager(database)
    private val remotePlaylists = RemotePlaylistManager(database)

    override fun continueWatching(limit: Int): Flow<List<YpVideo>> = history.getStreamStatistics()
        .asFlow()
        .map { entries ->
            entries
                .filter { entry ->
                    val durationMillis = entry.streamEntity.duration * 1000
                    entry.progressMillis > 0 &&
                        durationMillis > 0 &&
                        entry.progressMillis < durationMillis * FINISHED_FRACTION
                }
                .sortedByDescending { it.latestAccessDate }
                .take(limit)
                .map { entry ->
                    val stream = entry.streamEntity
                    YpVideo(
                        serviceId = stream.serviceId,
                        url = stream.url,
                        title = stream.title,
                        channelName = stream.uploader,
                        thumbnailUrl = stream.thumbnailUrl,
                        durationSeconds = stream.duration,
                        progressMillis = entry.progressMillis
                    )
                }
        }

    override fun historyCount(): Flow<Int> = history.getStreamStatistics()
        .asFlow()
        .map { it.size }

    override fun playlists(): Flow<List<YpPlaylist>> = combine(
        localPlaylists.getPlaylists().asFlow(),
        remotePlaylists.playlists.asFlow()
    ) { local, remote ->
        local.map {
            YpPlaylist(
                id = it.uid,
                name = it.orderingName.orEmpty(),
                thumbnailUrl = it.thumbnailUrl,
                streamCount = it.streamCount,
                isLocal = true
            )
        } + remote.map {
            YpPlaylist(
                id = it.uid,
                name = it.orderingName.orEmpty(),
                thumbnailUrl = it.thumbnailUrl,
                streamCount = it.streamCount ?: 0,
                isLocal = false,
                serviceId = it.serviceId,
                url = it.url
            )
        }
    }

    private companion object {
        /** Videos watched past this point count as finished. */
        const val FINISHED_FRACTION = 0.95
    }
}
