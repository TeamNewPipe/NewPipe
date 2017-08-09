package org.schabi.newpipe.database.history.model;


import android.arch.persistence.room.Entity;

import java.util.Date;

@Entity(tableName = SearchHistoryEntry.TABLE_NAME)
public class SearchHistoryEntry extends HistoryEntry {

    public static final String TABLE_NAME = "search_history";
    private final String search;

    public SearchHistoryEntry(Date creationDate, int serviceId, String search) {
        super(creationDate, serviceId);
        this.search = search;
    }

    public String getSearch() {
        return search;
    }
}
