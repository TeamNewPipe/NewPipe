package org.schabi.newpipe.database.playlist

import org.junit.Assert
import org.junit.Test
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.local.bookmark.MergedPlaylistManager.merge

class PlaylistLocalItemTest {
    @Test
    fun emptyPlaylists() {
        val localPlaylists: List<PlaylistMetadataEntry> = ArrayList()
        val remotePlaylists: List<PlaylistRemoteEntity> = ArrayList()
        val mergedPlaylists = merge(localPlaylists, remotePlaylists)
        Assert.assertEquals(0, mergedPlaylists.size.toLong())
    }

    @Test
    fun onlyLocalPlaylists() {
        val localPlaylists: MutableList<PlaylistMetadataEntry> = ArrayList()
        val remotePlaylists: List<PlaylistRemoteEntity> = ArrayList()
        localPlaylists.add(PlaylistMetadataEntry(1, "name1", "", false, -1, 0, 1))
        localPlaylists.add(PlaylistMetadataEntry(2, "name2", "", false, -1, 1, 1))
        localPlaylists.add(PlaylistMetadataEntry(3, "name3", "", false, -1, 3, 1))
        val mergedPlaylists = merge(localPlaylists, remotePlaylists)
        Assert.assertEquals(3, mergedPlaylists.size.toLong())
        Assert.assertEquals(0, mergedPlaylists[0].getDisplayIndex())
        Assert.assertEquals(1, mergedPlaylists[1].getDisplayIndex())
        Assert.assertEquals(3, mergedPlaylists[2].getDisplayIndex())
    }

    @Test
    fun onlyRemotePlaylists() {
        val localPlaylists: List<PlaylistMetadataEntry> = ArrayList()
        val remotePlaylists: MutableList<PlaylistRemoteEntity> = ArrayList()
        remotePlaylists.add(PlaylistRemoteEntity(
                1, "name1", "url1", "", "", 1, 1L))
        remotePlaylists.add(PlaylistRemoteEntity(
                2, "name2", "url2", "", "", 2, 1L))
        remotePlaylists.add(PlaylistRemoteEntity(
                3, "name3", "url3", "", "", 4, 1L))
        val mergedPlaylists = merge(localPlaylists, remotePlaylists)
        Assert.assertEquals(3, mergedPlaylists.size.toLong())
        Assert.assertEquals(1, mergedPlaylists[0].getDisplayIndex())
        Assert.assertEquals(2, mergedPlaylists[1].getDisplayIndex())
        Assert.assertEquals(4, mergedPlaylists[2].getDisplayIndex())
    }

    @Test
    fun sameIndexWithDifferentName() {
        val localPlaylists: MutableList<PlaylistMetadataEntry> = ArrayList()
        val remotePlaylists: MutableList<PlaylistRemoteEntity> = ArrayList()
        localPlaylists.add(PlaylistMetadataEntry(1, "name1", "", false, -1, 0, 1))
        localPlaylists.add(PlaylistMetadataEntry(2, "name2", "", false, -1, 1, 1))
        remotePlaylists.add(PlaylistRemoteEntity(
                1, "name3", "url1", "", "", 0, 1L))
        remotePlaylists.add(PlaylistRemoteEntity(
                2, "name4", "url2", "", "", 1, 1L))
        val mergedPlaylists = merge(localPlaylists, remotePlaylists)
        Assert.assertEquals(4, mergedPlaylists.size.toLong())
        Assert.assertTrue(mergedPlaylists[0] is PlaylistMetadataEntry)
        Assert.assertEquals("name1", (mergedPlaylists[0] as PlaylistMetadataEntry).name)
        Assert.assertTrue(mergedPlaylists[1] is PlaylistRemoteEntity)
        Assert.assertEquals("name3", (mergedPlaylists[1] as PlaylistRemoteEntity).getName())
        Assert.assertTrue(mergedPlaylists[2] is PlaylistMetadataEntry)
        Assert.assertEquals("name2", (mergedPlaylists[2] as PlaylistMetadataEntry).name)
        Assert.assertTrue(mergedPlaylists[3] is PlaylistRemoteEntity)
        Assert.assertEquals("name4", (mergedPlaylists[3] as PlaylistRemoteEntity).getName())
    }

    @Test
    fun sameNameWithDifferentIndex() {
        val localPlaylists: MutableList<PlaylistMetadataEntry> = ArrayList()
        val remotePlaylists: MutableList<PlaylistRemoteEntity> = ArrayList()
        localPlaylists.add(PlaylistMetadataEntry(1, "name1", "", false, -1, 1, 1))
        localPlaylists.add(PlaylistMetadataEntry(2, "name2", "", false, -1, 3, 1))
        remotePlaylists.add(PlaylistRemoteEntity(
                1, "name1", "url1", "", "", 0, 1L))
        remotePlaylists.add(PlaylistRemoteEntity(
                2, "name2", "url2", "", "", 2, 1L))
        val mergedPlaylists = merge(localPlaylists, remotePlaylists)
        Assert.assertEquals(4, mergedPlaylists.size.toLong())
        Assert.assertTrue(mergedPlaylists[0] is PlaylistRemoteEntity)
        Assert.assertEquals("name1", (mergedPlaylists[0] as PlaylistRemoteEntity).getName())
        Assert.assertTrue(mergedPlaylists[1] is PlaylistMetadataEntry)
        Assert.assertEquals("name1", (mergedPlaylists[1] as PlaylistMetadataEntry).name)
        Assert.assertTrue(mergedPlaylists[2] is PlaylistRemoteEntity)
        Assert.assertEquals("name2", (mergedPlaylists[2] as PlaylistRemoteEntity).getName())
        Assert.assertTrue(mergedPlaylists[3] is PlaylistMetadataEntry)
        Assert.assertEquals("name2", (mergedPlaylists[3] as PlaylistMetadataEntry).name)
    }
}
