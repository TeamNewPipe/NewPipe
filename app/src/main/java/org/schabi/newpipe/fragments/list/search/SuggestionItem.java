package org.schabi.newpipe.fragments.list.search;

import androidx.annotation.NonNull;

public class SuggestionItem {
    private final boolean fromHistory;
    private final String query;

    public SuggestionItem(final boolean fromHistory, final String query) {
        this.fromHistory = fromHistory;
        this.query = query;
    }

    public boolean isFromHistory() {
        return fromHistory;
    }

    public String getQuery() {
        return query;
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof SuggestionItem) {
            return query.equals(((SuggestionItem) o).query);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return query.hashCode();
    }

    @NonNull
    @Override
    public String toString() {
        return "[" + fromHistory + "→" + query + "]";
    }
}
