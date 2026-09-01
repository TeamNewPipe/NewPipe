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
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.stream.StreamSegment;

import java.util.Collections;
import java.util.List;

/**
 * A {@link FocusAwareSeekBar} that renders narrow transparent gaps at chapter boundaries,
 * giving the seekbar a segmented "chopped" appearance.
 * Call {@link #setChapters(List, long)} whenever a new stream loads.
 */
public final class ChaptersSeekBar extends FocusAwareSeekBar {

    private static final float GAP_WIDTH_DP = 2f;

    private final Paint gapPaint = new Paint();

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
        gapPaint.setStyle(Paint.Style.FILL);
        gapPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    /**
     * Stores chapter data for rendering segment gaps.
     *
     * @param newChapters     list of {@link StreamSegment}s; may be empty but never null
     * @param newDurationSecs total duration in seconds; used to compute fractional positions
     */
    public void setChapters(@NonNull final List<StreamSegment> newChapters,
                            final long newDurationSecs) {
        chapters = newChapters;
        durationSeconds = newDurationSecs;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull final Canvas canvas) {
        if (chapters.isEmpty() || durationSeconds <= 0) {
            super.onDraw(canvas);
            return;
        }

        // Draw the seekbar into an offscreen layer so CLEAR mode can punch transparent gaps
        final int sc = canvas.saveLayer(null, null);
        super.onDraw(canvas);

        final float density = getResources().getDisplayMetrics().density;
        final float gapHalfWidth = (GAP_WIDTH_DP * density) / 2f;
        final int paddingLeft = getPaddingLeft();
        final float trackWidth = getWidth() - paddingLeft - getPaddingRight();

        if (trackWidth > 0) {
            for (final StreamSegment seg : chapters) {
                final int startSec = seg.getStartTimeSeconds();
                // Skip the very first position and anything at or past the end
                if (startSec <= 0 || startSec >= durationSeconds) {
                    continue;
                }
                final float x = paddingLeft + (startSec / (float) durationSeconds) * trackWidth;
                canvas.drawRect(x - gapHalfWidth, 0, x + gapHalfWidth, getHeight(), gapPaint);
            }
        }

        canvas.restoreToCount(sc);

        // Redraw the thumb on top so it visually overlaps the gaps
        final Drawable thumb = getThumb();
        if (thumb != null) {
            final int thumbSave = canvas.save();
            canvas.translate(getPaddingLeft() - getThumbOffset(), getPaddingTop());
            thumb.draw(canvas);
            canvas.restoreToCount(thumbSave);
        }
    }
}
