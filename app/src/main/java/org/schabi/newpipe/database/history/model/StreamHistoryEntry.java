package org.schabi.newpipe.database.history.model;

import android.arch.persistence.room.ColumnInfo;

import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.Date;

public class StreamHistoryEntry {
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
    @ColumnInfo(name = StreamHistoryEntity.STREAM_ACCESS_DATE)
    final public Date accessDate;
    @ColumnInfo(name = StreamHistoryEntity.STREAM_REPEAT_COUNT)
    final public long repeatCount;

    public StreamHistoryEntry(long uid, int serviceId, String url, String title,
                              StreamType streamType, long duration, String uploader,
                              String thumbnailUrl, long streamId, Date accessDate,
                              long repeatCount) {
        this.uid = uid;
        this.serviceId = serviceId;
        this.url = url;
        this.title = title;
        this.streamType = streamType;
        this.duration = duration;
        this.uploader = uploader;
        this.thumbnailUrl = thumbnailUrl;
        this.streamId = streamId;
        this.accessDate = accessDate;
        this.repeatCount = repeatCount;
    }

    public StreamHistoryEntity toStreamHistoryEntity() {
        return new StreamHistoryEntity(streamId, accessDate, repeatCount);
    }

    public boolean hasEqualValues(StreamHistoryEntry other) {
        return this.uid == other.uid && streamId == other.streamId &&
                accessDate.compareTo(other.accessDate) == 0;
    }
}
