/*
 * SPDX-FileCopyrightText: 2018-2025 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.playlist

import androidx.room.ColumnInfo
import org.schabi.newpipe.database.LocalItem.LocalItemType
import org.schabi.newpipe.database.playlist.model.PlaylistEntity

open class PlaylistMetadataEntry(
    @ColumnInfo(name = PlaylistEntity.PLAYLIST_ID)
    override val uid: Long,

    @ColumnInfo(name = PlaylistEntity.PLAYLIST_NAME)
    override val orderingName: String?,

    @ColumnInfo(name = PlaylistEntity.PLAYLIST_THUMBNAIL_URL)
    override val thumbnailUrl: String?,

    @ColumnInfo(name = PlaylistEntity.PLAYLIST_DISPLAY_INDEX)
    override var displayIndex: Long?,

    @ColumnInfo(name = PlaylistEntity.PLAYLIST_THUMBNAIL_PERMANENT)
    open val isThumbnailPermanent: Boolean?,

    @ColumnInfo(name = PlaylistEntity.PLAYLIST_THUMBNAIL_STREAM_ID)
    open val thumbnailStreamId: Long?,

    @ColumnInfo(name = PLAYLIST_STREAM_COUNT)
    open val streamCount: Long
) : PlaylistLocalItem {

    override val localItemType: LocalItemType
        get() = LocalItemType.PLAYLIST_LOCAL_ITEM

    companion object {
        const val PLAYLIST_STREAM_COUNT: String = "streamCount"
    }
}
