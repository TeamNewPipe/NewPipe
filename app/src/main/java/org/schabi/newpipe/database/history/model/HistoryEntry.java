package org.schabi.newpipe.database.history.model;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;

import java.util.Date;

@Entity
public class HistoryEntry {

    public static final String SERVICE_ID = "service_id";
    public static final String CREATION_DATE = "creation_date";

    @ColumnInfo(name = CREATION_DATE)
    private final Date creationDate;

    @ColumnInfo(name = SERVICE_ID)
    private final int serviceId;

    @PrimaryKey(autoGenerate = true)
    private long id;

    public HistoryEntry(Date creationDate, int serviceId) {
        this.serviceId = serviceId;
        this.creationDate = creationDate;
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

    public int getServiceId() {
        return serviceId;
    }
}
