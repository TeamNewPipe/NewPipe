/*
 * SPDX-FileCopyrightText: 2023-2024 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.playlist

import androidx.room.ColumnInfo
import org.schabi.newpipe.database.playlist.model.PlaylistEntity

/**
 * This class adds a field to [PlaylistMetadataEntry] that contains an integer representing
 * how many times a specific stream is already contained inside a local playlist. Used to be able
 * to grey out playlists which already contain the current stream in the playlist append dialog.
 * @see org.schabi.newpipe.local.playlist.LocalPlaylistManager.getPlaylistDuplicates
 */
data class PlaylistDuplicatesEntry(
    @ColumnInfo(name = PlaylistEntity.PLAYLIST_ID)
    override val uid: Long,

    @ColumnInfo(name = PlaylistEntity.PLAYLIST_THUMBNAIL_URL)
    override val thumbnailUrl: String?,

    @ColumnInfo(name = PlaylistEntity.PLAYLIST_THUMBNAIL_PERMANENT)
    override val isThumbnailPermanent: Boolean?,

    @ColumnInfo(name = PlaylistEntity.PLAYLIST_THUMBNAIL_STREAM_ID)
    override val thumbnailStreamId: Long?,

    @ColumnInfo(name = PlaylistEntity.PLAYLIST_DISPLAY_INDEX)
    override var displayIndex: Long?,

    @ColumnInfo(name = PLAYLIST_STREAM_COUNT)
    override val streamCount: Long,

    @ColumnInfo(name = PlaylistEntity.PLAYLIST_NAME)
    override val orderingName: String?,

    @ColumnInfo(name = PLAYLIST_TIMES_STREAM_IS_CONTAINED)
    val timesStreamIsContained: Long
) : PlaylistMetadataEntry(
    uid = uid,
    orderingName = orderingName,
    thumbnailUrl = thumbnailUrl,
    isThumbnailPermanent = isThumbnailPermanent,
    thumbnailStreamId = thumbnailStreamId,
    displayIndex = displayIndex,
    streamCount = streamCount
) {
    companion object {
        const val PLAYLIST_TIMES_STREAM_IS_CONTAINED: String = "timesStreamIsContained"
    }
}
