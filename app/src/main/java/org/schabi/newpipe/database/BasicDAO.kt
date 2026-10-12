/*
 * SPDX-FileCopyrightText: 2017-2022 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.Update
import io.reactivex.rxjava3.core.Flowable

@Dao
interface BasicDAO<Entity> {

    /* Inserts */
    @Insert
    fun insert(entity: Entity): Long

    @Insert
    fun insertAll(entities: Collection<Entity>): List<Long>

    /* Searches */
    fun getAll(): Flowable<List<Entity>>

    fun listByService(serviceId: Int): Flowable<List<Entity>>

    /* Deletes */
    @Delete
    fun delete(entity: Entity)

    fun deleteAll(): Int

    /* Updates */
    @Update
    fun update(entity: Entity): Int

    @Update
    fun update(entities: Collection<Entity>)
}
