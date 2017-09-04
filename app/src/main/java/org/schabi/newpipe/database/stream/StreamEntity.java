package org.schabi.newpipe.database.stream;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Ignore;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.Date;

import static org.schabi.newpipe.database.stream.StreamEntity.STREAM_SERVICE_ID;
import static org.schabi.newpipe.database.stream.StreamEntity.STREAM_TABLE;
import static org.schabi.newpipe.database.stream.StreamEntity.STREAM_URL;

@Entity(tableName = STREAM_TABLE,
        indices = {@Index(value = {STREAM_SERVICE_ID, STREAM_URL}, unique = true)})
public class StreamEntity {
    public final static String STREAM_UID         = "uid";

    final static String STREAM_TABLE              = "streams";
    final static String STREAM_ID                 = "id";
    final static String STREAM_TYPE               = "type";
    final static String STREAM_SERVICE_ID         = "service_id";
    final static String STREAM_URL                = "url";
    final static String STREAM_TITLE              = "title";
    final static String STREAM_THUMBNAIL_URL      = "thumbnail_url";
    final static String STREAM_VIEW_COUNT         = "view_count";
    final static String STREAM_UPLOADER           = "uploader";
    final static String STREAM_UPLOAD_DATE        = "upload_date";
    final static String STREAM_DURATION           = "duration";

    @PrimaryKey(autoGenerate = true)
    private long uid = 0;

    @ColumnInfo(name = STREAM_SERVICE_ID)
    private int serviceId = -1;

    @ColumnInfo(name = STREAM_ID)
    private String id;

    @ColumnInfo(name = STREAM_TYPE)
    private String type;

    @ColumnInfo(name = STREAM_URL)
    private String url;

    @ColumnInfo(name = STREAM_TITLE)
    private String title;

    @ColumnInfo(name = STREAM_THUMBNAIL_URL)
    private String thumbnailUrl;

    @ColumnInfo(name = STREAM_VIEW_COUNT)
    private Long viewCount;

    @ColumnInfo(name = STREAM_UPLOADER)
    private String uploader;

    @ColumnInfo(name = STREAM_UPLOAD_DATE)
    private long uploadDate;

    @ColumnInfo(name = STREAM_DURATION)
    private long duration;

    @Ignore
    public StreamInfoItem toStreamInfoItem() {
        StreamInfoItem item = new StreamInfoItem();

        item.stream_type = StreamType.valueOf( this.getType() );

        item.service_id = this.getServiceId();
        item.url = this.getUrl();
        item.name = this.getTitle();
        item.thumbnail_url = this.getThumbnailUrl();
        item.view_count = this.getViewCount();
        item.uploader_name = this.getUploader();

        // TODO: temporary until upload date parsing is fleshed out
        item.upload_date = "Unknown";
        item.duration = this.getDuration();

        return item;
    }

    @Ignore
    public StreamEntity(final StreamInfoItem item) {
        setData(item);
    }

    @Ignore
    public void setData(final StreamInfoItem item) {
        // Do not store ordinals into db since they may change in the future
        this.type = item.stream_type.name();

        this.serviceId = item.service_id;
        this.url = item.url;
        this.title = item.name;
        this.thumbnailUrl = item.thumbnail_url;
        this.viewCount = item.view_count;
        this.uploader = item.uploader_name;

        // TODO: temporary until upload date parsing is fleshed out
        this.uploadDate = new Date().getTime();
        this.duration = item.duration;
    }

    @Ignore
    public boolean is(final StreamInfoItem item) {
        return this.type.equals( item.stream_type.name() ) &&
                this.serviceId == item.service_id &&
                this.url.equals( item.url );
    }

    public long getUid() {
        return uid;
    }

    void setUid(long uid) {
        this.uid = uid;
    }

    public int getServiceId() {
        return serviceId;
    }

    public void setServiceId(int serviceId) {
        this.serviceId = serviceId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public Long getViewCount() {
        return viewCount;
    }

    public void setViewCount(Long viewCount) {
        this.viewCount = viewCount;
    }

    public String getUploader() {
        return uploader;
    }

    public void setUploader(String uploader) {
        this.uploader = uploader;
    }

    public long getUploadDate() {
        return uploadDate;
    }

    public void setUploadDate(long uploadDate) {
        this.uploadDate = uploadDate;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }
}
