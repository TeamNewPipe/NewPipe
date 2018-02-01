package org.schabi.newpipe.fragments.list.feed;

/*
 * Created by wojcik.online on 2018-01-30.
 */

import android.support.annotation.NonNull;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.Serializable;
import java.util.Calendar;
import java.util.List;

class FeedInfo implements Serializable {

    static final long INVALID_CONTENT_HASH = 0;

    private final Calendar lastUpdated;

    private final List<StreamInfoItem> infoItems;

    private final long contentHash;

    FeedInfo(@NonNull Calendar lastUpdated,
             @NonNull List<StreamInfoItem> infoItems,
             long contentHash) {
        this.lastUpdated = lastUpdated;
        this.infoItems = infoItems;
        this.contentHash = contentHash;
    }

    @NonNull
    Calendar getLastUpdated() {
        return lastUpdated;
    }

    @NonNull
    List<StreamInfoItem> getInfoItems() {
        return infoItems;
    }

    boolean isNewerThan(FeedInfo other) {
        return other == null
                || other.contentHash == INVALID_CONTENT_HASH
                || (this.contentHash != other.contentHash
                        && this.lastUpdated.compareTo(other.lastUpdated) > 0);

    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ":" +
                "\n\tLast updated: " + lastUpdated.getTime() +
                "\n\tItems: [" + infoItems.size() + "] " + infoItems;
    }
}
