package org.schabi.newpipe.fragments.list.search;

public class SuggestionItem {
    final boolean fromHistory;
    public final String query;

    public SuggestionItem(final boolean fromHistory, final String query) {
        this.fromHistory = fromHistory;
        this.query = query;
    }

    @Override
    public String toString() {
        return "[" + fromHistory + "→" + query + "]";
    }
}
