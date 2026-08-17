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
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.annotation.RequiresApi
import java.lang.ref.WeakReference
import org.schabi.newpipe.R

class FocusOverlayView(context: Context) :
    Drawable(),
    ViewTreeObserver.OnGlobalFocusChangeListener,
    ViewTreeObserver.OnDrawListener,
    ViewTreeObserver.OnGlobalLayoutListener,
    ViewTreeObserver.OnScrollChangedListener,
    ViewTreeObserver.OnTouchModeChangeListener {

    private var isInTouchMode = false
    private val focusRect = Rect()
    private val rectPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        @Suppress("DEPRECATION")
        color = context.resources.getColor(R.color.white)
    }

    private val animator = Handler(Looper.getMainLooper()) {
        updateRect()
        true
    }

    private var focused: WeakReference<View>? = null

    override fun onGlobalFocusChanged(oldFocus: View?, newFocus: View?) {
        focused = newFocus?.let { WeakReference(it) }
        updateRect()
        animator.sendEmptyMessageDelayed(0, 1000)
    }

    private fun updateRect() {
        val focusedView = focused?.get()

        val l = focusRect.left
        val r = focusRect.right
        val t = focusRect.top
        val b = focusRect.bottom

        if (focusedView != null && isShown(focusedView)) {
            focusedView.getGlobalVisibleRect(focusRect)
        }

        if (shouldClearFocusRect(focusedView, focusRect)) {
            focusRect.setEmpty()
        }

        if (l != focusRect.left || r != focusRect.right || t != focusRect.top || b != focusRect.bottom) {
            invalidateSelf()
        }
    }

    private fun isShown(view: View): Boolean {
        return view.width != 0 && view.height != 0 && view.isShown
    }

    override fun onDraw() {
        updateRect()
    }

    override fun onScrollChanged() {
        updateRect()
        animator.removeMessages(0)
        animator.sendEmptyMessageDelayed(0, 1000)
    }

    override fun onGlobalLayout() {
        updateRect()
        animator.sendEmptyMessageDelayed(0, 1000)
    }

    override fun onTouchModeChanged(inTouchMode: Boolean) {
        this.isInTouchMode = inTouchMode
        if (inTouchMode) {
            updateRect()
        } else {
            invalidateSelf()
        }
    }

    fun setCurrentFocus(newFocus: View?) {
        if (newFocus == null) return
        this.isInTouchMode = newFocus.isInTouchMode
        onGlobalFocusChanged(null, newFocus)
    }

    override fun draw(canvas: Canvas) {
        if (!isInTouchMode && focusRect.width() != 0) {
            canvas.drawRect(focusRect, rectPaint)
        }
    }

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSPARENT", "android.graphics.PixelFormat"))
    override fun getOpacity(): Int = PixelFormat.TRANSPARENT

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    private fun shouldClearFocusRect(focusedView: View?, focusedRect: Rect): Boolean {
        return focusedView == null || focusedRect == bounds
    }

    fun onKey(event: KeyEvent) {
        if (event.action != KeyEvent.ACTION_DOWN) return
        updateRect()
        animator.sendEmptyMessageDelayed(0, 100)
    }

    companion object {
        private const val TAG = "FocusOverlayView"

        @JvmStatic
        fun setupFocusObserver(dialog: Dialog) {
            val displayRect = Rect()
            val window = dialog.window ?: return
            val decor = window.decorView
            decor.getWindowVisibleDisplayFrame(displayRect)

            val overlay = FocusOverlayView(dialog.context)
            overlay.setBounds(0, 0, displayRect.width(), displayRect.height())
            setupOverlay(window, overlay)
        }

        @JvmStatic
        fun setupFocusObserver(activity: Activity) {
            val displayRect = Rect()
            val window = activity.window
            val decor = window.decorView
            decor.getWindowVisibleDisplayFrame(displayRect)

            val overlay = FocusOverlayView(activity)
            overlay.setBounds(0, 0, displayRect.width(), displayRect.height())
            setupOverlay(window, overlay)
        }

        private fun setupOverlay(window: android.view.Window, overlay: FocusOverlayView) {
            val decor = window.decorView as ViewGroup
            decor.overlay.add(overlay)

            fixFocusHierarchy(decor)

            decor.viewTreeObserver.apply {
                addOnScrollChangedListener(overlay)
                addOnGlobalFocusChangeListener(overlay)
                addOnGlobalLayoutListener(overlay)
                addOnTouchModeChangeListener(overlay)
                addOnDrawListener(overlay)
            }

            overlay.setCurrentFocus(decor.findFocus())

            window.callback = object : SimpleWindowCallback(window.callback) {
                override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
                    val res = super.dispatchKeyEvent(event)
                    if (event != null) {
                        overlay.onKey(event)
                    }
                    return res
                }
            }
        }

        private fun fixFocusHierarchy(decor: View) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            if (decor !is ViewGroup) return
            clearFocusObstacles(decor)
        }

        @RequiresApi(Build.VERSION_CODES.O)
        private fun clearFocusObstacles(viewGroup: ViewGroup) {
            viewGroup.touchscreenBlocksFocus = false
            if (viewGroup.isKeyboardNavigationCluster) {
                viewGroup.isKeyboardNavigationCluster = false
                return
            }

            for (i in 0 until viewGroup.childCount) {
                val view = viewGroup.getChildAt(i)
                if (view is ViewGroup) {
                    clearFocusObstacles(view)
                }
            }
        }
    }
}
