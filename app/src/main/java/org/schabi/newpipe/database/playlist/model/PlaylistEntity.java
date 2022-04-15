package org.schabi.newpipe.database.playlist.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import static org.schabi.newpipe.database.playlist.model.PlaylistEntity.PLAYLIST_NAME;
import static org.schabi.newpipe.database.playlist.model.PlaylistEntity.PLAYLIST_TABLE;

import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;

@Entity(tableName = PLAYLIST_TABLE,
        indices = {@Index(value = {PLAYLIST_NAME})})
public class PlaylistEntity {
    public static final String PLAYLIST_TABLE = "playlists";
    public static final String PLAYLIST_ID = "uid";
    public static final String PLAYLIST_NAME = "name";
    public static final String PLAYLIST_THUMBNAIL_URL = "thumbnail_url";
    public static final String PLAYLIST_DISPLAY_INDEX = "display_index";

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = PLAYLIST_ID)
    private long uid = 0;

    @ColumnInfo(name = PLAYLIST_NAME)
    private String name;

    @ColumnInfo(name = PLAYLIST_THUMBNAIL_URL)
    private String thumbnailUrl;

    @ColumnInfo(name = PLAYLIST_DISPLAY_INDEX)
    private long displayIndex;

    public PlaylistEntity(final String name, final String thumbnailUrl, final long displayIndex) {
        this.name = name;
        this.thumbnailUrl = thumbnailUrl;
        this.displayIndex = displayIndex;
    }

    @Ignore
    public PlaylistEntity(final PlaylistMetadataEntry item) {
        this.uid = item.uid;
        this.name = item.name;
        this.thumbnailUrl = item.thumbnailUrl;
        this.displayIndex = item.displayIndex;
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

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(final String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public long getDisplayIndex() {
        return displayIndex;
    }

    public void setDisplayIndex(final long displayIndex) {
        this.displayIndex = displayIndex;
    }
}
