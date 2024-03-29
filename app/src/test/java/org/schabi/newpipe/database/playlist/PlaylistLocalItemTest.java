package org.schabi.newpipe.database.playlist;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;
import org.schabi.newpipe.local.bookmark.MergedPlaylistManager;

import java.util.ArrayList;
import java.util.List;

public class PlaylistLocalItemTest {
    @Test
    public void emptyPlaylists() {
        final List<PlaylistMetadataEntry> localPlaylists = new ArrayList<>();
        final List<PlaylistRemoteEntity> remotePlaylists = new ArrayList<>();
        final List<PlaylistLocalItem> mergedPlaylists =
                MergedPlaylistManager.merge(localPlaylists, remotePlaylists);

        assertEquals(0, mergedPlaylists.size());
    }

    @Test
    public void onlyLocalPlaylists() {
        final List<PlaylistMetadataEntry> localPlaylists = new ArrayList<>();
        final List<PlaylistRemoteEntity> remotePlaylists = new ArrayList<>();
        localPlaylists.add(new PlaylistMetadataEntry(1, "name1", "", false, -1, 0, 1));
        localPlaylists.add(new PlaylistMetadataEntry(2, "name2", "", false, -1, 1, 1));
        localPlaylists.add(new PlaylistMetadataEntry(3, "name3", "", false, -1, 3, 1));
        final List<PlaylistLocalItem> mergedPlaylists =
                MergedPlaylistManager.merge(localPlaylists, remotePlaylists);

        assertEquals(3, mergedPlaylists.size());
        assertEquals(0, mergedPlaylists.get(0).getDisplayIndex());
        assertEquals(1, mergedPlaylists.get(1).getDisplayIndex());
        assertEquals(3, mergedPlaylists.get(2).getDisplayIndex());
    }

    @Test
    public void onlyRemotePlaylists() {
        final List<PlaylistMetadataEntry> localPlaylists = new ArrayList<>();
        final List<PlaylistRemoteEntity> remotePlaylists = new ArrayList<>();
        remotePlaylists.add(new PlaylistRemoteEntity(
                1, "name1", "url1", "", "", 1, 1L));
        remotePlaylists.add(new PlaylistRemoteEntity(
                2, "name2", "url2", "", "", 2, 1L));
        remotePlaylists.add(new PlaylistRemoteEntity(
                3, "name3", "url3", "", "", 4, 1L));
        final List<PlaylistLocalItem> mergedPlaylists =
                MergedPlaylistManager.merge(localPlaylists, remotePlaylists);

        assertEquals(3, mergedPlaylists.size());
        assertEquals(1, mergedPlaylists.get(0).getDisplayIndex());
        assertEquals(2, mergedPlaylists.get(1).getDisplayIndex());
        assertEquals(4, mergedPlaylists.get(2).getDisplayIndex());
    }

    @Test
    public void sameIndexWithDifferentName() {
        final List<PlaylistMetadataEntry> localPlaylists = new ArrayList<>();
        final List<PlaylistRemoteEntity> remotePlaylists = new ArrayList<>();
        localPlaylists.add(new PlaylistMetadataEntry(1, "name1", "", false, -1, 0, 1));
        localPlaylists.add(new PlaylistMetadataEntry(2, "name2", "", false, -1, 1, 1));
        remotePlaylists.add(new PlaylistRemoteEntity(
                1, "name3", "url1", "", "", 0, 1L));
        remotePlaylists.add(new PlaylistRemoteEntity(
                2, "name4", "url2", "", "", 1, 1L));
        final List<PlaylistLocalItem> mergedPlaylists =
                MergedPlaylistManager.merge(localPlaylists, remotePlaylists);

        assertEquals(4, mergedPlaylists.size());
        assertTrue(mergedPlaylists.get(0) instanceof PlaylistMetadataEntry);
        assertEquals("name1", ((PlaylistMetadataEntry) mergedPlaylists.get(0)).name);
        assertTrue(mergedPlaylists.get(1) instanceof PlaylistRemoteEntity);
        assertEquals("name3", ((PlaylistRemoteEntity) mergedPlaylists.get(1)).getName());
        assertTrue(mergedPlaylists.get(2) instanceof PlaylistMetadataEntry);
        assertEquals("name2", ((PlaylistMetadataEntry) mergedPlaylists.get(2)).name);
        assertTrue(mergedPlaylists.get(3) instanceof PlaylistRemoteEntity);
        assertEquals("name4", ((PlaylistRemoteEntity) mergedPlaylists.get(3)).getName());
    }

    @Test
    public void sameNameWithDifferentIndex() {
        final List<PlaylistMetadataEntry> localPlaylists = new ArrayList<>();
        final List<PlaylistRemoteEntity> remotePlaylists = new ArrayList<>();
        localPlaylists.add(new PlaylistMetadataEntry(1, "name1", "", false, -1, 1, 1));
        localPlaylists.add(new PlaylistMetadataEntry(2, "name2", "", false, -1, 3, 1));
        remotePlaylists.add(new PlaylistRemoteEntity(
                1, "name1", "url1", "", "", 0, 1L));
        remotePlaylists.add(new PlaylistRemoteEntity(
                2, "name2", "url2", "", "", 2, 1L));
        final List<PlaylistLocalItem> mergedPlaylists =
                MergedPlaylistManager.merge(localPlaylists, remotePlaylists);

        assertEquals(4, mergedPlaylists.size());
        assertTrue(mergedPlaylists.get(0) instanceof PlaylistRemoteEntity);
        assertEquals("name1", ((PlaylistRemoteEntity) mergedPlaylists.get(0)).getName());
        assertTrue(mergedPlaylists.get(1) instanceof PlaylistMetadataEntry);
        assertEquals("name1", ((PlaylistMetadataEntry) mergedPlaylists.get(1)).name);
        assertTrue(mergedPlaylists.get(2) instanceof PlaylistRemoteEntity);
        assertEquals("name2", ((PlaylistRemoteEntity) mergedPlaylists.get(2)).getName());
        assertTrue(mergedPlaylists.get(3) instanceof PlaylistMetadataEntry);
        assertEquals("name2", ((PlaylistMetadataEntry) mergedPlaylists.get(3)).name);
    }
}
