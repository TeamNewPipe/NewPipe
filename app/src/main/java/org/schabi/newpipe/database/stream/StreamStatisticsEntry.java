package org.schabi.newpipe.database.stream;

import android.arch.persistence.room.ColumnInfo;

import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.history.model.StreamHistoryEntity;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.Date;

public class StreamStatisticsEntry implements LocalItem {
    final public static String STREAM_LATEST_DATE = "latestAccess";
    final public static String STREAM_WATCH_COUNT = "watchCount";

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
    @ColumnInfo(name = StreamHistoryEntity.JOIN_STREAM_ID)
    final public long streamId;
    @ColumnInfo(name = StreamStatisticsEntry.STREAM_LATEST_DATE)
    final public Date latestAccessDate;
    @ColumnInfo(name = StreamStatisticsEntry.STREAM_WATCH_COUNT)
    final public long watchCount;

    public StreamStatisticsEntry(long uid, int serviceId, String url, String title,
                                 StreamType streamType, long duration, String uploader,
                                 String thumbnailUrl, long streamId, Date latestAccessDate,
                                 long watchCount) {
        this.uid = uid;
        this.serviceId = serviceId;
        this.url = url;
        this.title = title;
        this.streamType = streamType;
        this.duration = duration;
        this.uploader = uploader;
        this.thumbnailUrl = thumbnailUrl;
        this.streamId = streamId;
        this.latestAccessDate = latestAccessDate;
        this.watchCount = watchCount;
    }

    public StreamInfoItem toStreamInfoItem() {
        StreamInfoItem item = new StreamInfoItem(serviceId, url, title, streamType);
        item.setDuration(duration);
        item.setUploaderName(uploader);
        item.setThumbnailUrl(thumbnailUrl);
        return item;
    }

    @Override
    public LocalItemType getLocalItemType() {
        return LocalItemType.STATISTIC_STREAM_ITEM;
    }
}
