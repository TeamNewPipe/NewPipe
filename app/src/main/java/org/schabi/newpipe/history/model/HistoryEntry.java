package org.schabi.newpipe.history.model;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;

import java.util.Date;

@Entity
public class HistoryEntry {

    private final Date creationDate;
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
