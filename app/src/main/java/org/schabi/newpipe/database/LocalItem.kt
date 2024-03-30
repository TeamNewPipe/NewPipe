package org.schabi.newpipe.database

open interface LocalItem {
    fun getLocalItemType(): LocalItemType
    enum class LocalItemType {
        PLAYLIST_LOCAL_ITEM,
        PLAYLIST_REMOTE_ITEM,
        PLAYLIST_STREAM_ITEM,
        STATISTIC_STREAM_ITEM
    }
}
