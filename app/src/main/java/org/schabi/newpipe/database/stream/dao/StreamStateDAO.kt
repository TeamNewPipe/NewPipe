/*
 * SPDX-FileCopyrightText: 2018-2021 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.stream.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import org.schabi.newpipe.database.BasicDAO
import org.schabi.newpipe.database.stream.model.StreamStateEntity

@Dao
interface StreamStateDAO {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(entity: StreamStateEntity): Long

    @Update
    fun update(entity: StreamStateEntity)

    @Query("SELECT * FROM stream_state")
    fun getAll(): Flowable<List<StreamStateEntity>>

    @Query("DELETE FROM stream_state")
    fun deleteAll(): Completable

    @Query("SELECT * FROM stream_state WHERE stream_id = :streamId")
    fun getState(streamId: Long): Maybe<StreamStateEntity>

    @Query("DELETE FROM stream_state WHERE stream_id = :streamId")
    fun deleteState(streamId: Long): Completable

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun silentInsertInternal(streamState: StreamStateEntity)

    @Transaction
    fun upsert(stream: StreamStateEntity) {
        silentInsertInternal(stream)
        update(stream)
    }
}
