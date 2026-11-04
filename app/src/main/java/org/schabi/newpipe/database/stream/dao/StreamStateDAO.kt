/*
 * SPDX-FileCopyrightText: 2018-2021 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.stream.dao

import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import androidx.room3.rxjava3.RxDaoReturnTypeConverters
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import org.schabi.newpipe.database.BasicDAO
import org.schabi.newpipe.database.stream.model.StreamStateEntity

@Dao
@DaoReturnTypeConverters(RxDaoReturnTypeConverters::class)
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
