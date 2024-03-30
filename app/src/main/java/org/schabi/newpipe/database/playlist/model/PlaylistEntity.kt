package org.schabi.newpipe.database.playlist.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import org.schabi.newpipe.R
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry
import org.schabi.newpipe.database.playlist.model.PlaylistEntity

@Entity(tableName = PlaylistEntity.PLAYLIST_TABLE)
class PlaylistEntity {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = PLAYLIST_ID)
    private var uid: Long = 0

    @ColumnInfo(name = PLAYLIST_NAME)
    private var name: String?

    @ColumnInfo(name = PLAYLIST_THUMBNAIL_PERMANENT)
    private var isThumbnailPermanent: Boolean

    @ColumnInfo(name = PLAYLIST_THUMBNAIL_STREAM_ID)
    private var thumbnailStreamId: Long

    @ColumnInfo(name = PLAYLIST_DISPLAY_INDEX)
    private var displayIndex: Long

    constructor(name: String?, isThumbnailPermanent: Boolean,
                thumbnailStreamId: Long, displayIndex: Long) {
        this.name = name
        this.isThumbnailPermanent = isThumbnailPermanent
        this.thumbnailStreamId = thumbnailStreamId
        this.displayIndex = displayIndex
    }

    @Ignore
    constructor(item: PlaylistMetadataEntry) {
        uid = item.getUid()
        name = item.name
        isThumbnailPermanent = item.isThumbnailPermanent()
        thumbnailStreamId = item.getThumbnailStreamId()
        displayIndex = item.getDisplayIndex()
    }

    fun getUid(): Long {
        return uid
    }

    fun setUid(uid: Long) {
        this.uid = uid
    }

    fun getName(): String? {
        return name
    }

    fun setName(name: String?) {
        this.name = name
    }

    fun getThumbnailStreamId(): Long {
        return thumbnailStreamId
    }

    fun setThumbnailStreamId(thumbnailStreamId: Long) {
        this.thumbnailStreamId = thumbnailStreamId
    }

    fun getIsThumbnailPermanent(): Boolean {
        return isThumbnailPermanent
    }

    fun setIsThumbnailPermanent(isThumbnailSet: Boolean) {
        isThumbnailPermanent = isThumbnailSet
    }

    fun getDisplayIndex(): Long {
        return displayIndex
    }

    fun setDisplayIndex(displayIndex: Long) {
        this.displayIndex = displayIndex
    }

    companion object {
        val DEFAULT_THUMBNAIL: String = ("drawable://"
                + R.drawable.placeholder_thumbnail_playlist)
        val DEFAULT_THUMBNAIL_ID: Long = -1
        val PLAYLIST_TABLE: String = "playlists"
        val PLAYLIST_ID: String = "uid"
        val PLAYLIST_NAME: String = "name"
        val PLAYLIST_THUMBNAIL_URL: String = "thumbnail_url"
        val PLAYLIST_DISPLAY_INDEX: String = "display_index"
        val PLAYLIST_THUMBNAIL_PERMANENT: String = "is_thumbnail_permanent"
        val PLAYLIST_THUMBNAIL_STREAM_ID: String = "thumbnail_stream_id"
    }
}
