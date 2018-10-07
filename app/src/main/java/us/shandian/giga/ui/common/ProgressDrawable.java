package us.shandian.giga.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorRes;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;

public class ProgressDrawable extends Drawable {
    private float mProgress;
    private final int mBackgroundColor;
    private final int mForegroundColor;

    public ProgressDrawable(Context context, @ColorRes int background, @ColorRes int foreground) {
        this(ContextCompat.getColor(context, background), ContextCompat.getColor(context, foreground));
    }

    public ProgressDrawable(int background, int foreground) {
        mBackgroundColor = background;
        mForegroundColor = foreground;
    }

    public void setProgress(float progress) {
        mProgress = progress;
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        int width = canvas.getWidth();
        int height = canvas.getHeight();

        Paint paint = new Paint();

        paint.setColor(mBackgroundColor);
        canvas.drawRect(0, 0, width, height, paint);

        paint.setColor(mForegroundColor);
        canvas.drawRect(0, 0, (int) (mProgress * width), height, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        // Unsupported
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        // Unsupported
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }

}
