package org.schabi.newpipe.database.playlist;

import android.arch.persistence.room.ColumnInfo;

import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.info_list.stored.LocalPlaylistInfoItem;

import static org.schabi.newpipe.database.playlist.model.PlaylistEntity.PLAYLIST_ID;
import static org.schabi.newpipe.database.playlist.model.PlaylistEntity.PLAYLIST_NAME;
import static org.schabi.newpipe.database.playlist.model.PlaylistEntity.PLAYLIST_THUMBNAIL_URL;

public class PlaylistMetadataEntry implements LocalItem {
    final public static String PLAYLIST_STREAM_COUNT = "streamCount";

    @ColumnInfo(name = PLAYLIST_ID)
    final public long uid;
    @ColumnInfo(name = PLAYLIST_NAME)
    final public String name;
    @ColumnInfo(name = PLAYLIST_THUMBNAIL_URL)
    final public String thumbnailUrl;
    @ColumnInfo(name = PLAYLIST_STREAM_COUNT)
    final public long streamCount;

    public PlaylistMetadataEntry(long uid, String name, String thumbnailUrl, long streamCount) {
        this.uid = uid;
        this.name = name;
        this.thumbnailUrl = thumbnailUrl;
        this.streamCount = streamCount;
    }

    public LocalPlaylistInfoItem toStoredPlaylistInfoItem() {
        LocalPlaylistInfoItem storedPlaylistInfoItem = new LocalPlaylistInfoItem(uid, name);
        storedPlaylistInfoItem.setThumbnailUrl(thumbnailUrl);
        storedPlaylistInfoItem.setStreamCount(streamCount);
        return storedPlaylistInfoItem;
    }

    @Override
    public LocalItemType getLocalItemType() {
        return LocalItemType.PLAYLIST_ITEM;
    }
}
