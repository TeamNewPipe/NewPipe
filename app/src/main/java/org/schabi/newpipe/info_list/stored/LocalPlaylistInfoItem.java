package org.schabi.newpipe.info_list.stored;

import org.schabi.newpipe.extractor.InfoItem;

import static org.schabi.newpipe.util.Constants.NO_SERVICE_ID;
import static org.schabi.newpipe.util.Constants.NO_URL;

public class LocalPlaylistInfoItem extends InfoItem {
    private final long playlistId;
    private long streamCount;

    public LocalPlaylistInfoItem(final long playlistId, final String name) {
        super(InfoType.PLAYLIST, NO_SERVICE_ID, NO_URL, name);

        this.playlistId = playlistId;
        this.streamCount = streamCount;
    }

    public long getPlaylistId() {
        return playlistId;
    }

    public long getStreamCount() {
        return streamCount;
    }

    public void setStreamCount(long streamCount) {
        this.streamCount = streamCount;
    }
}
