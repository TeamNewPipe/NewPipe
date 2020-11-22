package org.schabi.newpipe.database.history.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import org.schabi.newpipe.database.stream.model.StreamEntity
import java.time.OffsetDateTime

data class StreamHistoryEntry(
    @Embedded
    val streamEntity: StreamEntity,

    @ColumnInfo(name = StreamHistoryEntity.JOIN_STREAM_ID)
    val streamId: Long,

    @ColumnInfo(name = StreamHistoryEntity.STREAM_ACCESS_DATE)
    val accessDate: OffsetDateTime,

    @ColumnInfo(name = StreamHistoryEntity.STREAM_REPEAT_COUNT)
    val repeatCount: Long
) {

    fun toStreamHistoryEntity(): StreamHistoryEntity {
        return StreamHistoryEntity(streamId, accessDate, repeatCount)
    }

    fun hasEqualValues(other: StreamHistoryEntry): Boolean {
        return this.streamEntity.uid == other.streamEntity.uid && streamId == other.streamId &&
            accessDate.isEqual(other.accessDate)
    }
}
