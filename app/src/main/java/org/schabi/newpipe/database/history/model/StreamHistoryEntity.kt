package org.schabi.newpipe.database.history.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import org.schabi.newpipe.database.history.model.StreamHistoryEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import java.time.OffsetDateTime

@Entity(tableName = StreamHistoryEntity.STREAM_HISTORY_TABLE, primaryKeys = [StreamHistoryEntity.JOIN_STREAM_ID, StreamHistoryEntity.STREAM_ACCESS_DATE], indices = [Index(value = [StreamHistoryEntity.JOIN_STREAM_ID])], foreignKeys = [ForeignKey(entity = StreamEntity::class, parentColumns = StreamEntity.STREAM_ID, childColumns = StreamHistoryEntity.JOIN_STREAM_ID, onDelete = CASCADE, onUpdate = CASCADE)])
class StreamHistoryEntity
/**
 * @param streamUid the stream id this history item will refer to
 * @param accessDate the last time the stream was accessed
 * @param repeatCount the total number of views this stream received
 */(@field:ColumnInfo(name = JOIN_STREAM_ID) private var streamUid: Long,
    @field:ColumnInfo(name = STREAM_ACCESS_DATE) private var accessDate: OffsetDateTime,
    @field:ColumnInfo(name = STREAM_REPEAT_COUNT) private var repeatCount: Long) {
    fun getStreamUid(): Long {
        return streamUid
    }

    fun setStreamUid(streamUid: Long) {
        this.streamUid = streamUid
    }

    fun getAccessDate(): OffsetDateTime {
        return accessDate
    }

    fun setAccessDate(accessDate: OffsetDateTime) {
        this.accessDate = accessDate
    }

    fun getRepeatCount(): Long {
        return repeatCount
    }

    fun setRepeatCount(repeatCount: Long) {
        this.repeatCount = repeatCount
    }

    companion object {
        val STREAM_HISTORY_TABLE: String = "stream_history"
        val JOIN_STREAM_ID: String = "stream_id"
        val STREAM_ACCESS_DATE: String = "access_date"
        val STREAM_REPEAT_COUNT: String = "repeat_count"
    }
}
