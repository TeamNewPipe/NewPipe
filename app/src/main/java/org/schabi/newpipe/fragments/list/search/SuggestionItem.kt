package org.schabi.newpipe.fragments.list.search

class SuggestionItem(val fromHistory: Boolean, val query: String?) {
    public override fun equals(o: Any?): Boolean {
        if (o is SuggestionItem) {
            return (query == o.query)
        }
        return false
    }

    public override fun hashCode(): Int {
        return query.hashCode()
    }

    public override fun toString(): String {
        return "[" + fromHistory + "→" + query + "]"
    }
}
