package org.schabi.newpipe.local.bookmark;

import org.schabi.newpipe.database.playlist.PlaylistLocalItem;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.local.playlist.RemotePlaylistManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.reactivex.rxjava3.core.Flowable;

/**
 * Takes care of remote and local playlists at once, hence "merged".
 */
public final class MergedPlaylistManager {

    private MergedPlaylistManager() {
    }

    public static Flowable<List<PlaylistLocalItem>> getMergedOrderedPlaylists(
            final LocalPlaylistManager localPlaylistManager,
            final RemotePlaylistManager remotePlaylistManager) {
        return Flowable.combineLatest(
                localPlaylistManager.getPlaylists(),
                remotePlaylistManager.getPlaylists(),
                MergedPlaylistManager::merge
        );
    }

    /**
     * Merge localPlaylists and remotePlaylists by the display index.
     * If two items have the same display index, sort them in {@code CASE_INSENSITIVE_ORDER}.
     *
     * @param localPlaylists  local playlists, already sorted by display index
     * @param remotePlaylists remote playlists, already sorted by display index
     * @return merged playlists
     */
    public static List<PlaylistLocalItem> merge(
            final List<PlaylistMetadataEntry> localPlaylists,
            final List<PlaylistRemoteEntity> remotePlaylists) {

        // This algorithm is similar to the merge operation in merge sort.
        final List<PlaylistLocalItem> result = new ArrayList<>(
                localPlaylists.size() + remotePlaylists.size());
        final List<PlaylistLocalItem> itemsWithSameIndex = new ArrayList<>();

        int i = 0;
        int j = 0;
        while (i < localPlaylists.size()) {
            while (j < remotePlaylists.size()) {
                if (remotePlaylists.get(j).getDisplayIndex()
                        <= localPlaylists.get(i).getDisplayIndex()) {
                    addItem(result, remotePlaylists.get(j), itemsWithSameIndex);
                    j++;
                } else {
                    break;
                }
            }
            addItem(result, localPlaylists.get(i), itemsWithSameIndex);
            i++;
        }
        while (j < remotePlaylists.size()) {
            addItem(result, remotePlaylists.get(j), itemsWithSameIndex);
            j++;
        }
        addItemsWithSameIndex(result, itemsWithSameIndex);

        return result;
    }

    private static void addItem(final List<PlaylistLocalItem> result,
                                final PlaylistLocalItem item,
                                final List<PlaylistLocalItem> itemsWithSameIndex) {
        if (!itemsWithSameIndex.isEmpty()
                && itemsWithSameIndex.get(0).getDisplayIndex() != item.getDisplayIndex()) {
            // The new item has a different display index, add previous items with same
            // index to the result.
            addItemsWithSameIndex(result, itemsWithSameIndex);
            itemsWithSameIndex.clear();
        }
        itemsWithSameIndex.add(item);
    }

    private static void addItemsWithSameIndex(final List<PlaylistLocalItem> result,
                                              final List<PlaylistLocalItem> itemsWithSameIndex) {
        Collections.sort(itemsWithSameIndex,
                Comparator.comparing(PlaylistLocalItem::getOrderingName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        result.addAll(itemsWithSameIndex);
    }
}
