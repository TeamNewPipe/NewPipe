/*
 * SPDX-FileCopyrightText: 2018-2022 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.history.model

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.ForeignKey.Companion.CASCADE
import androidx.room3.Index
import java.time.OffsetDateTime
import org.schabi.newpipe.database.stream.model.StreamEntity

/**
 * @param streamUid the stream id this history item will refer to
 * @param accessDate the last time the stream was accessed
 * @param repeatCount the total number of views this stream received
 */
@Entity(
    tableName = "stream_history",
    primaryKeys = ["stream_id", "access_date"],
    indices = [Index(value = ["stream_id"])],
    foreignKeys = [
        ForeignKey(
            entity = StreamEntity::class,
            parentColumns = arrayOf("uid"),
            childColumns = arrayOf("stream_id"),
            onDelete = CASCADE,
            onUpdate = CASCADE
        )
    ]
)
data class StreamHistoryEntity(
    @ColumnInfo(name = "stream_id")
    val streamUid: Long,

    @ColumnInfo(name = "access_date")
    var accessDate: OffsetDateTime,

    @ColumnInfo(name = "repeat_count")
    var repeatCount: Long
)
