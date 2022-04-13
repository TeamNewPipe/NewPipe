package org.schabi.newpipe.database.playlist;

import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public interface PlaylistLocalItem extends LocalItem {
    String getOrderingName();

    long getDisplayIndex();

    static List<PlaylistLocalItem> merge(
            final List<PlaylistMetadataEntry> localPlaylists,
            final List<PlaylistRemoteEntity> remotePlaylists) {

        // Merge localPlaylists and remotePlaylists by displayIndex.
        // If two items have the same displayIndex, sort them in CASE_INSENSITIVE_ORDER.
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
        addItemsWithSameIndex(result, itemsWithSameIndex);

        // If displayIndex does not match actual index, update displayIndex.
        // This may happen when a new list is created with default displayIndex = 0.
        // todo: update displayIndex

        return result;
    }

    static void addItem(final List<PlaylistLocalItem> result, final PlaylistLocalItem item,
                        final List<PlaylistLocalItem> itemsWithSameIndex) {
        if (!itemsWithSameIndex.isEmpty()
                && itemsWithSameIndex.get(0).getDisplayIndex() != item.getDisplayIndex()) {
            // The new item has a different displayIndex,
            // add previous items with same index to the result.
            addItemsWithSameIndex(result, itemsWithSameIndex);
            itemsWithSameIndex.clear();
        }
        itemsWithSameIndex.add(item);
    }

    static void addItemsWithSameIndex(final List<PlaylistLocalItem> result,
                                      final List<PlaylistLocalItem> itemsWithSameIndex) {
        if (itemsWithSameIndex.size() > 1) {
            Collections.sort(itemsWithSameIndex,
                    Comparator.comparing(PlaylistLocalItem::getOrderingName,
                            Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        }
        result.addAll(itemsWithSameIndex);
    }
}
