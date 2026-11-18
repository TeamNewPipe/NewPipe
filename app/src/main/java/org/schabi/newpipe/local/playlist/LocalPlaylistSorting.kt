/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.playlist

import org.schabi.newpipe.database.playlist.PlaylistStreamEntry

/** View-only sorting; never changes persisted join indices. */
enum class LocalPlaylistSortMode {
    PLAYLIST_ORDER,
    VIEWS_DESC,
    NEWEST_FIRST,
    OLDEST_FIRST,
    SHORTEST_FIRST,
    LONGEST_FIRST;

    companion object {
        @JvmStatic
        fun fromName(name: String?): LocalPlaylistSortMode {
            return entries.firstOrNull { it.name == name } ?: PLAYLIST_ORDER
        }
    }
}

object LocalPlaylistSorting {
    @JvmStatic
    fun sort(
        entries: List<PlaylistStreamEntry>,
        mode: LocalPlaylistSortMode
    ): List<PlaylistStreamEntry> {
        if (mode == LocalPlaylistSortMode.PLAYLIST_ORDER) return entries.toList()
        return entries.withIndex().sortedWith { left, right ->
            val a = left.value.streamEntity
            val b = right.value.streamEntity
            val comparison = when (mode) {
                LocalPlaylistSortMode.PLAYLIST_ORDER -> 0

                LocalPlaylistSortMode.VIEWS_DESC ->
                    compareKnown(a.viewCount, b.viewCount, descending = true)

                LocalPlaylistSortMode.NEWEST_FIRST ->
                    compareKnown(
                        a.uploadDate?.takeUnless { a.hasSyntheticUploadDate },
                        b.uploadDate?.takeUnless { b.hasSyntheticUploadDate },
                        descending = true
                    )

                LocalPlaylistSortMode.OLDEST_FIRST ->
                    compareKnown(
                        a.uploadDate?.takeUnless { a.hasSyntheticUploadDate },
                        b.uploadDate?.takeUnless { b.hasSyntheticUploadDate },
                        descending = false
                    )

                LocalPlaylistSortMode.SHORTEST_FIRST ->
                    compareKnown(
                        a.duration.takeIf { it >= 0 },
                        b.duration.takeIf { it >= 0 },
                        descending = false
                    )

                LocalPlaylistSortMode.LONGEST_FIRST ->
                    compareKnown(
                        a.duration.takeIf { it >= 0 },
                        b.duration.takeIf { it >= 0 },
                        descending = true
                    )
            }
            if (comparison != 0) comparison else left.index.compareTo(right.index)
        }.map { it.value }
    }

    private fun <T : Comparable<T>> compareKnown(
        a: T?,
        b: T?,
        descending: Boolean
    ): Int = when {
        a == null && b == null -> 0
        a == null -> 1
        b == null -> -1
        descending -> b.compareTo(a)
        else -> a.compareTo(b)
    }
}
