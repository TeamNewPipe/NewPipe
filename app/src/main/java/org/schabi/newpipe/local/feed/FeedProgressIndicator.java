package org.schabi.newpipe.local.feed;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import org.schabi.newpipe.R;

/**
 * A determinate feed progress indicator that draws its counter around the same center as its ring.
 */
public final class FeedProgressIndicator extends CircularProgressIndicator {
    private static final Typeface COUNTER_TYPEFACE = Typeface.create(
            "sans-serif-medium", Typeface.NORMAL);
    private final Paint counterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect counterBounds = new Rect();
    private String counterText = "";

    public FeedProgressIndicator(@NonNull final Context context) {
        this(context, null);
    }

    public FeedProgressIndicator(@NonNull final Context context,
                                 @Nullable final AttributeSet attrs) {
        super(context, attrs);
        counterPaint.setColor(MaterialColors.getColor(
                this, com.google.android.material.R.attr.colorOnSurface));
        counterPaint.setTextSize(getResources().getDimension(
                R.dimen.feed_progress_counter_text_size));
        counterPaint.setTypeface(COUNTER_TYPEFACE);
    }

    public void setCounterText(@Nullable final CharSequence text) {
        counterText = text == null ? "" : text.toString();
        setContentDescription(counterText);
        invalidate();
    }

    @Override
    protected synchronized void onDraw(@NonNull final Canvas canvas) {
        super.onDraw(canvas);
        if (counterText.isEmpty()) {
            return;
        }

        counterPaint.setTypeface(org.schabi.newpipe.util.AppFonts.typeface(
                getContext(), COUNTER_TYPEFACE));
        counterPaint.getTextBounds(counterText, 0, counterText.length(), counterBounds);
        final float centerX = (getPaddingLeft() + getWidth() - getPaddingRight()) / 2.0f;
        final float centerY = (getPaddingTop() + getHeight() - getPaddingBottom()) / 2.0f;
        final float textX = centerX - counterBounds.exactCenterX();
        final float baselineY = centerY - counterBounds.exactCenterY();
        canvas.drawText(counterText, textX, baselineY, counterPaint);
    }
}
