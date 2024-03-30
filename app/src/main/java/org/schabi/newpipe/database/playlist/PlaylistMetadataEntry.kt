package org.schabi.newpipe.database.playlist

import androidx.room.ColumnInfo
import org.schabi.newpipe.database.LocalItem.LocalItemType
import org.schabi.newpipe.database.playlist.model.PlaylistEntity

open class PlaylistMetadataEntry(@field:ColumnInfo(name = PlaylistEntity.Companion.PLAYLIST_ID) private val uid: Long, @JvmField @field:ColumnInfo(name = PlaylistEntity.Companion.PLAYLIST_NAME) val name: String, @field:ColumnInfo(name = PlaylistEntity.Companion.PLAYLIST_THUMBNAIL_URL) val thumbnailUrl: String,
                                 @field:ColumnInfo(name = PlaylistEntity.Companion.PLAYLIST_THUMBNAIL_PERMANENT) private val isThumbnailPermanent: Boolean, @field:ColumnInfo(name = PlaylistEntity.Companion.PLAYLIST_THUMBNAIL_STREAM_ID) private val thumbnailStreamId: Long,
                                 @field:ColumnInfo(name = PlaylistEntity.Companion.PLAYLIST_DISPLAY_INDEX) private var displayIndex: Long, @field:ColumnInfo(name = PLAYLIST_STREAM_COUNT) val streamCount: Long) : PlaylistLocalItem {
    public override fun getLocalItemType(): LocalItemType {
        return LocalItemType.PLAYLIST_LOCAL_ITEM
    }

    public override fun getOrderingName(): String {
        return name
    }

    fun isThumbnailPermanent(): Boolean {
        return isThumbnailPermanent
    }

    fun getThumbnailStreamId(): Long {
        return thumbnailStreamId
    }

    public override fun getDisplayIndex(): Long {
        return displayIndex
    }

    public override fun getUid(): Long {
        return uid
    }

    public override fun setDisplayIndex(displayIndex: Long) {
        this.displayIndex = displayIndex
    }

    companion object {
        val PLAYLIST_STREAM_COUNT: String = "streamCount"
    }
}
