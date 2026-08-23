package org.schabi.newpipe.views;

public class SeekBarMarker {
    public double startTime;
    public double endTime;
    public double percentStart;
    public double percentEnd;
    public int color;

    public SeekBarMarker(final double startTime,
                         final double endTime,
                         final long maxTime,
                         final int color) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.percentStart = ((startTime / maxTime) * 100.0) / 100.0;
        this.percentEnd = ((endTime / maxTime) * 100.0) / 100.0;
        this.color = color;
    }

    public SeekBarMarker(final double percentStart, final double percentEnd, final int color) {
        this.percentStart = percentStart;
        this.percentEnd = percentEnd;
        this.color = color;
    }
}
