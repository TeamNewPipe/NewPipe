/*
 * SPDX-FileCopyrightText: 2018-2020 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.playlist.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.Index
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.playlist.model.PlaylistEntity.Companion.PLAYLIST_ID
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity.Companion.JOIN_INDEX
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity.Companion.JOIN_PLAYLIST_ID
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity.Companion.JOIN_STREAM_ID
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity.Companion.PLAYLIST_STREAM_JOIN_TABLE
import org.schabi.newpipe.database.stream.model.StreamEntity

@Entity(
    tableName = PLAYLIST_STREAM_JOIN_TABLE,
    primaryKeys = [JOIN_PLAYLIST_ID, JOIN_INDEX],
    indices = [
        Index(value = [JOIN_PLAYLIST_ID, JOIN_INDEX], unique = true),
        Index(value = [JOIN_STREAM_ID])
    ],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = arrayOf(PLAYLIST_ID),
            childColumns = arrayOf(JOIN_PLAYLIST_ID),
            onDelete = CASCADE,
            onUpdate = CASCADE,
            deferred = true
        ),
        ForeignKey(
            entity = StreamEntity::class,
            parentColumns = arrayOf(StreamEntity.STREAM_ID),
            childColumns = arrayOf(JOIN_STREAM_ID),
            onDelete = CASCADE,
            onUpdate = CASCADE,
            deferred = true
        )
    ]
)
data class PlaylistStreamEntity(
    @ColumnInfo(name = JOIN_PLAYLIST_ID)
    val playlistUid: Long,

    @ColumnInfo(name = JOIN_STREAM_ID)
    val streamUid: Long,

    @ColumnInfo(name = JOIN_INDEX)
    val index: Int
) : LocalItem {

    override val localItemType: LocalItem.LocalItemType
        get() = LocalItem.LocalItemType.PLAYLIST_STREAM_ITEM

    companion object {
        const val PLAYLIST_STREAM_JOIN_TABLE = "playlist_stream_join"
        const val JOIN_PLAYLIST_ID = "playlist_id"
        const val JOIN_STREAM_ID = "stream_id"
        const val JOIN_INDEX = "join_index"
    }
}
