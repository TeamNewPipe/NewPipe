package org.schabi.newpipe.util;

public class VideoSegment {
    public double startTime;
    public double endTime;
    public String category;

    public VideoSegment(final double startTime, final double endTime, final String category) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.category = category;
    }
}
