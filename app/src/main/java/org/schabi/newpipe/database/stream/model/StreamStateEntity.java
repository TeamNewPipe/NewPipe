package org.schabi.newpipe.database.stream.model;


import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.Nullable;

import java.util.concurrent.TimeUnit;

import static android.arch.persistence.room.ForeignKey.CASCADE;
import static org.schabi.newpipe.database.stream.model.StreamStateEntity.JOIN_STREAM_ID;
import static org.schabi.newpipe.database.stream.model.StreamStateEntity.STREAM_STATE_TABLE;

@Entity(tableName = STREAM_STATE_TABLE,
        indices = {@Index(value = {JOIN_STREAM_ID}, unique = true)},
        foreignKeys = {
                @ForeignKey(entity = StreamEntity.class,
                        parentColumns = StreamEntity.STREAM_ID,
                        childColumns = JOIN_STREAM_ID,
                        onDelete = CASCADE, onUpdate = CASCADE)
        })
public class StreamStateEntity {
    final public static String STREAM_STATE_TABLE   = "stream_state";
    final public static String STREAM_STATE_ID      = "uid";
    final public static String JOIN_STREAM_ID       = "stream_id";
    final public static String STREAM_PROGRESS_TIME = "progress_time";


    /** Playback state will not be saved, if playback time less than this threshold */
    private static final int PLAYBACK_SAVE_THRESHOLD_START_SECONDS = 5;
    /** Playback state will not be saved, if time left less than this threshold */
    private static final int PLAYBACK_SAVE_THRESHOLD_END_SECONDS = 10;

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = STREAM_STATE_ID)
    private long uid;

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

    public Long getUid() {
        return uid;
    }

    public void setUid(long uid) {
        this.uid = uid;
    }

    public long getProgressTime() {
        return progressTime;
    }

    public void setProgressTime(long progressTime) {
        this.progressTime = progressTime;
    }

    public boolean isValid(int durationInSeconds) {
        final int seconds = (int) TimeUnit.MILLISECONDS.toSeconds(progressTime);
        return seconds > PLAYBACK_SAVE_THRESHOLD_START_SECONDS
                && seconds < durationInSeconds - PLAYBACK_SAVE_THRESHOLD_END_SECONDS;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof StreamStateEntity) {
            return ((StreamStateEntity) obj).streamUid == streamUid
                    && ((StreamStateEntity) obj).progressTime == progressTime;
        } else return false;
    }
}
