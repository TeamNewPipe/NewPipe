package org.schabi.newpipe.database.stream.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamStateEntity
import java.util.Objects

@Entity(tableName = StreamStateEntity.STREAM_STATE_TABLE, primaryKeys = [StreamStateEntity.JOIN_STREAM_ID], foreignKeys = [ForeignKey(entity = StreamEntity::class, parentColumns = StreamEntity.STREAM_ID, childColumns = StreamStateEntity.JOIN_STREAM_ID, onDelete = CASCADE, onUpdate = CASCADE)])
class StreamStateEntity(@field:ColumnInfo(name = JOIN_STREAM_ID) private var streamUid: Long, @field:ColumnInfo(name = STREAM_PROGRESS_MILLIS) private var progressMillis: Long) {
    fun getStreamUid(): Long {
        return streamUid
    }

    fun setStreamUid(streamUid: Long) {
        this.streamUid = streamUid
    }

    fun getProgressMillis(): Long {
        return progressMillis
    }

    fun setProgressMillis(progressMillis: Long) {
        this.progressMillis = progressMillis
    }

    /**
     * The state will be considered valid, and thus be saved, if the progress is more than [ ][.PLAYBACK_SAVE_THRESHOLD_START_MILLISECONDS] or at least 1/4 of the video length.
     * @param durationInSeconds the duration of the stream connected with this state, in seconds
     * @return whether this stream state entity should be saved or not
     */
    fun isValid(durationInSeconds: Long): Boolean {
        return (progressMillis > PLAYBACK_SAVE_THRESHOLD_START_MILLISECONDS
                || progressMillis > durationInSeconds * 1000 / 4)
    }

    /**
     * The video will be considered as finished, if the time left is less than [ ][.PLAYBACK_FINISHED_END_MILLISECONDS] and the progress is at least 3/4 of the video length.
     * The state will be saved anyway, so that it can be shown under stream info items, but the
     * player will not resume if a state is considered as finished. Finished streams are also the
     * ones that can be filtered out in the feed fragment.
     * @see org.schabi.newpipe.database.feed.dao.FeedDAO.getLiveOrNotPlayedStreams
     * @see org.schabi.newpipe.database.feed.dao.FeedDAO.getLiveOrNotPlayedStreamsForGroup
     * @param durationInSeconds the duration of the stream connected with this state, in seconds
     * @return whether the stream is finished or not
     */
    fun isFinished(durationInSeconds: Long): Boolean {
        return (progressMillis >= durationInSeconds * 1000 - PLAYBACK_FINISHED_END_MILLISECONDS
                && progressMillis >= durationInSeconds * 1000 * 3 / 4)
    }

    public override fun equals(obj: Any?): Boolean {
        if (obj is StreamStateEntity) {
            return (obj.streamUid == streamUid
                    && obj.progressMillis == progressMillis)
        } else {
            return false
        }
    }

    public override fun hashCode(): Int {
        return Objects.hash(streamUid, progressMillis)
    }

    companion object {
        val STREAM_STATE_TABLE: String = "stream_state"
        val JOIN_STREAM_ID: String = "stream_id"

        // This additional field is required for the SQL query because 'stream_id' is used
        // for some other joins already
        val JOIN_STREAM_ID_ALIAS: String = "stream_id_alias"
        val STREAM_PROGRESS_MILLIS: String = "progress_time"

        /**
         * Playback state will not be saved, if playback time is less than this threshold (5000ms = 5s).
         */
        val PLAYBACK_SAVE_THRESHOLD_START_MILLISECONDS: Long = 5000

        /**
         * Stream will be considered finished if the playback time left exceeds this threshold
         * (60000ms = 60s).
         * @see .isFinished
         * @see org.schabi.newpipe.database.feed.dao.FeedDAO.getLiveOrNotPlayedStreams
         * @see org.schabi.newpipe.database.feed.dao.FeedDAO.getLiveOrNotPlayedStreamsForGroup
         */
        val PLAYBACK_FINISHED_END_MILLISECONDS: Long = 60000
    }
}
