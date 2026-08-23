package org.schabi.newpipe.learning

import org.schabi.newpipe.database.playlist.PlaylistStreamEntry
import org.schabi.newpipe.database.stream.model.StreamStateEntity

data class LearningProgress(
    val completed: Int,
    val eligible: Int
) {
    val percentage: Int
        get() = if (eligible == 0) 0 else (completed * 100 / eligible).coerceIn(0, 100)
}

object LearningPlaylistProgress {
    @JvmStatic
    fun calculate(entries: List<PlaylistStreamEntry>): LearningProgress = calculateValues(
        entries.map { it.streamEntity.duration to it.progressMillis }
    )

    internal fun calculateValues(values: List<Pair<Long, Long>>): LearningProgress {
        var eligible = 0
        var completed = 0
        values.forEach { (duration, progress) ->
            if (duration > 0) {
                eligible += 1
                if (StreamStateEntity(0, progress).isFinished(duration)) {
                    completed += 1
                }
            }
        }
        return LearningProgress(completed, eligible)
    }
}
