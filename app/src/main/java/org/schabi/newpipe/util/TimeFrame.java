package org.schabi.newpipe.util;

public class TimeFrame {
    public double startTime;
    public double endTime;
    public Object tag;

    public TimeFrame(double startTime, double endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }
}