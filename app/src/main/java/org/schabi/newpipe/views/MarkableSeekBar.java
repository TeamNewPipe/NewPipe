package org.schabi.newpipe.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatSeekBar;

import java.util.ArrayList;

public class MarkableSeekBar extends AppCompatSeekBar {
    public ArrayList<SeekBarMarker> seekBarMarkers = new ArrayList<>();
    private RectF markerRect = new RectF();

    public MarkableSeekBar(Context context) {
        super(context);
    }

    public MarkableSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MarkableSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    protected synchronized void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        Drawable progressDrawable = getProgressDrawable();
        Rect progressDrawableBounds = progressDrawable.getBounds();

        int width = getMeasuredWidth() - (getPaddingStart() + getPaddingEnd());
        int height = progressDrawable.getIntrinsicHeight();

        for (int i = 0; i < seekBarMarkers.size(); i++) {
            SeekBarMarker marker = seekBarMarkers.get(i);

            markerRect.left = width - (float) Math.floor(width * (1.0 - marker.percentStart)) + getPaddingStart();
            markerRect.top = progressDrawableBounds.bottom - height - 1;
            markerRect.right = width - (float) Math.ceil(width * (1.0 - marker.percentEnd)) + getPaddingStart();
            markerRect.bottom = progressDrawableBounds.bottom;

            canvas.drawRect(markerRect, marker.paint);
        }
    }
}