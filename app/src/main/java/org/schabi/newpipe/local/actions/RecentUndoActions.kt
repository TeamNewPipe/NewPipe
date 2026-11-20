package org.schabi.newpipe.local.actions

/** Volatile, bounded undo journal; only operations with concurrency guards should be registered. */
internal class UndoHistory(
    private val capacity: Int = 20,
    private val maxAgeMillis: Long = 5 * 60_000L
) {
    data class Entry(
        val id: Long,
        val profile: String,
        val title: String,
        val timestamp: Long,
        val undo: () -> Boolean,
        var claimed: Boolean = false
    )
    private val entries = ArrayDeque<Entry>()
    private var nextId = 0L

    @Synchronized
    fun record(profile: String, title: String, undo: () -> Boolean, now: Long): Long {
        prune(now)
        val entry = Entry(++nextId, profile, title.take(150), now, undo)
        entries.addFirst(entry)
        while (entries.size > capacity) entries.removeLast()
        return entry.id
    }

    @Synchronized
    fun snapshot(profile: String, now: Long): List<Entry> {
        prune(now)
        return entries.filter { it.profile == profile && !it.claimed }
    }

    @Synchronized
    fun claim(id: Long, profile: String, now: Long): (() -> Boolean)? {
        prune(now)
        val item = entries.firstOrNull { it.id == id && it.profile == profile && !it.claimed }
            ?: return null
        item.claimed = true
        return item.undo
    }

    @Synchronized
    fun finish(id: Long, success: Boolean) {
        val item = entries.firstOrNull { it.id == id } ?: return
        if (success) entries.remove(item) else item.claimed = false
    }

    private fun prune(now: Long) {
        entries.removeAll { it.timestamp > now || now - it.timestamp > maxAgeMillis }
    }
}

internal object RecentUndoActions {
    private val store = UndoHistory()

    @JvmStatic
    fun record(profile: String, title: String, undo: () -> Boolean): Long {
        return store.record(profile, title, undo, System.currentTimeMillis())
    }

    @JvmStatic
    fun snapshot(profile: String): List<UndoHistory.Entry> {
        return store.snapshot(profile, System.currentTimeMillis())
    }

    @JvmStatic
    fun claim(id: Long, profile: String): (() -> Boolean)? {
        return store.claim(id, profile, System.currentTimeMillis())
    }

    @JvmStatic
    fun finish(id: Long, success: Boolean) = store.finish(id, success)
}
