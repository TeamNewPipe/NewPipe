package org.schabi.newpipe.database;

public interface LocalItem {
    LocalItemType getLocalItemType();

    enum LocalItemType {
        PLAYLIST_LOCAL_ITEM,
        PLAYLIST_REMOTE_ITEM,

        PLAYLIST_STREAM_ITEM,
        STATISTIC_STREAM_ITEM,
    }
}
