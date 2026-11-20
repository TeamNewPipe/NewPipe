package org.schabi.newpipe.about.changelog

/** Case-insensitive plain-text offsets; never searches HTML markup or runs JavaScript. */
internal object ChangelogSearch {
    fun findMatches(text: CharSequence, query: String, limit: Int = 500): List<IntRange> {
        if (query.isBlank() || limit <= 0) return emptyList()
        val matches = ArrayList<IntRange>()
        var start = 0
        while (matches.size < limit) {
            val next = text.indexOf(query, startIndex = start, ignoreCase = true)
            if (next < 0) break
            matches.add(next until next + query.length)
            start = next + query.length
        }
        return matches
    }
}
