/*
 * SPDX-FileCopyrightText: 2022-2024 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.local.bookmark.MergedPlaylistManager

class PlaylistLocalItemTest {

    @Test
    fun emptyPlaylists() {
        val localPlaylists = listOf<PlaylistMetadataEntry?>()
        val remotePlaylists = listOf<PlaylistRemoteEntity?>()
        val mergedPlaylists = MergedPlaylistManager.merge(localPlaylists, remotePlaylists)
        assertEquals(0, mergedPlaylists.size)
    }

    @Test
    fun onlyLocalPlaylists() {
        val localPlaylists = listOf<PlaylistMetadataEntry?>(
            PlaylistMetadataEntry(1, "name1", "", 0, false, -1, 1),
            PlaylistMetadataEntry(2, "name2", "", 1, false, -1, 1),
            PlaylistMetadataEntry(3, "name3", "", 3, false, -1, 1)
        )
        val remotePlaylists = listOf<PlaylistRemoteEntity?>()
        val mergedPlaylists = MergedPlaylistManager.merge(localPlaylists, remotePlaylists)

        assertEquals(3, mergedPlaylists.size)
        assertEquals(0L, mergedPlaylists[0]!!.displayIndex)
        assertEquals(1L, mergedPlaylists[1]!!.displayIndex)
        assertEquals(3L, mergedPlaylists[2]!!.displayIndex)
    }

    @Test
    fun onlyRemotePlaylists() {
        val localPlaylists = listOf<PlaylistMetadataEntry?>()
        val remotePlaylists = listOf<PlaylistRemoteEntity?>(
            PlaylistRemoteEntity(1, 1, "name1", "url1", "", "", 1, 1),
            PlaylistRemoteEntity(2, 2, "name2", "url2", "", "", 2, 1),
            PlaylistRemoteEntity(3, 3, "name3", "url3", "", "", 4, 1)
        )
        val mergedPlaylists = MergedPlaylistManager.merge(localPlaylists, remotePlaylists)

        assertEquals(3, mergedPlaylists.size)
        assertEquals(1L, mergedPlaylists[0]!!.displayIndex)
        assertEquals(2L, mergedPlaylists[1]!!.displayIndex)
        assertEquals(4L, mergedPlaylists[2]!!.displayIndex)
    }

    @Test
    fun sameIndexWithDifferentName() {
        val localPlaylists = listOf<PlaylistMetadataEntry?>(
            PlaylistMetadataEntry(1, "name1", "", 0, false, -1, 1),
            PlaylistMetadataEntry(2, "name2", "", 1, false, -1, 1)
        )
        val remotePlaylists = listOf<PlaylistRemoteEntity?>(
            PlaylistRemoteEntity(1, 1, "name3", "url1", "", "", 0, 1),
            PlaylistRemoteEntity(2, 2, "name4", "url2", "", "", 1, 1)
        )
        val mergedPlaylists = MergedPlaylistManager.merge(localPlaylists, remotePlaylists)

        assertEquals(4, mergedPlaylists.size)
        assertTrue(mergedPlaylists[0] is PlaylistMetadataEntry)
        assertEquals("name1", (mergedPlaylists[0] as PlaylistMetadataEntry).orderingName)
        assertTrue(mergedPlaylists[1] is PlaylistRemoteEntity)
        assertEquals("name3", (mergedPlaylists[1] as PlaylistRemoteEntity).orderingName)
        assertTrue(mergedPlaylists[2] is PlaylistMetadataEntry)
        assertEquals("name2", (mergedPlaylists[2] as PlaylistMetadataEntry).orderingName)
        assertTrue(mergedPlaylists[3] is PlaylistRemoteEntity)
        assertEquals("name4", (mergedPlaylists[3] as PlaylistRemoteEntity).orderingName)
    }

    @Test
    fun sameNameWithDifferentIndex() {
        val localPlaylists = listOf<PlaylistMetadataEntry?>(
            PlaylistMetadataEntry(1, "name1", "", 1, false, -1, 1),
            PlaylistMetadataEntry(2, "name2", "", 3, false, -1, 1)
        )
        val remotePlaylists = listOf<PlaylistRemoteEntity?>(
            PlaylistRemoteEntity(1, 1, "name1", "url1", "", "", 0, 1),
            PlaylistRemoteEntity(2, 2, "name2", "url2", "", "", 2, 1)
        )
        val mergedPlaylists = MergedPlaylistManager.merge(localPlaylists, remotePlaylists)

        assertEquals(4, mergedPlaylists.size)
        assertTrue(mergedPlaylists[0] is PlaylistRemoteEntity)
        assertEquals("name1", (mergedPlaylists[0] as PlaylistRemoteEntity).orderingName)
        assertTrue(mergedPlaylists[1] is PlaylistMetadataEntry)
        assertEquals("name1", (mergedPlaylists[1] as PlaylistMetadataEntry).orderingName)
        assertTrue(mergedPlaylists[2] is PlaylistRemoteEntity)
        assertEquals("name2", (mergedPlaylists[2] as PlaylistRemoteEntity).orderingName)
        assertTrue(mergedPlaylists[3] is PlaylistMetadataEntry)
        assertEquals("name2", (mergedPlaylists[3] as PlaylistMetadataEntry).orderingName)
    }
}
