package org.schabi.newpipe.views

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.ViewTreeObserver
import android.widget.SeekBar
import androidx.appcompat.widget.AppCompatSeekBar
import org.schabi.newpipe.util.DeviceUtils

class FocusAwareSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.seekBarStyle
) : AppCompatSeekBar(context, attrs, defStyleAttr) {

    private var listener: NestedListener? = null
    private var treeObserver: ViewTreeObserver? = null

    override fun setOnSeekBarChangeListener(l: OnSeekBarChangeListener?) {
        this.listener = l?.let { NestedListener(it) }
        super.setOnSeekBarChangeListener(listener)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!isInTouchMode && DeviceUtils.isConfirmKey(keyCode)) {
            releaseTrack()
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (!isInTouchMode && !gainFocus) {
            releaseTrack()
        }
    }

    private val touchModeListener = ViewTreeObserver.OnTouchModeChangeListener { isInTouchMode ->
        if (isInTouchMode) {
            releaseTrack()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        treeObserver = viewTreeObserver
        treeObserver?.addOnTouchModeChangeListener(touchModeListener)
    }

    override fun onDetachedFromWindow() {
        if (treeObserver == null || treeObserver?.isAlive == false) {
            treeObserver = viewTreeObserver
        }
        treeObserver?.removeOnTouchModeChangeListener(touchModeListener)
        treeObserver = null
        super.onDetachedFromWindow()
    }

    private fun releaseTrack() {
        if (listener?.isSeeking == true) {
            listener?.onStopTrackingTouch(this)
        }
    }

    private class NestedListener(private val delegate: OnSeekBarChangeListener) : OnSeekBarChangeListener {
        var isSeeking = false

        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (!seekBar.isInTouchMode && !isSeeking && fromUser) {
                isSeeking = true
                onStartTrackingTouch(seekBar)
            }
            delegate.onProgressChanged(seekBar, progress, fromUser)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {
            isSeeking = true
            delegate.onStartTrackingTouch(seekBar)
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            isSeeking = false
            delegate.onStopTrackingTouch(seekBar)
        }
    }
}
