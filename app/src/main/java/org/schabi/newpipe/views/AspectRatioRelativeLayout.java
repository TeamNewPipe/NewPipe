package org.schabi.newpipe.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.IdRes;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import org.schabi.newpipe.R;

public class AspectRatioRelativeLayout extends RelativeLayout {

    protected double aspectRatio = 18f / 13f;
    @IdRes
    protected int operatedChildId = R.id.itemThumbnailView;
    @Nullable
    private View operatedChild = null;

    public AspectRatioRelativeLayout(Context context) {
        this(context, null, 0);
    }

    public AspectRatioRelativeLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AspectRatioRelativeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (attrs != null) {
            final TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.AspectRatioRelativeLayout);
            float rawAspectRatio = attributes.getFloat(R.styleable.AspectRatioRelativeLayout_aspectRatio, 0);
            if (rawAspectRatio != 0) {
                setAspectRatio(rawAspectRatio);
            } else {
                int heigth = attributes.getDimensionPixelSize(R.styleable.AspectRatioRelativeLayout_aspectRatio_height, 0);
                int width = attributes.getDimensionPixelSize(R.styleable.AspectRatioRelativeLayout_aspectRatio_width, 0);
                if (heigth > 0 && width > 0) {
                    setAspectRatio(heigth, width);
                }
            }
            attributes.recycle();
        }
    }

    public void setAspectRatio(float value) {
        aspectRatio = value;
    }

    public void setAspectRatio(int height, int width) {
        aspectRatio = height / (float) width;
    }

    public double getAspectRatio() {
        return aspectRatio;
    }

    private View getOperatedChild() {
        if (operatedChild == null) {
            operatedChild = findViewById(operatedChildId);
        }
        return operatedChild;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        final View view = getOperatedChild();
        if (view != null) {
            final ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            final int width = w - getPaddingLeft() - getPaddingRight();
            if (width != layoutParams.width && width > 0) {
                int height = (int) (width * aspectRatio);
                layoutParams.width = width;
                layoutParams.height = height;
            }
        }
        super.onSizeChanged(w, h, oldw, oldh);
    }
}
