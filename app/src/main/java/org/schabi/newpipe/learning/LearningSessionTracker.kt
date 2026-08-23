package org.schabi.newpipe.learning

import android.content.Context
import android.os.SystemClock
import android.util.Log
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.learning.model.LearningSessionEntity
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.player.playqueue.PlayQueueItem

/**
 * Records study-time intervals for Learning Mode. The tracker is intentionally small and stateful:
 * callers sample the currently playing queue item and playback state, and the tracker persists
 * finished intervals into Room.
 */
class LearningSessionTracker(context: Context) {
    private val appContext = context.applicationContext
    private val database = NewPipeDatabase.getInstance(appContext)
    private var activeSession: ActiveSession? = null

    @Synchronized
    fun update(item: PlayQueueItem?, isPlaying: Boolean, isBackgroundPlayback: Boolean) {
        val nowWallClock = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        accrueElapsedTime(nowWallClock, nowElapsed)

        val eligible = item != null &&
            isPlaying &&
            LearningMode.isEnabled(appContext) &&
            (!isBackgroundPlayback || LearningMode.shouldCountBackgroundPlayback(appContext))

        if (!eligible) {
            finish(nowWallClock)
            return
        }

        val sessionDate = localDate(nowWallClock)
        val current = activeSession
        if (current == null || !current.matches(item, isBackgroundPlayback, sessionDate)) {
            finish(nowWallClock)
            activeSession = ActiveSession(
                sessionId = UUID.randomUUID().toString(),
                stream = StreamEntity(item),
                startedAt = nowWallClock,
                endedAt = nowWallClock,
                watchedDurationMillis = 0,
                localDate = sessionDate,
                backgroundPlayback = isBackgroundPlayback,
                lastSampleElapsed = nowElapsed,
                lastPersistedDuration = 0
            )
            return
        }

        if (current.watchedDurationMillis - current.lastPersistedDuration >= FLUSH_INTERVAL_MS) {
            persist(current)
            current.lastPersistedDuration = current.watchedDurationMillis
        }
    }

    @Synchronized
    fun stop() {
        val nowWallClock = System.currentTimeMillis()
        accrueElapsedTime(nowWallClock, SystemClock.elapsedRealtime())
        finish(nowWallClock)
    }

    private fun accrueElapsedTime(nowWallClock: Long, nowElapsed: Long) {
        activeSession?.let { session ->
            val elapsed = (nowElapsed - session.lastSampleElapsed).coerceAtLeast(0)
            session.watchedDurationMillis += elapsed
            session.endedAt = nowWallClock
            session.lastSampleElapsed = nowElapsed
        }
    }

    private fun finish(nowWallClock: Long) {
        activeSession?.let { session ->
            session.endedAt = nowWallClock
            if (session.watchedDurationMillis >= MIN_SESSION_DURATION_MS) {
                persist(session)
            }
        }
        activeSession = null
    }

    private fun persist(session: ActiveSession) {
        val entity = session.toEntity()
        val stream = session.stream.copy()
        Completable.fromAction {
            val streamId = database.streamDAO().upsert(stream)
            database.learningSessionDAO().upsert(entity.copy(streamId = streamId))
        }.subscribeOn(Schedulers.single()).subscribe(
            {},
            { error -> Log.e(TAG, "Could not persist Learning Mode session", error) }
        )
    }

    private fun localDate(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .toString()

    private data class ActiveSession(
        val sessionId: String,
        val stream: StreamEntity,
        val startedAt: Long,
        var endedAt: Long,
        var watchedDurationMillis: Long,
        val localDate: String,
        val backgroundPlayback: Boolean,
        var lastSampleElapsed: Long,
        var lastPersistedDuration: Long
    ) {
        fun matches(item: PlayQueueItem, background: Boolean, date: String): Boolean =
            stream.serviceId == item.serviceId &&
                stream.url == item.url &&
                backgroundPlayback == background &&
                localDate == date

        fun toEntity(): LearningSessionEntity = LearningSessionEntity(
            sessionId = sessionId,
            streamId = 0,
            startedAt = startedAt,
            endedAt = endedAt,
            watchedDurationMillis = watchedDurationMillis,
            localDate = localDate,
            backgroundPlayback = backgroundPlayback,
            designatedLearningContent = true
        )
    }

    companion object {
        private const val TAG = "LearningSessionTracker"
        private const val FLUSH_INTERVAL_MS = 15_000L
        private const val MIN_SESSION_DURATION_MS = 1_000L
    }
}
