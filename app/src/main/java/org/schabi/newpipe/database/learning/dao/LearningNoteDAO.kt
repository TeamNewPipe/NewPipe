package org.schabi.newpipe.database.learning.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.rxjava3.core.Flowable
import org.schabi.newpipe.database.learning.model.LearningNoteEntity

@Dao
interface LearningNoteDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(note: LearningNoteEntity)

    @Query("DELETE FROM learning_notes WHERE note_id = :noteId")
    fun delete(noteId: String)

    @Query("SELECT * FROM learning_notes WHERE note_id = :noteId")
    fun get(noteId: String): LearningNoteEntity?

    @Query("SELECT * FROM learning_notes WHERE stream_id = :streamId ORDER BY timestamp_ms ASC, created_at ASC")
    fun observeForStream(streamId: Long): Flowable<List<LearningNoteEntity>>

    @Query("SELECT * FROM learning_notes ORDER BY updated_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flowable<List<LearningNoteEntity>>
}
