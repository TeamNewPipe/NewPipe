/*
 * SPDX-FileCopyrightText: 2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.block

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.rxjava3.core.Flowable

@Dao
abstract class BlockedChannelDAO {
    @Query(
        """
        SELECT * FROM blocked_channels
        ORDER BY name COLLATE NOCASE ASC, url COLLATE NOCASE ASC
        """
    )
    abstract fun getAll(): Flowable<List<BlockedChannelEntity>>

    @Query("SELECT url FROM blocked_channels")
    abstract fun getBlockedUrls(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun upsert(blockedChannel: BlockedChannelEntity): Long

    @Query("DELETE FROM blocked_channels WHERE url = :url")
    abstract fun deleteByUrl(url: String): Int
}
