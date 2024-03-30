package org.schabi.newpipe.database.playlist.model

import android.text.TextUtils
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import org.schabi.newpipe.database.LocalItem.LocalItemType
import org.schabi.newpipe.database.playlist.PlaylistLocalItem
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.util.image.ImageStrategy

@Entity(tableName = PlaylistRemoteEntity.REMOTE_PLAYLIST_TABLE, indices = [Index(value = [PlaylistRemoteEntity.REMOTE_PLAYLIST_SERVICE_ID, PlaylistRemoteEntity.REMOTE_PLAYLIST_URL], unique = true)])
class PlaylistRemoteEntity : PlaylistLocalItem {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = REMOTE_PLAYLIST_ID)
    private var uid: Long = 0

    @ColumnInfo(name = REMOTE_PLAYLIST_SERVICE_ID)
    private var serviceId: Int = NO_SERVICE_ID

    @ColumnInfo(name = REMOTE_PLAYLIST_NAME)
    private var name: String

    @ColumnInfo(name = REMOTE_PLAYLIST_URL)
    private var url: String

    @ColumnInfo(name = REMOTE_PLAYLIST_THUMBNAIL_URL)
    private var thumbnailUrl: String?

    @ColumnInfo(name = REMOTE_PLAYLIST_UPLOADER_NAME)
    private var uploader: String

    @ColumnInfo(name = REMOTE_PLAYLIST_DISPLAY_INDEX)
    private var displayIndex: Long = -1 // Make sure the new item is on the top

    @ColumnInfo(name = REMOTE_PLAYLIST_STREAM_COUNT)
    private var streamCount: Long

    constructor(serviceId: Int, name: String, url: String,
                thumbnailUrl: String?, uploader: String,
                streamCount: Long) {
        this.serviceId = serviceId
        this.name = name
        this.url = url
        this.thumbnailUrl = thumbnailUrl
        this.uploader = uploader
        this.streamCount = streamCount
    }

    @Ignore
    constructor(serviceId: Int, name: String, url: String,
                thumbnailUrl: String?, uploader: String,
                displayIndex: Long, streamCount: Long) {
        this.serviceId = serviceId
        this.name = name
        this.url = url
        this.thumbnailUrl = thumbnailUrl
        this.uploader = uploader
        this.displayIndex = displayIndex
        this.streamCount = streamCount
    }

    @Ignore
    constructor(info: PlaylistInfo) : this(info.getServiceId(), info.getName(), info.getUrl(),  // use uploader avatar when no thumbnail is available
            ImageStrategy.imageListToDbUrl(if (info.getThumbnails().isEmpty()) info.getUploaderAvatars() else info.getThumbnails()),
            info.getUploaderName(), info.getStreamCount())

    @Ignore
    fun isIdenticalTo(info: PlaylistInfo): Boolean {
        /*
         * Returns boolean comparing the online playlist and the local copy.
         * (False if info changed such as playlist name or track count)
         */
        return ((getServiceId() == info.getServiceId()
                ) && (getStreamCount() == info.getStreamCount()
                ) && TextUtils.equals(getName(), info.getName())
                && TextUtils.equals(getUrl(), info.getUrl()) // we want to update the local playlist data even when either the remote thumbnail
                // URL changes, or the preferred image quality setting is changed by the user
                && TextUtils.equals(getThumbnailUrl(),
                ImageStrategy.imageListToDbUrl(info.getThumbnails()))
                && TextUtils.equals(getUploader(), info.getUploaderName()))
    }

    public override fun getUid(): Long {
        return uid
    }

    fun setUid(uid: Long) {
        this.uid = uid
    }

    fun getServiceId(): Int {
        return serviceId
    }

    fun setServiceId(serviceId: Int) {
        this.serviceId = serviceId
    }

    fun getName(): String {
        return name
    }

    fun setName(name: String) {
        this.name = name
    }

    fun getThumbnailUrl(): String? {
        return thumbnailUrl
    }

    fun setThumbnailUrl(thumbnailUrl: String?) {
        this.thumbnailUrl = thumbnailUrl
    }

    fun getUrl(): String {
        return url
    }

    fun setUrl(url: String) {
        this.url = url
    }

    fun getUploader(): String {
        return uploader
    }

    fun setUploader(uploader: String) {
        this.uploader = uploader
    }

    public override fun getDisplayIndex(): Long {
        return displayIndex
    }

    public override fun setDisplayIndex(displayIndex: Long) {
        this.displayIndex = displayIndex
    }

    fun getStreamCount(): Long {
        return streamCount
    }

    fun setStreamCount(streamCount: Long) {
        this.streamCount = streamCount
    }

    public override fun getLocalItemType(): LocalItemType {
        return LocalItemType.PLAYLIST_REMOTE_ITEM
    }

    public override fun getOrderingName(): String {
        return name
    }

    companion object {
        val REMOTE_PLAYLIST_TABLE: String = "remote_playlists"
        val REMOTE_PLAYLIST_ID: String = "uid"
        val REMOTE_PLAYLIST_SERVICE_ID: String = "service_id"
        val REMOTE_PLAYLIST_NAME: String = "name"
        val REMOTE_PLAYLIST_URL: String = "url"
        val REMOTE_PLAYLIST_THUMBNAIL_URL: String = "thumbnail_url"
        val REMOTE_PLAYLIST_UPLOADER_NAME: String = "uploader"
        val REMOTE_PLAYLIST_DISPLAY_INDEX: String = "display_index"
        val REMOTE_PLAYLIST_STREAM_COUNT: String = "stream_count"
    }
}
