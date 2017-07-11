package org.schabi.newpipe.history.model;

import android.arch.persistence.room.Entity;

import org.schabi.newpipe.extractor.stream_info.StreamInfo;

import java.util.Date;

@Entity(tableName = "watch_history")
public class WatchHistoryEntry extends HistoryEntry {

    private final String title;
    private final String url;
    private final String streamId;

    public WatchHistoryEntry(Date creationDate, int serviceId, String title, String url, String streamId) {
        super(creationDate, serviceId);
        this.title = title;
        this.url = url;
        this.streamId = streamId;
    }

    public WatchHistoryEntry(StreamInfo streamInfo) {
        this(new Date(), streamInfo.service_id, streamInfo.title, streamInfo.webpage_url, streamInfo.id);
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
}
