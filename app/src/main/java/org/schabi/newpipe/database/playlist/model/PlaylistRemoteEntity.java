package org.schabi.newpipe.database.playlist.model;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import org.schabi.newpipe.database.playlist.PlaylistLocalItem;
import org.schabi.newpipe.ui.components.playlist.PlaylistScreenInfo;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.image.ImageStrategy;

import static org.schabi.newpipe.database.LocalItem.LocalItemType.PLAYLIST_REMOTE_ITEM;
import static org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity.REMOTE_PLAYLIST_SERVICE_ID;
import static org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity.REMOTE_PLAYLIST_TABLE;
import static org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity.REMOTE_PLAYLIST_URL;

@Entity(tableName = REMOTE_PLAYLIST_TABLE,
        indices = {
                @Index(value = {REMOTE_PLAYLIST_SERVICE_ID, REMOTE_PLAYLIST_URL}, unique = true)
        })
public class PlaylistRemoteEntity implements PlaylistLocalItem {
    public static final String REMOTE_PLAYLIST_TABLE = "remote_playlists";
    public static final String REMOTE_PLAYLIST_ID = "uid";
    public static final String REMOTE_PLAYLIST_SERVICE_ID = "service_id";
    public static final String REMOTE_PLAYLIST_NAME = "name";
    public static final String REMOTE_PLAYLIST_URL = "url";
    public static final String REMOTE_PLAYLIST_THUMBNAIL_URL = "thumbnail_url";
    public static final String REMOTE_PLAYLIST_UPLOADER_NAME = "uploader";
    public static final String REMOTE_PLAYLIST_DISPLAY_INDEX = "display_index";
    public static final String REMOTE_PLAYLIST_STREAM_COUNT = "stream_count";

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = REMOTE_PLAYLIST_ID)
    private long uid = 0;

    @ColumnInfo(name = REMOTE_PLAYLIST_SERVICE_ID)
    private int serviceId = Constants.NO_SERVICE_ID;

    @ColumnInfo(name = REMOTE_PLAYLIST_NAME)
    private String name;

    @ColumnInfo(name = REMOTE_PLAYLIST_URL)
    private String url;

    @ColumnInfo(name = REMOTE_PLAYLIST_THUMBNAIL_URL)
    private String thumbnailUrl;

    @ColumnInfo(name = REMOTE_PLAYLIST_UPLOADER_NAME)
    private String uploader;

    @ColumnInfo(name = REMOTE_PLAYLIST_DISPLAY_INDEX)
    private long displayIndex = -1; // Make sure the new item is on the top

    @ColumnInfo(name = REMOTE_PLAYLIST_STREAM_COUNT)
    private Long streamCount;

    public PlaylistRemoteEntity(final int serviceId, final String name, final String url,
                                final String thumbnailUrl, final String uploader,
                                final Long streamCount) {
        this.serviceId = serviceId;
        this.name = name;
        this.url = url;
        this.thumbnailUrl = thumbnailUrl;
        this.uploader = uploader;
        this.streamCount = streamCount;
    }

    @Ignore
    public PlaylistRemoteEntity(final int serviceId, final String name, final String url,
                                final String thumbnailUrl, final String uploader,
                                final long displayIndex, final Long streamCount) {
        this.serviceId = serviceId;
        this.name = name;
        this.url = url;
        this.thumbnailUrl = thumbnailUrl;
        this.uploader = uploader;
        this.displayIndex = displayIndex;
        this.streamCount = streamCount;
    }

    @Ignore
    public PlaylistRemoteEntity(final PlaylistScreenInfo info) {
        this(info.getServiceId(), info.getName(), info.getUrl(),
                // use uploader avatar when no thumbnail is available
                ImageStrategy.imageListToDbUrl(info.getThumbnails().isEmpty()
                        ? info.getUploaderAvatars() : info.getThumbnails()),
                info.getUploaderName(), info.getStreamCount());
    }

    @Override
    public long getUid() {
        return uid;
    }

    public void setUid(final long uid) {
        this.uid = uid;
    }

    public int getServiceId() {
        return serviceId;
    }

    public void setServiceId(final int serviceId) {
        this.serviceId = serviceId;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    @Nullable
    @Override
    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(final String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(final String url) {
        this.url = url;
    }

    public String getUploader() {
        return uploader;
    }

    public void setUploader(final String uploader) {
        this.uploader = uploader;
    }

    @Override
    public long getDisplayIndex() {
        return displayIndex;
    }

    @Override
    public void setDisplayIndex(final long displayIndex) {
        this.displayIndex = displayIndex;
    }

    public Long getStreamCount() {
        return streamCount;
    }

    public void setStreamCount(final Long streamCount) {
        this.streamCount = streamCount;
    }

    @Override
    public LocalItemType getLocalItemType() {
        return PLAYLIST_REMOTE_ITEM;
    }

    @Override
    public String getOrderingName() {
        return name;
    }
}
