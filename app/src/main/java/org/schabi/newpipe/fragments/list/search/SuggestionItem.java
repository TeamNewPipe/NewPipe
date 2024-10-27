package org.schabi.newpipe.fragments.list.search;

import androidx.annotation.NonNull;

import org.schabi.newpipe.database.history.model.SearchHistoryEntry;


public class SuggestionItem {
    final boolean fromHistory;
    public final String query;
    public boolean bookmark;

    public int serviceId;
    public long historyId;
    public SuggestionItem(final boolean fromHistory, final String query) {
        this.fromHistory = fromHistory;
        this.query = query;
        this.bookmark = false;
    }

    public SuggestionItem(final SearchHistoryEntry entry) {
        this.fromHistory = true;
        this.query = entry.getSearch();
        this.bookmark = entry.getBookmark();
        this.serviceId = entry.getServiceId();
        this.historyId = entry.getId();

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
