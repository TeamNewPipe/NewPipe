package org.schabi.newpipe.util;

import java.io.Serializable;

public class VideoSegment implements Serializable {
    public double startTime;
    public double endTime;
    public String category;

    public VideoSegment(final double startTime, final double endTime, final String category) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.category = category;
    }
}
