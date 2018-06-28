package us.shandian.giga.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorRes;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;

import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class ProgressDrawable extends Drawable {
    private float mProgress;
    private int mBackgroundColor, mForegroundColor;
    private float mMarqueeProgress;
    private Path mMarqueeLine;
    private Disposable mMarqueeThread;
    private int mMarqueeSize;
    private boolean mMarqueeUpdated;

    public ProgressDrawable(Context context, @ColorRes int background, @ColorRes int foreground) {
        this(ContextCompat.getColor(context, background), ContextCompat.getColor(context, foreground));
    }

    public ProgressDrawable(int background, int foreground) {
        mBackgroundColor = background;
        mForegroundColor = foreground;
        mMarqueeLine = null;// marquee disabled
        mMarqueeProgress = 0f;
        mMarqueeThread = null;
        mMarqueeUpdated = true;
        mMarqueeSize = 0;
    }

    public void setProgress(float progress) {
        mProgress = progress;
        invalidateSelf();
    }

    public void setMarquee(boolean marquee) {
        if (marquee == (mMarqueeLine != null)) {
            return;
        }

        if (marquee) {
            mMarqueeLine = new Path();
            setupMarquee();
        } else {
            mMarqueeThread.dispose();
            mMarqueeThread = null;
            mMarqueeLine = null;
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        int width = canvas.getWidth();
        int height = canvas.getHeight();

        Paint paint = new Paint();

        paint.setColor(mBackgroundColor);
        canvas.drawRect(0, 0, width, height, paint);

        paint.setColor(mForegroundColor);

        if (mMarqueeLine != null) {
            mMarqueeUpdated = true;
            if (mMarqueeThread.isDisposed()) {
                setupMarquee();// Resume
            }

            int size = mMarqueeSize;

            Paint paint2 = new Paint();
            paint2.setColor(mForegroundColor);
            paint2.setStrokeWidth(size);
            paint2.setStyle(Paint.Style.STROKE);

            size *= 2;

            if (mMarqueeProgress >= size) {
                mMarqueeProgress = 1;
            } else {
                mMarqueeProgress++;
            }

            // render marquee
            width += size * 2;
            Path marquee = new Path();
            for (float i = -size; i < width; i += size) {
                marquee.addPath(mMarqueeLine, i + mMarqueeProgress, 0);
            }
            marquee.close();

            canvas.drawPath(marquee, paint2);// draw marquee
            return;
        }

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

    @Override
    public void onBoundsChange(Rect rect) {
        if (mMarqueeLine != null) {
            mMarqueeSize = (int) ((rect.width() * 5f) / 100f);// the size is 5% of the width

            mMarqueeLine.rewind();
            mMarqueeLine.moveTo(-mMarqueeSize, -mMarqueeSize);
            mMarqueeLine.lineTo(-mMarqueeSize * 4, rect.height() + mMarqueeSize);
            mMarqueeLine.close();
        }
    }

    private void setupMarquee() {
        mMarqueeThread = Observable.interval(150, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((ts) -> {
                    if (!mMarqueeUpdated) {
                        mMarqueeThread.dispose();// drawable not used (screen off, activity close, etc..)
                        return;
                    }

                    mMarqueeUpdated = false;
                    invalidateSelf();
                });
    }
}
