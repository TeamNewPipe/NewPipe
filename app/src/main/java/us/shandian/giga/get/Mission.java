package us.shandian.giga.get;

import java.io.File;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public abstract class Mission implements Serializable {
    private static final long serialVersionUID = 0L;// last bump: 5 october 2018

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
     * The filename
     */
    public String name;

    /**
     * The directory to store the download
     */
    public String location;

    /**
     * pre-defined content type
     */
    public char kind;

    /**
     * get the target file on the storage
     *
     * @return File object
     */
    public File getDownloadedFile() {
        return new File(location, name);
    }

    public boolean delete() {
        deleted = true;
        return getDownloadedFile().delete();
    }

    /**
     * Indicate if this mission is deleted whatever is stored
     */
    public transient boolean deleted = false;

    @Override
    public String toString() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        return "[" + calendar.getTime().toString() + "] " + location + File.separator + name;
    }
}
