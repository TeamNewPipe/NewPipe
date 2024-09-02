package org.schabi.newpipe.database.playlist

import org.schabi.newpipe.database.LocalItem

/**
 * Represents a playlist item stored locally.
 */
interface PlaylistLocalItem : LocalItem {
    /**
     * The name used for ordering this item within the playlist. Can be null.
     */
    val orderingName: String?

    /**
     * The index used to display this item within the playlist.
     */
    var displayIndex: Long

    /**
     * The unique identifier for this playlist item.
     */
    val uid: Long

    /**
     * The URL of the thumbnail image for this playlist item. Can be null.
     */
    val thumbnailUrl: String?
}
