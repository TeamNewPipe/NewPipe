package org.schabi.newpipe.fragments

/** Removes stale pager references without accidentally discarding equal-but-distinct tabs. */
object HomeTabRetention {
    @JvmStatic
    fun <T : Any> forget(items: MutableList<T>, removed: T) {
        items.removeAll { it === removed }
    }
}
