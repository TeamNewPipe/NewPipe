package us.shandian.giga.get;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.Calendar;

import us.shandian.giga.io.StoredFileHelper;

public abstract class Mission implements Serializable {
    private static final long serialVersionUID = 1L;// last bump: 27 march 2019

    /**
     * Source url of the resource
     */
    public String source;

    /**
     * Length of the current resource
     */
    public long length;

    /**
     * creation timestamp (and maybe unique identifier)
     */
    public long timestamp;

    /**
     * pre-defined content type
     */
    public char kind;

    /**
     * The downloaded file
     */
    public StoredFileHelper storage;

    /**
     * Delete the downloaded file
     *
     * @return {@code true] if and only if the file is successfully deleted, otherwise, {@code false}
     */
    public boolean delete() {
        if (storage != null) return storage.delete();
        return true;
    }

    /**
     * Indicate if this mission is deleted whatever is stored
     */
    public transient boolean deleted = false;

    @NonNull
    @Override
    public String toString() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        return "[" + calendar.getTime().toString() + "] " + (storage.isInvalid() ? storage.getName() : storage.getUri());
    }
}
