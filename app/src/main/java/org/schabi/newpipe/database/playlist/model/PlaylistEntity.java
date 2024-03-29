package org.schabi.newpipe.database.playlist.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import static org.schabi.newpipe.database.playlist.model.PlaylistEntity.PLAYLIST_TABLE;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;

@Entity(tableName = PLAYLIST_TABLE)
public class PlaylistEntity {

    public static final String DEFAULT_THUMBNAIL = "drawable://"
            + R.drawable.placeholder_thumbnail_playlist;
    public static final long DEFAULT_THUMBNAIL_ID = -1;

    public static final String PLAYLIST_TABLE = "playlists";
    public static final String PLAYLIST_ID = "uid";
    public static final String PLAYLIST_NAME = "name";
    public static final String PLAYLIST_THUMBNAIL_URL = "thumbnail_url";
    public static final String PLAYLIST_DISPLAY_INDEX = "display_index";
    public static final String PLAYLIST_THUMBNAIL_PERMANENT = "is_thumbnail_permanent";
    public static final String PLAYLIST_THUMBNAIL_STREAM_ID = "thumbnail_stream_id";

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = PLAYLIST_ID)
    private long uid = 0;

    @ColumnInfo(name = PLAYLIST_NAME)
    private String name;

    @ColumnInfo(name = PLAYLIST_THUMBNAIL_PERMANENT)
    private boolean isThumbnailPermanent;

    @ColumnInfo(name = PLAYLIST_THUMBNAIL_STREAM_ID)
    private long thumbnailStreamId;

    @ColumnInfo(name = PLAYLIST_DISPLAY_INDEX)
    private long displayIndex;

    public PlaylistEntity(final String name, final boolean isThumbnailPermanent,
                          final long thumbnailStreamId, final long displayIndex) {
        this.name = name;
        this.isThumbnailPermanent = isThumbnailPermanent;
        this.thumbnailStreamId = thumbnailStreamId;
        this.displayIndex = displayIndex;
    }

    @Ignore
    public PlaylistEntity(final PlaylistMetadataEntry item) {
        this.uid = item.getUid();
        this.name = item.name;
        this.isThumbnailPermanent = item.isThumbnailPermanent();
        this.thumbnailStreamId = item.getThumbnailStreamId();
        this.displayIndex = item.getDisplayIndex();
    }

    public long getUid() {
        return uid;
    }

    public void setUid(final long uid) {
        this.uid = uid;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public long getThumbnailStreamId() {
        return thumbnailStreamId;
    }

    public void setThumbnailStreamId(final long thumbnailStreamId) {
        this.thumbnailStreamId = thumbnailStreamId;
    }

    public boolean getIsThumbnailPermanent() {
        return isThumbnailPermanent;
    }

    public void setIsThumbnailPermanent(final boolean isThumbnailSet) {
        this.isThumbnailPermanent = isThumbnailSet;
    }

    public long getDisplayIndex() {
        return displayIndex;
    }

    public void setDisplayIndex(final long displayIndex) {
        this.displayIndex = displayIndex;
    }
}
