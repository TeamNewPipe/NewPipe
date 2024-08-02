package org.schabi.newpipe.database.playlist;

import org.schabi.newpipe.database.LocalItem;

public interface PlaylistLocalItem extends LocalItem {
    String getOrderingName();

    long getDisplayIndex();

    long getUid();

    void setDisplayIndex(long displayIndex);

    String getThumbnailUrl();
}
