/*
 * SPDX-FileCopyrightText: 2018-2024 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.playlist.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import org.schabi.newpipe.R
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry

@Entity(tableName = PlaylistEntity.Companion.PLAYLIST_TABLE)
data class PlaylistEntity @JvmOverloads constructor(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = PLAYLIST_ID)
    var uid: Long = 0,

    @ColumnInfo(name = PLAYLIST_NAME)
    var name: String?,

    @ColumnInfo(name = PLAYLIST_THUMBNAIL_PERMANENT)
    var isThumbnailPermanent: Boolean,

    @ColumnInfo(name = PLAYLIST_THUMBNAIL_STREAM_ID)
    var thumbnailStreamId: Long,

    @ColumnInfo(name = PLAYLIST_DISPLAY_INDEX)
    var displayIndex: Long
) {

    @Ignore
    constructor(item: PlaylistMetadataEntry) : this(
        uid = item.uid,
        name = item.orderingName,
        isThumbnailPermanent = item.isThumbnailPermanent!!,
        thumbnailStreamId = item.thumbnailStreamId!!,
        displayIndex = item.displayIndex!!,
    )

    companion object {
        @JvmField
        val DEFAULT_THUMBNAIL = "drawable://" + R.drawable.placeholder_thumbnail_playlist

        const val DEFAULT_THUMBNAIL_ID = -1L

        const val PLAYLIST_TABLE = "playlists"
        const val PLAYLIST_ID = "uid"
        const val PLAYLIST_NAME = "name"
        const val PLAYLIST_THUMBNAIL_URL = "thumbnail_url"
        const val PLAYLIST_DISPLAY_INDEX = "display_index"
        const val PLAYLIST_THUMBNAIL_PERMANENT = "is_thumbnail_permanent"
        const val PLAYLIST_THUMBNAIL_STREAM_ID = "thumbnail_stream_id"
    }
}
