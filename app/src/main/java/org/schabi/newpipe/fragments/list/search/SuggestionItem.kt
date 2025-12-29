package org.schabi.newpipe.fragments.list.search

class SuggestionItem(@JvmField val fromHistory: Boolean, @JvmField val query: String) {
    override fun equals(other: Any?): Boolean {
        if (other is SuggestionItem) {
            return query == other.query
        }
        return false
    }

    override fun hashCode() = query.hashCode()

    override fun toString() = "[$fromHistory→$query]"
}
