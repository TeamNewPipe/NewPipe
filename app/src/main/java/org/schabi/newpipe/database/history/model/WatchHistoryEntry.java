package org.schabi.newpipe.database.history.model;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Ignore;

import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.Date;

@Entity(tableName = WatchHistoryEntry.TABLE_NAME)
public class WatchHistoryEntry extends HistoryEntry {

    public static final String TABLE_NAME = "watch_history";
    public static final String TITLE = "title";
    public static final String URL = "url";
    public static final String STREAM_ID = "stream_id";
    public static final String THUMBNAIL_URL = "thumbnail_url";
    public static final String UPLOADER = "uploader";
    public static final String DURATION = "duration";

    @ColumnInfo(name = TITLE)
    private String title;

    @ColumnInfo(name = URL)
    private String url;

    @ColumnInfo(name = STREAM_ID)
    private String streamId;

    @ColumnInfo(name = THUMBNAIL_URL)
    private String thumbnailURL;

    @ColumnInfo(name = UPLOADER)
    private String uploader;

    @ColumnInfo(name = DURATION)
    private long duration;

    public WatchHistoryEntry(Date creationDate, int serviceId, String title, String url, String streamId, String thumbnailURL, String uploader, long duration) {
        super(creationDate, serviceId);
        this.title = title;
        this.url = url;
        this.streamId = streamId;
        this.thumbnailURL = thumbnailURL;
        this.uploader = uploader;
        this.duration = duration;
    }

    public WatchHistoryEntry(StreamInfo streamInfo) {
        this(new Date(), streamInfo.getServiceId(), streamInfo.getName(), streamInfo.getUrl(),
                streamInfo.id, streamInfo.thumbnail_url, streamInfo.uploader_name, streamInfo.duration);
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStreamId() {
        return streamId;
    }

    public void setStreamId(String streamId) {
        this.streamId = streamId;
    }

    public String getThumbnailURL() {
        return thumbnailURL;
    }

    public void setThumbnailURL(String thumbnailURL) {
        this.thumbnailURL = thumbnailURL;
    }

    public String getUploader() {
        return uploader;
    }

    public void setUploader(String uploader) {
        this.uploader = uploader;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    @Ignore
    @Override
    public boolean hasEqualValues(HistoryEntry otherEntry) {
        return otherEntry instanceof WatchHistoryEntry && super.hasEqualValues(otherEntry)
                && getUrl().equals(((WatchHistoryEntry) otherEntry).getUrl());
    }
}
