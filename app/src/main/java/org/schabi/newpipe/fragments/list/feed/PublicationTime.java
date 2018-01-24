package org.schabi.newpipe.fragments.list.feed;

/*
 * Created by wojcik.online on 2018-01-23.
 */

import android.support.annotation.NonNull;


/**
 * TODO Move this logic to the extractor.
 */
class PublicationTime implements Comparable<PublicationTime> {

    static final PublicationTime MAX_PUBLICATION_TIME = new PublicationTime(TimeAgo.WEEKS, 4);

    private final TimeAgo timeAgo;

    private final int timeValue;

    private PublicationTime(TimeAgo timeAgo, int timeValue) {
        this.timeAgo = timeAgo;
        this.timeValue = timeValue;
    }

    @Override
    public int compareTo(@NonNull PublicationTime otherTime) {
        int agoCompared = this.timeAgo.compareTo(otherTime.timeAgo);

        if (agoCompared == 0) {
            return this.timeValue - otherTime.timeValue;
        } else {
            return agoCompared;
        }
    }

    static PublicationTime parse(String publicationTimeStr) {
        try {
            String timeValueStr = publicationTimeStr.replaceAll("\\D+", "");
            int timeValue = Integer.parseInt(timeValueStr);

            TimeAgo timeAgo = parseTimeAgo(publicationTimeStr);

            return new PublicationTime(timeAgo, timeValue);
        }
        catch (NumberFormatException e) {
            return new PublicationTime(TimeAgo.SECONDS, 0);
        }
    }

    private static TimeAgo parseTimeAgo(String publicationTimeStr) {
        for (TimeAgo timeAgo : TimeAgo.values()) {
            if (publicationTimeStr.contains(timeAgo.containedPhrase)) {
                return timeAgo;
            }
        }

        return TimeAgo.UNKNOWN;
    }


    /**
     * TODO Introduce support for multiple languages.
     */
    private enum TimeAgo {
        SECONDS("sec"),
        MINUTES("min"),
        HOURS("hour"),
        DAYS("day"),
        WEEKS("week"),
        MONTHS("month"),
        YEARS("year"),
        UNKNOWN(""),
        ;

        private final String containedPhrase;

        TimeAgo(String containedPhrase) {
            this.containedPhrase = containedPhrase;
        }
    }

}
