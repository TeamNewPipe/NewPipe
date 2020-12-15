package org.schabi.newpipe.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.R;

import java.util.ArrayList;

public class MarkableSeekBar extends AppCompatSeekBar {
    public ArrayList<SeekBarMarker> seekBarMarkers = new ArrayList<>();
    private Drawable originalProgressDrawable;

    public MarkableSeekBar(final Context context) {
        super(context);
    }

    public MarkableSeekBar(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public MarkableSeekBar(final Context context,
                           final AttributeSet attrs,
                           final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setProgressDrawable(final Drawable d) {
        super.setProgressDrawable(d);

        // stored for when we draw (and potentially re-draw) markers
        originalProgressDrawable = d;
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldW, final int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);

        // re-draw markers since the progress bar may have a different width
        drawMarkers();
    }

    public void drawMarkers() {
        if (seekBarMarkers.size() == 0) {
            return;
        }

        // Markers are drawn like so:
        //
        //  - LayerDrawable (original drawable for the SeekBar)
        //    - GradientDrawable (background)
        //    - ScaleDrawable (secondaryProgress)
        //    - ScaleDrawable (progress)
        //    - LayerDrawable (we add our markers in a sub-LayerDrawable)
        //      - Drawable (marker)
        //      - Drawable (marker)
        //      - Drawable (marker)
        //      - etc...

        final int width = getMeasuredWidth() - (getPaddingStart() + getPaddingEnd());

        LayerDrawable layerDrawable = (LayerDrawable) originalProgressDrawable;

        final ArrayList<Drawable> markerDrawables = new ArrayList<>();
        markerDrawables.add(layerDrawable);

        for (final SeekBarMarker seekBarMarker : seekBarMarkers) {
            @SuppressLint("PrivateResource")
            final Drawable markerDrawable =
                    ContextCompat.getDrawable(
                            getContext(),
                            R.drawable.abc_scrubber_primary_mtrl_alpha);

            final PorterDuffColorFilter colorFilter =
                    new PorterDuffColorFilter(seekBarMarker.color, PorterDuff.Mode.SRC_IN);

            assert markerDrawable != null;
            markerDrawable.setColorFilter(colorFilter);

            markerDrawables.add(markerDrawable);
        }

        layerDrawable = new LayerDrawable(markerDrawables.toArray(new Drawable[0]));

        for (int i = 1; i < layerDrawable.getNumberOfLayers(); i++) {
            final SeekBarMarker seekBarMarker = seekBarMarkers.get(i - 1);
            final int l = (int) (width * seekBarMarker.percentStart);
            final int r = (int) (width * (1.0 - seekBarMarker.percentEnd));

            layerDrawable.setLayerInset(i, l, 0, r, 0);
        }

        super.setProgressDrawable(layerDrawable);
    }

    public void clearMarkers() {
        seekBarMarkers.clear();
        super.setProgressDrawable(originalProgressDrawable);
    }
}
