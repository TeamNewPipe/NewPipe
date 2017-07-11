package org.schabi.newpipe.history.model;


import android.arch.persistence.room.Entity;

import java.util.Date;

@Entity(tableName = "search_history")
public class SearchHistoryEntry extends HistoryEntry {

    private final String search;

    public SearchHistoryEntry(Date creationDate, int serviceId, String search) {
        super(creationDate, serviceId);
        this.search = search;
    }

    public String getSearch() {
        return search;
    }
}
