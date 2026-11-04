/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package io.github.yewplay.core.data

import io.github.yewplay.core.model.YpPlaylist
import io.github.yewplay.core.model.YpVideo
import kotlinx.coroutines.flow.Flow

/** Data of the "Library" screen. */
interface LibraryRepository {
    /** Videos started but not finished, most recent first. */
    fun continueWatching(limit: Int): Flow<List<YpVideo>>

    /** Number of entries in the watch history. */
    fun historyCount(): Flow<Int>

    /** Local and bookmarked playlists. */
    fun playlists(): Flow<List<YpPlaylist>>
}
