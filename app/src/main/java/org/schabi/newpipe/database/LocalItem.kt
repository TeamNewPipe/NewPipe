package org.schabi.newpipe.database

/**
 * Represents a generic item that can be stored locally. This can be a playlist, a stream, etc.
 */
interface LocalItem {
    /**
     * The type of local item. Can be null if the type is unknown or not applicable.
     */
    val localItemType: LocalItemType?

    enum class LocalItemType {
        PLAYLIST_LOCAL_ITEM,
        PLAYLIST_REMOTE_ITEM,
        PLAYLIST_STREAM_ITEM,
        STATISTIC_STREAM_ITEM,
    }
}
