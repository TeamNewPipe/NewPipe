package org.schabi.newpipe.about;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Class for storing information about a software license.
 */
public class License implements Parcelable {
    public static final Creator<License> CREATOR = new Creator<License>() {
        @Override
        public License createFromParcel(final Parcel source) {
            return new License(source);
        }

        @Override
        public License[] newArray(final int size) {
            return new License[size];
        }
    };
    private final String abbreviation;
    private final String name;
    private String filename;

    public License(final String name, final String abbreviation, final String filename) {
        if (name == null) {
            throw new NullPointerException("name is null");
        }
        if (abbreviation == null) {
            throw new NullPointerException("abbreviation is null");
        }
        if (filename == null) {
            throw new NullPointerException("filename is null");
        }
        this.name = name;
        this.filename = filename;
        this.abbreviation = abbreviation;
    }

    protected License(final Parcel in) {
        this.filename = in.readString();
        this.abbreviation = in.readString();
        this.name = in.readString();
    }

    public Uri getContentUri() {
        return new Uri.Builder()
                .scheme("file")
                .path("/android_asset")
                .appendPath(filename)
                .build();
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    public String getFilename() {
        return filename;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeString(this.filename);
        dest.writeString(this.abbreviation);
        dest.writeString(this.name);
    }

    public String getName() {
        return name;
    }
}
