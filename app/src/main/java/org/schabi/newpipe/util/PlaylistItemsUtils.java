package org.schabi.newpipe.util;

import org.schabi.newpipe.database.playlist.PlaylistLocalItem;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PlaylistItemsUtils {
    private PlaylistItemsUtils() { }

    public static List<PlaylistLocalItem> merge(
            final List<PlaylistMetadataEntry> localPlaylists,
            final List<PlaylistRemoteEntity> remotePlaylists) {
        final List<PlaylistLocalItem> items = new ArrayList<>(
                localPlaylists.size() + remotePlaylists.size());
        items.addAll(localPlaylists);
        items.addAll(remotePlaylists);

        Collections.sort(items, (left, right) -> {
            String on1 = left.getOrderingName();
            String on2 = right.getOrderingName();
            if (on1 == null && on2 == null) {
                return 0;
            } else if (on1 != null && on2 == null) {
                return -1;
            } else if (on1 == null && on2 != null) {
                return 1;
            } else {
                return on1.compareToIgnoreCase(on2);
            }
        });

        return items;
    }
}
