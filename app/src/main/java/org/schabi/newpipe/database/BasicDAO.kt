/*
 * SPDX-FileCopyrightText: 2017-2022 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BasicDAO<Entity> {

    /* Inserts */
    @Insert
    suspend fun insert(entity: Entity): Long

    @Insert
    suspend fun insertAll(entities: Collection<Entity>): List<Long>

    /* Searches */
    fun getAll(): Flow<List<Entity>>

    fun listByService(serviceId: Int): Flow<List<Entity>>

    /* Deletes */
    @Delete
    suspend fun delete(entity: Entity)

    @Query("") // This is a placeholder as BasicDAO is generic, but subclasses override it
    suspend fun deleteAll(): Int

    /* Updates */
    @Update
    suspend fun update(entity: Entity): Int

    @Update
    suspend fun update(entities: Collection<Entity>)
}
