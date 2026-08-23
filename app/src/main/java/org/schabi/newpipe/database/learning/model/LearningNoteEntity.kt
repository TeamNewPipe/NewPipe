package org.schabi.newpipe.database.learning.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.schabi.newpipe.database.stream.model.StreamEntity

@Entity(
    tableName = LearningNoteEntity.TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = StreamEntity::class,
            parentColumns = [StreamEntity.STREAM_ID],
            childColumns = [LearningNoteEntity.STREAM_ID],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = [LearningNoteEntity.STREAM_ID, LearningNoteEntity.TIMESTAMP_MILLIS])]
)
data class LearningNoteEntity(
    @PrimaryKey
    @ColumnInfo(name = NOTE_ID)
    val noteId: String,

    @ColumnInfo(name = STREAM_ID)
    val streamId: Long,

    @ColumnInfo(name = TIMESTAMP_MILLIS)
    val timestampMillis: Long,

    @ColumnInfo(name = NOTE_TEXT)
    val noteText: String,

    @ColumnInfo(name = CREATED_AT)
    val createdAt: Long,

    @ColumnInfo(name = UPDATED_AT)
    val updatedAt: Long
) {
    companion object {
        const val TABLE_NAME = "learning_notes"
        const val NOTE_ID = "note_id"
        const val STREAM_ID = "stream_id"
        const val TIMESTAMP_MILLIS = "timestamp_ms"
        const val NOTE_TEXT = "note_text"
        const val CREATED_AT = "created_at"
        const val UPDATED_AT = "updated_at"
    }
}
