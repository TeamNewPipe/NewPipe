package org.schabi.newpipe.player

/** Bounded, session-only history for user seeks; no profile data or telemetry is stored. */
internal class UserSeekHistory(private val maxPositions: Int = 32) {
    private var media = ""
    private val positions = mutableListOf<Long>()
    private var index = -1

    private fun ensureMedia(key: String) {
        if (media == key) return
        media = key
        positions.clear()
        index = -1
    }

    fun record(key: String, from: Long, to: Long) {
        ensureMedia(key)
        if (key.isBlank() || from < 0 || to < 0 || kotlin.math.abs(from - to) < 1_000L) return
        if (index < 0) {
            positions.add(from)
            index = 0
        }
        while (positions.size > index + 1) positions.removeAt(positions.lastIndex)
        if (positions.lastOrNull() != from) positions.add(from)
        positions.add(to)
        while (positions.size > maxPositions.coerceAtLeast(2)) {
            positions.removeAt(0)
        }
        index = positions.lastIndex
    }

    fun canBack(key: String): Boolean {
        ensureMedia(key)
        return index > 0
    }

    fun canForward(key: String): Boolean {
        ensureMedia(key)
        return index in 0 until positions.lastIndex
    }

    fun back(key: String): Long? = if (canBack(key)) positions[--index] else null

    fun forward(key: String): Long? = if (canForward(key)) positions[++index] else null
}
