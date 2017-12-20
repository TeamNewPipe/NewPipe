package org.schabi.newpipe.database.history.model;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Ignore;

import java.util.Date;

@Entity(tableName = SearchHistoryEntry.TABLE_NAME)
public class SearchHistoryEntry extends HistoryEntry {

    public static final String TABLE_NAME = "search_history";
    public static final String SEARCH = "search";

    @ColumnInfo(name = SEARCH)
    private String search;

    public SearchHistoryEntry(Date creationDate, int serviceId, String search) {
        super(creationDate, serviceId);
        this.search = search;
    }

    public String getSearch() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search;
    }

    @Ignore
    @Override
    public boolean hasEqualValues(HistoryEntry otherEntry) {
        return otherEntry instanceof SearchHistoryEntry && super.hasEqualValues(otherEntry)
                && getSearch().equals(((SearchHistoryEntry) otherEntry).getSearch());
    }
}
