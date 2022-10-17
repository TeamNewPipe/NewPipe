package org.schabi.newpipe.util;

import java.io.Serializable;

public class SponsorBlockSegment implements Serializable {
    public String uuid;
    public double startTime;
    public double endTime;
    public SponsorBlockCategory category;
    public SponsorBlockAction action;

    public SponsorBlockSegment(final String uuid, final double startTime, final double endTime,
                               final SponsorBlockCategory category,
                               final SponsorBlockAction action) {
        // NOTE: start/end times are in milliseconds

        this.uuid = uuid;
        this.startTime = startTime;
        this.endTime = endTime;
        this.category = category;
        this.action = action;

        // since "highlight" segments are marked with the same start time and end time,
        // increment the end time by 1 second (so it is actually visible on the seekbar)
        if (this.category == SponsorBlockCategory.HIGHLIGHT) {
            this.endTime = this.startTime + 1000;
        }
    }
}
