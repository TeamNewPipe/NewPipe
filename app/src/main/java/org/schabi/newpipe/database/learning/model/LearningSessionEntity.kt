package org.schabi.newpipe.database.learning.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.schabi.newpipe.database.stream.model.StreamEntity

@Entity(
    tableName = LearningSessionEntity.TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = StreamEntity::class,
            parentColumns = [StreamEntity.STREAM_ID],
            childColumns = [LearningSessionEntity.STREAM_ID],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = [LearningSessionEntity.STREAM_ID, LearningSessionEntity.STARTED_AT]),
        Index(value = [LearningSessionEntity.LOCAL_DATE])
    ]
)
data class LearningSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = SESSION_ID)
    val sessionId: String,

    @ColumnInfo(name = STREAM_ID)
    val streamId: Long,

    @ColumnInfo(name = STARTED_AT)
    val startedAt: Long,

    @ColumnInfo(name = ENDED_AT)
    val endedAt: Long,

    @ColumnInfo(name = WATCHED_DURATION_MILLIS)
    val watchedDurationMillis: Long,

    @ColumnInfo(name = LOCAL_DATE)
    val localDate: String,

    @ColumnInfo(name = BACKGROUND_PLAYBACK)
    val backgroundPlayback: Boolean,

    @ColumnInfo(name = DESIGNATED_LEARNING_CONTENT, defaultValue = "0")
    val designatedLearningContent: Boolean = false
) {
    companion object {
        const val TABLE_NAME = "learning_sessions"
        const val SESSION_ID = "session_id"
        const val STREAM_ID = "stream_id"
        const val STARTED_AT = "started_at"
        const val ENDED_AT = "ended_at"
        const val WATCHED_DURATION_MILLIS = "watched_duration_ms"
        const val LOCAL_DATE = "local_date"
        const val BACKGROUND_PLAYBACK = "background_playback"
        const val DESIGNATED_LEARNING_CONTENT = "is_designated"
    }
}
