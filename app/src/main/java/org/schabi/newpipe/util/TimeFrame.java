package org.schabi.newpipe.util;

public class TimeFrame {
    public double startTime;
    public double endTime;
    public Object tag;

    public TimeFrame(final double startTime, final double endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
