package org.schabi.newpipe.history.model;

import android.arch.persistence.room.Entity;

import org.schabi.newpipe.extractor.stream_info.StreamInfo;

import java.util.Date;

@Entity(tableName = "watch_history")
public class WatchHistoryEntry extends HistoryEntry {

    private final String title;
    private final String url;
    private final String streamId;
    private final String thumbnailURL;
    private final String uploader;
    private final int duration;

    public WatchHistoryEntry(Date creationDate, int serviceId, String title, String url, String streamId, String thumbnailURL, String uploader, int duration) {
        super(creationDate, serviceId);
        this.title = title;
        this.url = url;
        this.streamId = streamId;
        this.thumbnailURL = thumbnailURL;
        this.uploader = uploader;
        this.duration = duration;
    }

    public WatchHistoryEntry(StreamInfo streamInfo) {
        this(new Date(), streamInfo.service_id, streamInfo.title, streamInfo.webpage_url,
                streamInfo.id, streamInfo.thumbnail_url, streamInfo.uploader, streamInfo.duration);
    }

    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }

    public String getStreamId() {
        return streamId;
    }

    public String getThumbnailURL() {
        return thumbnailURL;
    }

    public String getUploader() {
        return uploader;
    }

    public int getDuration() {
        return duration;
    }
}
