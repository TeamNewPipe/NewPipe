package org.schabi.newpipe.database.playlist;

import android.arch.persistence.room.ColumnInfo;

import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

public class PlaylistStreamEntry implements LocalItem {
    @ColumnInfo(name = StreamEntity.STREAM_ID)
    final public long uid;
    @ColumnInfo(name = StreamEntity.STREAM_SERVICE_ID)
    final public int serviceId;
    @ColumnInfo(name = StreamEntity.STREAM_URL)
    final public String url;
    @ColumnInfo(name = StreamEntity.STREAM_TITLE)
    final public String title;
    @ColumnInfo(name = StreamEntity.STREAM_TYPE)
    final public StreamType streamType;
    @ColumnInfo(name = StreamEntity.STREAM_DURATION)
    final public long duration;
    @ColumnInfo(name = StreamEntity.STREAM_UPLOADER)
    final public String uploader;
    @ColumnInfo(name = StreamEntity.STREAM_THUMBNAIL_URL)
    final public String thumbnailUrl;
    @ColumnInfo(name = PlaylistStreamEntity.JOIN_STREAM_ID)
    final public long streamId;
    @ColumnInfo(name = PlaylistStreamEntity.JOIN_INDEX)
    final public int joinIndex;

    public PlaylistStreamEntry(long uid, int serviceId, String url, String title,
                               StreamType streamType, long duration, String uploader,
                               String thumbnailUrl, long streamId, int joinIndex) {
        this.uid = uid;
        this.serviceId = serviceId;
        this.url = url;
        this.title = title;
        this.streamType = streamType;
        this.duration = duration;
        this.uploader = uploader;
        this.thumbnailUrl = thumbnailUrl;
        this.streamId = streamId;
        this.joinIndex = joinIndex;
    }

    public StreamInfoItem toStreamInfoItem() throws IllegalArgumentException {
        StreamInfoItem item = new StreamInfoItem(serviceId, url, title, streamType);
        item.setThumbnailUrl(thumbnailUrl);
        item.setUploaderName(uploader);
        item.setDuration(duration);
        return item;
    }

    @Override
    public LocalItemType getLocalItemType() {
        return LocalItemType.PLAYLIST_STREAM_ITEM;
    }
}
