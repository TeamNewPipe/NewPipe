package org.schabi.newpipe.database.stream.model;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;

import static androidx.room.ForeignKey.CASCADE;
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
    public static final String STREAM_STATE_TABLE = "stream_state";
    public static final String JOIN_STREAM_ID = "stream_id";
    // This additional field is required for the SQL query because 'stream_id' is used
    // for some other joins already
    public static final String JOIN_STREAM_ID_ALIAS = "stream_id_alias";
    public static final String STREAM_PROGRESS_MILLIS = "progress_time";

    /**
     * Playback state will not be saved, if playback time is less than this threshold.
     */
    private static final long PLAYBACK_SAVE_THRESHOLD_START_MILLISECONDS = 5000; // 5000ms = 5s

    /**
     * @see #isFinished(long)
     * @see org.schabi.newpipe.database.feed.dao.FeedDAO#getLiveOrNotPlayedStreams()
     */
    public static final long PLAYBACK_FINISHED_END_MILLISECONDS = 60000; // 60000ms = 60s

    @ColumnInfo(name = JOIN_STREAM_ID)
    private long streamUid;

    @ColumnInfo(name = STREAM_PROGRESS_MILLIS)
    private long progressMillis;

    public StreamStateEntity(final long streamUid, final long progressMillis) {
        this.streamUid = streamUid;
        this.progressMillis = progressMillis;
    }

    public long getStreamUid() {
        return streamUid;
    }

    public void setStreamUid(final long streamUid) {
        this.streamUid = streamUid;
    }

    public long getProgressMillis() {
        return progressMillis;
    }

    public void setProgressMillis(final long progressMillis) {
        this.progressMillis = progressMillis;
    }

    /**
     * The state will be considered valid, and thus be saved, if the progress is more than {@link
     * #PLAYBACK_SAVE_THRESHOLD_START_MILLISECONDS}.
     * @return whether this stream state entity should be saved or not
     */
    public boolean isValid() {
        return progressMillis > PLAYBACK_SAVE_THRESHOLD_START_MILLISECONDS;
    }

    /**
     * The video will be considered as finished, if the time left is less than {@link
     * #PLAYBACK_FINISHED_END_MILLISECONDS} and the progress is at least 3/4 of the video length.
     * The state will be saved anyway, so that it can be shown under stream info items, but the
     * player will not resume if a state is considered as finished. Finished streams are also the
     * ones that can be filtered out in the feed fragment.
     * @param durationInSeconds the duration of the stream connected with this state, in seconds
     * @return whether the stream is finished or not
     */
    public boolean isFinished(final long durationInSeconds) {
        return progressMillis >= durationInSeconds * 1000 - PLAYBACK_FINISHED_END_MILLISECONDS
                && progressMillis >= durationInSeconds * 1000 * 3 / 4;
    }

    @Override
    public boolean equals(@Nullable final Object obj) {
        if (obj instanceof StreamStateEntity) {
            return ((StreamStateEntity) obj).streamUid == streamUid
                    && ((StreamStateEntity) obj).progressMillis == progressMillis;
        } else {
            return false;
        }
    }
}
