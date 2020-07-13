package org.schabi.newpipe.database.playlist;

import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface PlaylistLocalItem extends LocalItem {
    String getOrderingName();

    static List<PlaylistLocalItem> merge(
            final List<PlaylistMetadataEntry> localPlaylists,
            final List<PlaylistRemoteEntity> remotePlaylists) {
        final List<PlaylistLocalItem> items = new ArrayList<>(
                localPlaylists.size() + remotePlaylists.size());
        items.addAll(localPlaylists);
        items.addAll(remotePlaylists);

        Collections.sort(items, (left, right) -> {
            final String on1 = left.getOrderingName();
            final String on2 = right.getOrderingName();
            if (on1 == null) {
                return on2 == null ? 0 : 1;
            } else {
                return on2 == null ? -1 : on1.compareToIgnoreCase(on2);
            }
        });

        return items;
    }
}
