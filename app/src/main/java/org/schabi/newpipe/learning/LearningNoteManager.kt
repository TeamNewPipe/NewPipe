package org.schabi.newpipe.learning

import android.content.Context
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.UUID
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.learning.model.LearningNoteEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamInfo

class LearningNoteManager(context: Context) {
    private val database = NewPipeDatabase.getInstance(context.applicationContext)
    private val noteDao = database.learningNoteDAO()
    private val streamDao = database.streamDAO()

    fun create(info: StreamInfo, timestampMillis: Long, text: String): Single<LearningNoteEntity> =
        Single.fromCallable {
            database.runInTransaction<LearningNoteEntity> {
                val now = System.currentTimeMillis()
                val streamId = streamDao.upsert(StreamEntity(info))
                val note = LearningNoteEntity(
                    noteId = UUID.randomUUID().toString(),
                    streamId = streamId,
                    timestampMillis = timestampMillis.coerceAtLeast(0),
                    noteText = cleanText(text),
                    createdAt = now,
                    updatedAt = now
                )
                noteDao.upsert(note)
                note
            }
        }.subscribeOn(Schedulers.io())

    fun update(note: LearningNoteEntity, timestampMillis: Long, text: String): Single<LearningNoteEntity> =
        Single.fromCallable {
            note.copy(
                timestampMillis = timestampMillis.coerceAtLeast(0),
                noteText = cleanText(text),
                updatedAt = System.currentTimeMillis()
            ).also(noteDao::upsert)
        }.subscribeOn(Schedulers.io())

    fun delete(noteId: String): Completable = Completable.fromAction { noteDao.delete(noteId) }
        .subscribeOn(Schedulers.io())

    fun observe(streamId: Long): Flowable<List<LearningNoteEntity>> =
        noteDao.observeForStream(streamId).subscribeOn(Schedulers.io())

    private fun cleanText(value: String): String {
        val text = value.trim()
        require(text.isNotEmpty()) { "Learning note cannot be empty" }
        require(text.length <= MAX_NOTE_LENGTH) { "Learning note is too long" }
        return text
    }

    companion object {
        const val MAX_NOTE_LENGTH = 10_000
    }
}
