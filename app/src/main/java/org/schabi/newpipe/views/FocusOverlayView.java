/*
 * Copyright 2019 Alexander Rvachev <rvacheva@nxt.ru>
 * FocusOverlayView.java is part of NewPipe
 *
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.schabi.newpipe.views;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.view.WindowCallbackWrapper;

import org.schabi.newpipe.R;

import java.lang.ref.WeakReference;

public final class FocusOverlayView extends Drawable implements
        ViewTreeObserver.OnGlobalFocusChangeListener,
        ViewTreeObserver.OnDrawListener,
        ViewTreeObserver.OnGlobalLayoutListener,
        ViewTreeObserver.OnScrollChangedListener, ViewTreeObserver.OnTouchModeChangeListener {

    private boolean isInTouchMode;

    private final Rect focusRect = new Rect();

    private final Paint rectPaint = new Paint();

    private final Handler animator = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(final Message msg) {
            updateRect();
        }
    };

    private WeakReference<View> focused;

    public FocusOverlayView(final Context context) {
        rectPaint.setStyle(Paint.Style.STROKE);
        rectPaint.setStrokeWidth(2);
        rectPaint.setColor(context.getResources().getColor(R.color.white));
    }

    @Override
    public void onGlobalFocusChanged(final View oldFocus, final View newFocus) {
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

        if (l != focusRect.left || r != focusRect.right
                || t != focusRect.top || b != focusRect.bottom) {
            invalidateSelf();
        }

        focused = new WeakReference<>(newFocus);

        animator.sendEmptyMessageDelayed(0, 1000);
    }

    private void updateRect() {
        if (focused == null) {
            return;
        }

        View focusedView = this.focused.get();

        int l = focusRect.left;
        int r = focusRect.right;
        int t = focusRect.top;
        int b = focusRect.bottom;

        if (focusedView != null) {
            focusedView.getGlobalVisibleRect(focusRect);
        } else {
            focusRect.setEmpty();
        }

        if (l != focusRect.left || r != focusRect.right
                || t != focusRect.top || b != focusRect.bottom) {
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

        animator.removeMessages(0);
        animator.sendEmptyMessageDelayed(0, 1000);
    }

    @Override
    public void onGlobalLayout() {
        updateRect();

        animator.sendEmptyMessageDelayed(0, 1000);
    }

    @Override
    public void onTouchModeChanged(final boolean inTouchMode) {
        this.isInTouchMode = inTouchMode;

        if (inTouchMode) {
            updateRect();
        } else {
            invalidateSelf();
        }
    }

    public void setCurrentFocus(final View newFocus) {
        if (newFocus == null) {
            return;
        }

        this.isInTouchMode = newFocus.isInTouchMode();

        onGlobalFocusChanged(null, newFocus);
    }

    @Override
    public void draw(@NonNull final Canvas canvas) {
        if (!isInTouchMode && focusRect.width() != 0) {
            canvas.drawRect(focusRect, rectPaint);
        }
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public void setAlpha(final int alpha) {
    }

    @Override
    public void setColorFilter(final ColorFilter colorFilter) {
    }

    public static void setupFocusObserver(final Dialog dialog) {
        Rect displayRect = new Rect();

        Window window = dialog.getWindow();
        assert window != null;

        View decor = window.getDecorView();
        decor.getWindowVisibleDisplayFrame(displayRect);

        FocusOverlayView overlay = new FocusOverlayView(dialog.getContext());
        overlay.setBounds(0, 0, displayRect.width(), displayRect.height());

        setupOverlay(window, overlay);
    }

    public static void setupFocusObserver(final Activity activity) {
        Rect displayRect = new Rect();

        Window window = activity.getWindow();
        View decor = window.getDecorView();
        decor.getWindowVisibleDisplayFrame(displayRect);

        FocusOverlayView overlay = new FocusOverlayView(activity);
        overlay.setBounds(0, 0, displayRect.width(), displayRect.height());

        setupOverlay(window, overlay);
    }

    private static void setupOverlay(final Window window, final FocusOverlayView overlay) {
        ViewGroup decor = (ViewGroup) window.getDecorView();
        decor.getOverlay().add(overlay);

        fixFocusHierarchy(decor);

        ViewTreeObserver observer = decor.getViewTreeObserver();
        observer.addOnScrollChangedListener(overlay);
        observer.addOnGlobalFocusChangeListener(overlay);
        observer.addOnGlobalLayoutListener(overlay);
        observer.addOnTouchModeChangeListener(overlay);

        overlay.setCurrentFocus(decor.getFocusedChild());

        // Some key presses don't actually move focus, but still result in movement on screen.
        // For example, MovementMethod of TextView may cause requestRectangleOnScreen() due to
        // some "focusable" spans, which in turn causes CoordinatorLayout to "scroll" it's children.
        // Unfortunately many such forms of "scrolling" do not count as scrolling for purpose
        // of dispatching ViewTreeObserver callbacks, so we have to intercept them by directly
        // receiving keys from Window.
        window.setCallback(new WindowCallbackWrapper(window.getCallback()) {
            @Override
            public boolean dispatchKeyEvent(final KeyEvent event) {
                boolean res = super.dispatchKeyEvent(event);
                overlay.onKey(event);
                return res;
            }
        });
    }

    private void onKey(final KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return;
        }

        updateRect();

        animator.sendEmptyMessageDelayed(0, 100);
    }

    private static void fixFocusHierarchy(final View decor) {
        // During Android 8 development some dumb ass decided, that action bar has to be
        // a keyboard focus cluster. Unfortunately, keyboard clusters do not work for primary
        // auditory of key navigation — Android TV users (Android TV remotes do not have
        // keyboard META key for moving between clusters). We have to fix this unfortunate accident
        // While we are at it, let's deal with touchscreenBlocksFocus too.

        if (Build.VERSION.SDK_INT < 26) {
            return;
        }

        if (!(decor instanceof ViewGroup)) {
            return;
        }

        clearFocusObstacles((ViewGroup) decor);
    }

    @RequiresApi(api = 26)
    private static void clearFocusObstacles(final ViewGroup viewGroup) {
        viewGroup.setTouchscreenBlocksFocus(false);

        if (viewGroup.isKeyboardNavigationCluster()) {
            viewGroup.setKeyboardNavigationCluster(false);

            return; // clusters aren't supposed to nest
        }

        int childCount = viewGroup.getChildCount();

        for (int i = 0; i < childCount; ++i) {
            View view = viewGroup.getChildAt(i);

            if (view instanceof ViewGroup) {
                clearFocusObstacles((ViewGroup) view);
            }
        }
    }
}
