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

    private final Calendar lastUpdated;

    private final List<StreamInfoItem> infoItems;


    FeedInfo(@NonNull Calendar lastUpdated, @NonNull List<StreamInfoItem> infoItems) {
        this.lastUpdated = lastUpdated;
        this.infoItems = infoItems;
    }

    @NonNull
    Calendar getLastUpdated() {
        return lastUpdated;
    }

    @NonNull
    List<StreamInfoItem> getInfoItems() {
        return infoItems;
    }

    boolean isOlderThan(FeedInfo other) {
        if (this.infoItems.isEmpty() && !other.infoItems.isEmpty()) {
            return true;
        }

        if (!this.infoItems.isEmpty() && !other.infoItems.isEmpty()) {
            StreamInfoItem item1 = this.infoItems.get(0);
            StreamInfoItem item2 = other.infoItems.get(0);

            return item1.getServiceId() != item2.getServiceId()
                    || !item1.getUrl().equals(item2.getUrl());
        }

        return false;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ":" +
                "\n\tLast updated: " + lastUpdated.getTime() +
                "\n\tItems: [" + infoItems.size() + "] " + infoItems;
    }
}
