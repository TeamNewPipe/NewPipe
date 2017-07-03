package org.schabi.newpipe.database.video;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;

@Entity(tableName = "videos", indices = {@Index("video_title")})
public class VideoEntity {

    @PrimaryKey
    private long uid;

    @ColumnInfo(name = "service_id")
    private int serviceId;

    @ColumnInfo(name = "video_title")
    private String videoTitle;

    public long getUid() {
        return uid;
    }

    public void setUid(long uid) {
        this.uid = uid;
    }

    public int getServiceId() {
        return serviceId;
    }

    public void setServiceId(int serviceId) {
        this.serviceId = serviceId;
    }

    public String getVideoTitle() {
        return videoTitle;
    }

    public void setVideoTitle(String videoTitle) {
        this.videoTitle = videoTitle;
    }
}
