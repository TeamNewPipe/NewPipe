package org.schabi.newpipe.database.playlist;

import androidx.room.ColumnInfo;

import static org.schabi.newpipe.database.playlist.model.PlaylistEntity.PLAYLIST_DISPLAY_INDEX;
import static org.schabi.newpipe.database.playlist.model.PlaylistEntity.PLAYLIST_ID;
import static org.schabi.newpipe.database.playlist.model.PlaylistEntity.PLAYLIST_NAME;
import static org.schabi.newpipe.database.playlist.model.PlaylistEntity.PLAYLIST_THUMBNAIL_PERMANENT;
import static org.schabi.newpipe.database.playlist.model.PlaylistEntity.PLAYLIST_THUMBNAIL_STREAM_ID;
import static org.schabi.newpipe.database.playlist.model.PlaylistEntity.PLAYLIST_THUMBNAIL_URL;

public class PlaylistMetadataEntry implements PlaylistLocalItem {
    public static final String PLAYLIST_STREAM_COUNT = "streamCount";

    @ColumnInfo(name = PLAYLIST_ID)
    private final long uid;
    @ColumnInfo(name = PLAYLIST_NAME)
    public final String name;
    @ColumnInfo(name = PLAYLIST_THUMBNAIL_PERMANENT)
    private final boolean isThumbnailPermanent;
    @ColumnInfo(name = PLAYLIST_THUMBNAIL_STREAM_ID)
    private final long thumbnailStreamId;
    @ColumnInfo(name = PLAYLIST_THUMBNAIL_URL)
    public final String thumbnailUrl;
    @ColumnInfo(name = PLAYLIST_DISPLAY_INDEX)
    private long displayIndex;
    @ColumnInfo(name = PLAYLIST_STREAM_COUNT)
    public final long streamCount;

    public PlaylistMetadataEntry(final long uid, final String name, final String thumbnailUrl,
                                 final boolean isThumbnailPermanent, final long thumbnailStreamId,
                                 final long displayIndex, final long streamCount) {
        this.uid = uid;
        this.name = name;
        this.thumbnailUrl = thumbnailUrl;
        this.isThumbnailPermanent = isThumbnailPermanent;
        this.thumbnailStreamId = thumbnailStreamId;
        this.displayIndex = displayIndex;
        this.streamCount = streamCount;
    }

    @Override
    public LocalItemType getLocalItemType() {
        return LocalItemType.PLAYLIST_LOCAL_ITEM;
    }

    @Override
    public String getOrderingName() {
        return name;
    }

    public boolean isThumbnailPermanent() {
        return isThumbnailPermanent;
    }

    public long getThumbnailStreamId() {
        return thumbnailStreamId;
    }

    @Override
    public long getDisplayIndex() {
        return displayIndex;
    }

    @Override
    public long getUid() {
        return uid;
    }

    @Override
    public void setDisplayIndex(final long displayIndex) {
        this.displayIndex = displayIndex;
    }

    @Override
    public String getThumbnailUrl() {
        return thumbnailUrl;
    }
}
