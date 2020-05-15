package org.schabi.newpipe.views;

import android.graphics.Paint;

public class SeekBarMarker {
    public double startTime;
    public double endTime;
    public double percentStart;
    public double percentEnd;
    public Paint paint;
    public Object tag;

    public SeekBarMarker(final double startTime,
                         final double endTime,
                         final int maxTime,
                         final int color) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.percentStart = Math.round((startTime / maxTime) * 100.0) / 100.0;
        this.percentEnd = Math.round((endTime / maxTime) * 100.0) / 100.0;

        initPaint(color);
    }

    public SeekBarMarker(final double percentStart, final double percentEnd, final int color) {
        this.percentStart = percentStart;
        this.percentEnd = percentEnd;

        initPaint(color);
    }

    private void initPaint(final int color) {
        this.paint = new Paint();
        this.paint.setColor(color);
    }
}
