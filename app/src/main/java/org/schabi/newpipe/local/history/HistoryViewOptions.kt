/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.history

import java.time.LocalDate
import java.util.Locale
import org.schabi.newpipe.database.stream.StreamStatisticsEntry

/** Pure history presentation rules; never changes persisted history or playback progress. */
object HistoryViewOptions {
    @JvmStatic
    fun inDateRange(date: LocalDate, fromEpochDay: Long, toEpochDay: Long): Boolean {
        val day = date.toEpochDay()
        return day >= fromEpochDay && day <= toEpochDay
    }

    @JvmStatic
    fun sort(items: List<StreamStatisticsEntry>, mode: String): List<StreamStatisticsEntry> {
        return when (mode) {
            "EARLIEST_PLAYED" ->
                items.sortedWith(
                    compareBy<StreamStatisticsEntry> { it.latestAccessDate }
                        .thenBy { it.streamId }
                )

            "MOST_PLAYED" ->
                items.sortedWith(
                    compareByDescending<StreamStatisticsEntry> { it.watchCount }
                        .thenByDescending { it.latestAccessDate }
                )

            "LEAST_PLAYED" ->
                items.sortedWith(
                    compareBy<StreamStatisticsEntry> { it.watchCount }
                        .thenByDescending { it.latestAccessDate }
                )

            "TITLE" ->
                items.sortedWith(
                    compareBy<StreamStatisticsEntry> {
                        it.streamEntity.title.lowercase(Locale.ROOT)
                    }.thenByDescending { it.latestAccessDate }
                )

            else -> items
        }
    }
}
