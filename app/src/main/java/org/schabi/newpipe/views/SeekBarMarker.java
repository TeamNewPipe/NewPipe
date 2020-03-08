package org.schabi.newpipe.views;

import android.graphics.Paint;

public class SeekBarMarker {
    public double startTime;
    public double endTime;
    public double percentStart;
    public double percentEnd;
    public Paint paint;
    public Object tag;

    public SeekBarMarker(double startTime, double endTime, int maxTime, int color) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.percentStart = Math.round((startTime / maxTime) * 100.0) / 100.0;
        this.percentEnd = Math.round((endTime / maxTime) * 100.0) / 100.0;

        initPaint(color);
    }

    public SeekBarMarker(double percentStart, double percentEnd, int color) {
        this.percentStart = percentStart;
        this.percentEnd = percentEnd;

        initPaint(color);
    }

    private void initPaint(int color) {
        this.paint = new Paint();
        this.paint.setColor(color);
    }
}