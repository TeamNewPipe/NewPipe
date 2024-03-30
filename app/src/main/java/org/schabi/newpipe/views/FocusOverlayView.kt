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
package org.schabi.newpipe.views

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnDrawListener
import android.view.ViewTreeObserver.OnGlobalFocusChangeListener
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.ViewTreeObserver.OnScrollChangedListener
import android.view.ViewTreeObserver.OnTouchModeChangeListener
import android.view.Window
import androidx.annotation.RequiresApi
import androidx.appcompat.view.WindowCallbackWrapper
import org.schabi.newpipe.R
import java.lang.ref.WeakReference

class FocusOverlayView(context: Context) : Drawable(), OnGlobalFocusChangeListener, OnDrawListener, OnGlobalLayoutListener, OnScrollChangedListener, OnTouchModeChangeListener {
    private var isInTouchMode: Boolean = false
    private val focusRect: Rect = Rect()
    private val rectPaint: Paint = Paint()
    private val animator: Handler = object : Handler(Looper.getMainLooper()) {
        public override fun handleMessage(msg: Message) {
            updateRect()
        }
    }
    private var focused: WeakReference<View>? = null

    init {
        rectPaint.setStyle(Paint.Style.STROKE)
        rectPaint.setStrokeWidth(2f)
        rectPaint.setColor(context.getResources().getColor(R.color.white))
    }

    public override fun onGlobalFocusChanged(oldFocus: View, newFocus: View) {
        if (newFocus != null) {
            focused = WeakReference(newFocus)
        } else {
            focused = null
        }
        updateRect()
        animator.sendEmptyMessageDelayed(0, 1000)
    }

    private fun updateRect() {
        val focusedView: View? = if (focused == null) null else focused!!.get()
        val l: Int = focusRect.left
        val r: Int = focusRect.right
        val t: Int = focusRect.top
        val b: Int = focusRect.bottom
        if (focusedView != null && isShown(focusedView)) {
            focusedView.getGlobalVisibleRect(focusRect)
        }
        if (shouldClearFocusRect(focusedView, focusRect)) {
            focusRect.setEmpty()
        }
        if ((l != focusRect.left) || (r != focusRect.right
                        ) || (t != focusRect.top) || (b != focusRect.bottom)) {
            invalidateSelf()
        }
    }

    private fun isShown(view: View): Boolean {
        return (view.getWidth() != 0) && (view.getHeight() != 0) && view.isShown()
    }

    public override fun onDraw() {
        updateRect()
    }

    public override fun onScrollChanged() {
        updateRect()
        animator.removeMessages(0)
        animator.sendEmptyMessageDelayed(0, 1000)
    }

    public override fun onGlobalLayout() {
        updateRect()
        animator.sendEmptyMessageDelayed(0, 1000)
    }

    public override fun onTouchModeChanged(inTouchMode: Boolean) {
        isInTouchMode = inTouchMode
        if (inTouchMode) {
            updateRect()
        } else {
            invalidateSelf()
        }
    }

    fun setCurrentFocus(newFocus: View?) {
        if (newFocus == null) {
            return
        }
        isInTouchMode = newFocus.isInTouchMode()
        onGlobalFocusChanged(null, newFocus)
    }

    public override fun draw(canvas: Canvas) {
        if (!isInTouchMode && focusRect.width() != 0) {
            canvas.drawRect(focusRect, rectPaint)
        }
    }

    public override fun getOpacity(): Int {
        return PixelFormat.TRANSPARENT
    }

    public override fun setAlpha(alpha: Int) {}
    public override fun setColorFilter(colorFilter: ColorFilter?) {}

    /*
     * When any view in the player looses it's focus (after setVisibility(GONE)) the focus gets
     * added to the whole fragment which has a width and height equal to the window frame.
     * The easiest way to avoid the unneeded frame is to skip highlighting of rect that is
     * equal to the overlayView bounds
     * */
    private fun shouldClearFocusRect(focusedView: View?, focusedRect: Rect): Boolean {
        return focusedView == null || (focusedRect == getBounds())
    }

    private fun onKey(event: KeyEvent) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return
        }
        updateRect()
        animator.sendEmptyMessageDelayed(0, 100)
    }

    companion object {
        fun setupFocusObserver(dialog: Dialog) {
            val displayRect: Rect = Rect()
            val window: Window? = dialog.getWindow()
            assert(window != null)
            val decor: View = window!!.getDecorView()
            decor.getWindowVisibleDisplayFrame(displayRect)
            val overlay: FocusOverlayView = FocusOverlayView(dialog.getContext())
            overlay.setBounds(0, 0, displayRect.width(), displayRect.height())
            setupOverlay(window, overlay)
        }

        fun setupFocusObserver(activity: Activity) {
            val displayRect: Rect = Rect()
            val window: Window = activity.getWindow()
            val decor: View = window.getDecorView()
            decor.getWindowVisibleDisplayFrame(displayRect)
            val overlay: FocusOverlayView = FocusOverlayView(activity)
            overlay.setBounds(0, 0, displayRect.width(), displayRect.height())
            setupOverlay(window, overlay)
        }

        private fun setupOverlay(window: Window?, overlay: FocusOverlayView) {
            val decor: ViewGroup = window!!.getDecorView() as ViewGroup
            decor.getOverlay().add(overlay)
            fixFocusHierarchy(decor)
            val observer: ViewTreeObserver = decor.getViewTreeObserver()
            observer.addOnScrollChangedListener(overlay)
            observer.addOnGlobalFocusChangeListener(overlay)
            observer.addOnGlobalLayoutListener(overlay)
            observer.addOnTouchModeChangeListener(overlay)
            observer.addOnDrawListener(overlay)
            overlay.setCurrentFocus(decor.getFocusedChild())

            // Some key presses don't actually move focus, but still result in movement on screen.
            // For example, MovementMethod of TextView may cause requestRectangleOnScreen() due to
            // some "focusable" spans, which in turn causes CoordinatorLayout to "scroll" it's children.
            // Unfortunately many such forms of "scrolling" do not count as scrolling for purpose
            // of dispatching ViewTreeObserver callbacks, so we have to intercept them by directly
            // receiving keys from Window.
            window.setCallback(object : WindowCallbackWrapper(window.getCallback()) {
                public override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    val res: Boolean = super.dispatchKeyEvent(event)
                    overlay.onKey(event)
                    return res
                }
            })
        }

        private fun fixFocusHierarchy(decor: View) {
            // During Android 8 development some dumb ass decided, that action bar has to be
            // a keyboard focus cluster. Unfortunately, keyboard clusters do not work for primary
            // auditory of key navigation — Android TV users (Android TV remotes do not have
            // keyboard META key for moving between clusters). We have to fix this unfortunate accident
            // While we are at it, let's deal with touchscreenBlocksFocus too.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return
            }
            if (!(decor is ViewGroup)) {
                return
            }
            clearFocusObstacles(decor)
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        private fun clearFocusObstacles(viewGroup: ViewGroup) {
            viewGroup.setTouchscreenBlocksFocus(false)
            if (viewGroup.isKeyboardNavigationCluster()) {
                viewGroup.setKeyboardNavigationCluster(false)
                return  // clusters aren't supposed to nest
            }
            val childCount: Int = viewGroup.getChildCount()
            for (i in 0 until childCount) {
                val view: View = viewGroup.getChildAt(i)
                if (view is ViewGroup) {
                    clearFocusObstacles(view)
                }
            }
        }
    }
}
