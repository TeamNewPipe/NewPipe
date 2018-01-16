package org.schabi.newpipe.info_list.stored;

import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;

import static org.schabi.newpipe.util.Constants.NO_SERVICE_ID;
import static org.schabi.newpipe.util.Constants.NO_URL;

public class LocalPlaylistInfoItem extends PlaylistInfoItem {
    private final long playlistId;

    public LocalPlaylistInfoItem(final long playlistId, final String name) {
        super(NO_SERVICE_ID, NO_URL, name);

        this.playlistId = playlistId;
    }

    public long getPlaylistId() {
        return playlistId;
    }
}
