package org.schabi.newpipe.database.playlist;

import androidx.room.ColumnInfo;

import static org.schabi.newpipe.database.playlist.model.PlaylistEntity.PLAYLIST_ID;
import static org.schabi.newpipe.database.playlist.model.PlaylistEntity.PLAYLIST_NAME;
import static org.schabi.newpipe.database.playlist.model.PlaylistEntity.PLAYLIST_THUMBNAIL_URL;

public class PlaylistMetadataEntry implements PlaylistLocalItem {
    public static final String PLAYLIST_STREAM_COUNT = "streamCount";

    @ColumnInfo(name = PLAYLIST_ID)
    public final long uid;
    @ColumnInfo(name = PLAYLIST_NAME)
    public final String name;
    @ColumnInfo(name = PLAYLIST_THUMBNAIL_URL)
    public final String thumbnailUrl;
    @ColumnInfo(name = PLAYLIST_STREAM_COUNT)
    public final long streamCount;

    public PlaylistMetadataEntry(final long uid, final String name, final String thumbnailUrl,
                                 final long streamCount) {
        this.uid = uid;
        this.name = name;
        this.thumbnailUrl = thumbnailUrl;
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
}
