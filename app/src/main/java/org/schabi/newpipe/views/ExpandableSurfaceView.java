package org.schabi.newpipe.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceView;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;

import static com.google.android.exoplayer2.ui.AspectRatioFrameLayout.*;

public class ExpandableSurfaceView extends SurfaceView {
    private int resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
    private int baseHeight = 0;
    private int maxHeight = 0;
    private float videoAspectRatio = 0.0f;
    private float scaleX = 1.0f;
    private float scaleY = 1.0f;

    public ExpandableSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (videoAspectRatio == 0.0f) return;

        int width = MeasureSpec.getSize(widthMeasureSpec);
        final boolean verticalVideo = videoAspectRatio < 1;
        // Use maxHeight only on non-fit resize mode and in vertical videos
        int height = maxHeight != 0 && resizeMode != AspectRatioFrameLayout.RESIZE_MODE_FIT && verticalVideo ? maxHeight : baseHeight;

        if (height == 0) return;

        final float viewAspectRatio = width / ((float) height);
        final float aspectDeformation = videoAspectRatio / viewAspectRatio - 1;
        scaleX = 1.0f;
        scaleY = 1.0f;

        switch (resizeMode) {
            case AspectRatioFrameLayout.RESIZE_MODE_FIT:
                if (aspectDeformation > 0) {
                    height = (int) (width / videoAspectRatio);
                } else {
                    width = (int) (height * videoAspectRatio);
                }

                break;
            case RESIZE_MODE_ZOOM:
                if (aspectDeformation < 0) {
                    scaleY = viewAspectRatio / videoAspectRatio;
                } else {
                    scaleX = videoAspectRatio / viewAspectRatio;
                }

                break;
            default:
                break;
        }
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    /**
     * Scale view only in {@link #onLayout} to make transition for ZOOM mode as smooth as possible
     */
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        setScaleX(scaleX);
        setScaleY(scaleY);
    }

    /**
     * @param base The height that will be used in every resize mode as a minimum height
     * @param max The max height for vertical videos in non-FIT resize modes
     */
    public void setHeights(final int base, final int max) {
        if (baseHeight == base && maxHeight == max) return;
        baseHeight = base;
        maxHeight = max;
        requestLayout();
    }

    public void setResizeMode(@AspectRatioFrameLayout.ResizeMode final int newResizeMode) {
        if (resizeMode == newResizeMode) return;

        resizeMode = newResizeMode;
        requestLayout();
    }

    @AspectRatioFrameLayout.ResizeMode
    public int getResizeMode() {
        return resizeMode;
    }

    public void setAspectRatio(final float aspectRatio) {
        if (videoAspectRatio == aspectRatio) return;

        videoAspectRatio = aspectRatio;
        requestLayout();
    }
}