package org.schabi.newpipe.database.stream.model;


import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;

import static android.arch.persistence.room.ForeignKey.CASCADE;
import static org.schabi.newpipe.database.stream.model.StreamStateEntity.JOIN_STREAM_ID;
import static org.schabi.newpipe.database.stream.model.StreamStateEntity.STREAM_STATE_TABLE;

@Entity(tableName = STREAM_STATE_TABLE,
        primaryKeys = {JOIN_STREAM_ID},
        foreignKeys = {
                @ForeignKey(entity = StreamEntity.class,
                        parentColumns = StreamEntity.STREAM_ID,
                        childColumns = JOIN_STREAM_ID,
                        onDelete = CASCADE, onUpdate = CASCADE)
        })
public class StreamStateEntity {
    final public static String STREAM_STATE_TABLE   = "stream_state";
    final public static String JOIN_STREAM_ID       = "stream_id";
    final public static String STREAM_PROGRESS_TIME = "progress_time";

    @ColumnInfo(name = JOIN_STREAM_ID)
    private long streamUid;

    @ColumnInfo(name = STREAM_PROGRESS_TIME)
    private long progressTime;

    public StreamStateEntity(long streamUid, long progressTime) {
        this.streamUid = streamUid;
        this.progressTime = progressTime;
    }

    public long getStreamUid() {
        return streamUid;
    }

    public void setStreamUid(long streamUid) {
        this.streamUid = streamUid;
    }

    public long getProgressTime() {
        return progressTime;
    }

    public void setProgressTime(long progressTime) {
        this.progressTime = progressTime;
    }
}
