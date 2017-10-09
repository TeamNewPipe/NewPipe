package org.schabi.newpipe.fragments.list.search;

public class SuggestionItem {
    public final boolean fromHistory;
    public final String query;

    public SuggestionItem(boolean fromHistory, String query) {
        this.fromHistory = fromHistory;
        this.query = query;
    }

    @Override
    public String toString() {
        return "[" + fromHistory + "→" + query + "]";
    }
}
