package org.schabi.newpipe.views;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.schabi.newpipe.R;

import java.lang.ref.WeakReference;

public final class FocusOverlayView extends Drawable implements
        ViewTreeObserver.OnGlobalFocusChangeListener,
        ViewTreeObserver.OnDrawListener,
        ViewTreeObserver.OnGlobalLayoutListener,
        ViewTreeObserver.OnScrollChangedListener {

    private final Rect focusRect = new Rect();

    private final Paint rectPaint = new Paint();

    private final Handler animator = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            updateRect();
        }
    };

    private WeakReference<View> focused;

    public FocusOverlayView(Context context) {
        rectPaint.setStyle(Paint.Style.STROKE);
        rectPaint.setStrokeWidth(2);
        rectPaint.setColor(context.getResources().getColor(R.color.white));
    }

    @Override
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        int l = focusRect.left;
        int r = focusRect.right;
        int t = focusRect.top;
        int b = focusRect.bottom;

        if (newFocus != null && newFocus.getWidth() > 0 && newFocus.getHeight() > 0) {
            newFocus.getGlobalVisibleRect(focusRect);

            focused = new WeakReference<>(newFocus);
        } else {
            focusRect.setEmpty();

            focused = null;
        }

        if (l != focusRect.left || r != focusRect.right || t != focusRect.top || b != focusRect.bottom) {
            invalidateSelf();
        }

        focused = new WeakReference<>(newFocus);
    }

    private void updateRect() {
        if (focused == null) {
            return;
        }

        View focused = this.focused.get();

        int l = focusRect.left;
        int r = focusRect.right;
        int t = focusRect.top;
        int b = focusRect.bottom;

        if (focused != null) {
            focused.getGlobalVisibleRect(focusRect);
        } else {
            focusRect.setEmpty();
        }

        if (l != focusRect.left || r != focusRect.right || t != focusRect.top || b != focusRect.bottom) {
            invalidateSelf();
        }
    }

    @Override
    public void onDraw() {
        updateRect();
    }

    @Override
    public void onScrollChanged() {
        updateRect();
    }

    @Override
    public void onGlobalLayout() {
        updateRect();

        animator.sendEmptyMessageDelayed(0, 1000);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (focusRect.width() != 0) {
            canvas.drawRect(focusRect, rectPaint);
        }
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    public static void setupFocusObserver(Activity activity) {
        DisplayMetrics display = activity.getResources().getDisplayMetrics();
        FocusOverlayView overlay = new FocusOverlayView(activity);
        overlay.setBounds(0, 0, display.widthPixels, display.heightPixels);

        Window window = activity.getWindow();
        ViewGroup decor = (ViewGroup) window.getDecorView();
        decor.getOverlay().add(overlay);

        ViewTreeObserver observer = decor.getViewTreeObserver();
        observer.addOnScrollChangedListener(overlay);
        observer.addOnGlobalFocusChangeListener(overlay);
        observer.addOnGlobalLayoutListener(overlay);
    }
}
