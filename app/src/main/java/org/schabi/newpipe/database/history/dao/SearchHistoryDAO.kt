/*
 * SPDX-FileCopyrightText: 2017-2021 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.history.dao

import androidx.room.Dao
import androidx.room.Query
import io.reactivex.rxjava3.core.Flowable
import org.schabi.newpipe.database.history.model.SearchHistoryEntry

@Dao
interface SearchHistoryDAO : HistoryDAO<SearchHistoryEntry> {

    @get:Query("SELECT * FROM search_history WHERE id = (SELECT MAX(id) FROM search_history)")
    override val latestEntry: SearchHistoryEntry

    @Query("DELETE FROM search_history")
    override fun deleteAll(): Int

    @Query("DELETE FROM search_history WHERE search = :query")
    fun deleteAllWhereQuery(query: String): Int

    @Query("SELECT * FROM search_history ORDER BY creation_date DESC")
    override fun getAll(): Flowable<List<SearchHistoryEntry>>

    @Query("SELECT search FROM search_history GROUP BY search ORDER BY MAX(creation_date) DESC LIMIT :limit")
    fun getUniqueEntries(limit: Int): Flowable<MutableList<String>>

    @Query("SELECT * FROM search_history WHERE service_id = :serviceId ORDER BY creation_date DESC")
    override fun listByService(serviceId: Int): Flowable<List<SearchHistoryEntry>>

    @Query(
        """
        SELECT search FROM search_history WHERE search LIKE :query ||
        '%' GROUP BY search ORDER BY MAX(creation_date) DESC LIMIT :limit
        """
    )
    fun getSimilarEntries(query: String, limit: Int): Flowable<MutableList<String>>
}
