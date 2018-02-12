package org.schabi.newpipe.database.history.model;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Ignore;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;

import java.util.Date;

import static org.schabi.newpipe.database.history.model.SearchHistoryEntry.SEARCH;

@Entity(tableName = SearchHistoryEntry.TABLE_NAME,
        indices = {@Index(value = SEARCH)})
public class SearchHistoryEntry {

    public static final String ID = "id";
    public static final String TABLE_NAME = "search_history";
    public static final String SERVICE_ID = "service_id";
    public static final String CREATION_DATE = "creation_date";
    public static final String SEARCH = "search";

    @ColumnInfo(name = ID)
    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = CREATION_DATE)
    private Date creationDate;

    @ColumnInfo(name = SERVICE_ID)
    private int serviceId;

    @ColumnInfo(name = SEARCH)
    private String search;

    public SearchHistoryEntry(Date creationDate, int serviceId, String search) {
        this.serviceId = serviceId;
        this.creationDate = creationDate;
        this.search = search;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    public int getServiceId() {
        return serviceId;
    }

    public void setServiceId(int serviceId) {
        this.serviceId = serviceId;
    }

    public String getSearch() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search;
    }

    @Ignore
    public boolean hasEqualValues(SearchHistoryEntry otherEntry) {
        return getServiceId() == otherEntry.getServiceId() &&
                getSearch().equals(otherEntry.getSearch());
    }
}
