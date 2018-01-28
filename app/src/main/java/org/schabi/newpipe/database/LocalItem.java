package org.schabi.newpipe.database;

public interface LocalItem {
    enum LocalItemType {
        PLAYLIST_ITEM,
        PLAYLIST_STREAM_ITEM,
        STATISTIC_STREAM_ITEM
    }

    LocalItemType getLocalItemType();
}
