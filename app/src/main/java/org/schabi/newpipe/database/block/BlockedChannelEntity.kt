/*
 * SPDX-FileCopyrightText: 2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.block

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = BlockedChannelEntity.BLOCKED_CHANNEL_TABLE,
    indices = [Index(value = [BlockedChannelEntity.BLOCKED_CHANNEL_URL], unique = true)]
)
data class BlockedChannelEntity(
    @PrimaryKey(autoGenerate = true)
    val uid: Long = 0,

    @ColumnInfo(name = BLOCKED_CHANNEL_URL)
    val url: String,

    @ColumnInfo(name = BLOCKED_CHANNEL_NAME)
    val name: String
) {
    companion object {
        const val BLOCKED_CHANNEL_TABLE = "blocked_channels"
        const val BLOCKED_CHANNEL_URL = "url"
        const val BLOCKED_CHANNEL_NAME = "name"
    }
}
