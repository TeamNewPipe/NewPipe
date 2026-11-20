/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.playlist

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType

class LocalPlaylistSortingTest {
    private fun entry(
        id: Long,
        views: Long? = null,
        duration: Long = -1,
        date: String? = null,
        synthetic: Boolean = false
    ): PlaylistStreamEntry = PlaylistStreamEntry(
        streamEntity = StreamEntity(
            uid = id,
            serviceId = 0,
            url = "https://example.com/watch/$id",
            title = "Video $id",
            streamType = StreamType.VIDEO_STREAM,
            duration = duration,
            uploader = "Channel",
            viewCount = views,
            uploadDate = date?.let(OffsetDateTime::parse),
            isUploadDateApproximation = synthetic
        ),
        progressMillis = 0,
        streamId = id,
        joinIndex = id.toInt()
    )

    @Test
    fun sortsViewsAndDurationWithoutChangingSavedOrder() {
        val original = listOf(
            entry(1, views = 10, duration = 200),
            entry(2, duration = -1),
            entry(3, views = 100, duration = 30),
            entry(4, views = 10, duration = 200)
        )
        fun ids(mode: LocalPlaylistSortMode) = LocalPlaylistSorting.sort(original, mode)
            .map { it.streamId }
        assertEquals(listOf(3L, 1L, 4L, 2L), ids(LocalPlaylistSortMode.VIEWS_DESC))
        assertEquals(listOf(3L, 1L, 4L, 2L), ids(LocalPlaylistSortMode.SHORTEST_FIRST))
        assertEquals(listOf(1L, 4L, 3L, 2L), ids(LocalPlaylistSortMode.LONGEST_FIRST))
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(LocalPlaylistSortMode.PLAYLIST_ORDER))
        assertEquals(listOf(1L, 2L, 3L, 4L), original.map { it.streamId })
    }

    @Test
    fun reversePlaylistOrderShowsLastAddedFirstWithoutChangingJoinIndices() {
        val original = listOf(entry(11), entry(12), entry(13))
        val reversed = LocalPlaylistSorting.sort(original, LocalPlaylistSortMode.REVERSE_PLAYLIST_ORDER)
        assertEquals(listOf(13L, 12L, 11L), reversed.map { it.streamId })
        assertEquals(listOf(11L, 12L, 13L), original.map { it.streamId })
        assertEquals(listOf(11, 12, 13), original.map { it.joinIndex })
    }

    @Test
    fun publicationSortKeepsUnknownAndSyntheticDatesLast() {
        val original = listOf(
            entry(1, date = "2026-01-01T00:00:00Z"),
            entry(2, date = "2026-11-01T00:00:00Z", synthetic = true),
            entry(3, date = "2026-09-01T00:00:00Z"),
            entry(4)
        )
        assertEquals(
            listOf(3L, 1L, 2L, 4L),
            LocalPlaylistSorting.sort(original, LocalPlaylistSortMode.NEWEST_FIRST)
                .map { it.streamId }
        )
        assertEquals(
            listOf(1L, 3L, 2L, 4L),
            LocalPlaylistSorting.sort(original, LocalPlaylistSortMode.OLDEST_FIRST)
                .map { it.streamId }
        )
    }
}
