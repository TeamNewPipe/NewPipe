/*
 * Copyright (C) NewPipe Contributors
 * ChaptersSeekBar.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.schabi.newpipe.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.stream.StreamSegment;

import java.util.Collections;
import java.util.List;

/**
 * A {@link FocusAwareSeekBar} that draws thin white vertical tick marks at chapter boundaries.
 * Call {@link #setChapters(List, long)} whenever a new stream loads.
 */
public final class ChaptersSeekBar extends FocusAwareSeekBar {

    private static final String TAG = "ChaptersSeekBar";

    private static final int   TICK_ALPHA           = 180;  // ~70% opacity
    private static final float TICK_WIDTH_DP        = 2f;
    private static final float TICK_HEIGHT_FRACTION = 0.6f; // fraction of view height

    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @NonNull private List<StreamSegment> chapters = Collections.emptyList();
    private long durationSeconds = 0;

    public ChaptersSeekBar(@NonNull final Context context) {
        super(context);
        init();
    }

    public ChaptersSeekBar(@NonNull final Context context,
                           @Nullable final AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ChaptersSeekBar(@NonNull final Context context,
                           @Nullable final AttributeSet attrs,
                           final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        tickPaint.setColor(Color.WHITE);
        tickPaint.setAlpha(TICK_ALPHA);
        tickPaint.setStyle(Paint.Style.FILL);
        Log.d(TAG, "init: ChaptersSeekBar created");
    }

    /**
     * Stores chapter data for rendering tick marks.
     *
     * @param newChapters     list of {@link StreamSegment}s; may be empty but never null
     * @param newDurationSecs total duration in seconds; used to compute fractional positions
     */
    public void setChapters(@NonNull final List<StreamSegment> newChapters,
                            final long newDurationSecs) {
        chapters = newChapters;
        durationSeconds = newDurationSecs;
        Log.d(TAG, "setChapters: count=" + newChapters.size()
                + " durationSeconds=" + newDurationSecs);
        for (final StreamSegment seg : newChapters) {
            Log.d(TAG, "  chapter: startSec=" + seg.getStartTimeSeconds()
                    + " title=" + seg.getTitle());
        }
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull final Canvas canvas) {
        super.onDraw(canvas);

        if (chapters.isEmpty() || durationSeconds <= 0) {
            Log.d(TAG, "onDraw: skipped — chapters=" + chapters.size()
                    + " durationSeconds=" + durationSeconds);
            return;
        }

        final float density = getResources().getDisplayMetrics().density;
        final float tickWidthPx = TICK_WIDTH_DP * density;

        // Track bounds: AbsSeekBar pads the track by getPaddingLeft/getPaddingRight
        final int paddingLeft  = getPaddingLeft();
        final int paddingRight = getPaddingRight();
        final float trackWidth = getWidth() - paddingLeft - paddingRight;

        Log.d(TAG, "onDraw: w=" + getWidth() + " h=" + getHeight()
                + " paddingL=" + paddingLeft + " paddingR=" + paddingRight
                + " trackWidth=" + trackWidth + " chapters=" + chapters.size()
                + " durationSeconds=" + durationSeconds);

        if (trackWidth <= 0) {
            Log.d(TAG, "onDraw: trackWidth<=0, skipping");
            return;
        }

        // Center ticks vertically, scaling height as a fraction of the view
        final float tickHeight = getHeight() * TICK_HEIGHT_FRACTION;
        final float tickTop    = (getHeight() - tickHeight) / 2f;
        final float tickBottom = tickTop + tickHeight;

        for (final StreamSegment seg : chapters) {
            final int startSec = seg.getStartTimeSeconds();
            // Skip the very beginning and anything at or past the end
            if (startSec <= 0 || startSec >= durationSeconds) {
                Log.d(TAG, "  skipping seg startSec=" + startSec);
                continue;
            }
            final float x = paddingLeft + (startSec / (float) durationSeconds) * trackWidth;
            Log.d(TAG, "  drawing tick at x=" + x + " for startSec=" + startSec
                    + " title=" + seg.getTitle());
            canvas.drawRect(
                    x - tickWidthPx / 2f,
                    tickTop,
                    x + tickWidthPx / 2f,
                    tickBottom,
                    tickPaint);
        }
    }
}
