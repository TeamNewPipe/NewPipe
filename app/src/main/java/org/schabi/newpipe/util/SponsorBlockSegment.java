package org.schabi.newpipe.util;

import java.io.Serializable;

public class SponsorBlockSegment implements Serializable {
    public String uuid;
    public double startTime;
    public double endTime;
    public SponsorBlockCategory category;

    public SponsorBlockSegment(final String uuid, final double startTime, final double endTime,
                               final SponsorBlockCategory category) {
        this.uuid = uuid;
        this.startTime = startTime;
        this.endTime = endTime;
        this.category = category;
    }
}
