package org.schabi.newpipe.fragments.list.feed;

/*
 * Created by wojcik.online on 2018-01-30.
 */

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.Serializable;
import java.util.Calendar;
import java.util.List;

/**
 * Holds the information displayed by the "What's New" feed.
 */
class FeedInfo implements Serializable {

    /** Represents a hash that always compares to not equal. */
    static final long INVALID_CONTENT_HASH = 0;

    private final Calendar lastUpdated;

    private final List<StreamInfoItem> infoItems;

    private final long contentHash;

    /**
     * Creates a container that holds all information displayed by the feed.
     * @param lastUpdated The time items have been loaded
     * @param infoItems The list of news in the feed
     * @param contentHash A hash used for quick comparisons
     */
    FeedInfo(@NonNull Calendar lastUpdated,
             @NonNull List<StreamInfoItem> infoItems,
             long contentHash) {
        this.lastUpdated = lastUpdated;
        this.infoItems = infoItems;
        this.contentHash = contentHash;
    }

    /**
     * @return The time the items have been loaded.
     */
    @NonNull
    Calendar getLastUpdated() {
        return lastUpdated;
    }

    /**
     * @return The list of news in the feed.
     */
    @NonNull
    List<StreamInfoItem> getInfoItems() {
        return infoItems;
    }

    /**
     * Checks whether the info is newer by comparing the hash and the lastUpdated time.
     * @param other The other feed info to compare to (or {@code null})
     * @return Whether this feed info is newer
     */
    boolean isNewerThan(@Nullable FeedInfo other) {
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
