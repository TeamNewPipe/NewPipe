/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.views

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.ViewTreeObserver
import android.widget.SeekBar
import androidx.appcompat.widget.AppCompatSeekBar
import org.schabi.newpipe.util.DeviceUtils

/**
 * SeekBar, adapted for directional navigation. It emulates touch-related callbacks
 * (onStartTrackingTouch/onStopTrackingTouch), so existing code does not need to be changed to
 * work with it.
 */
open class FocusAwareSeekBar : AppCompatSeekBar {

    private var listener: NestedListener? = null
    private var treeObserver: ViewTreeObserver? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr)

    override fun setOnSeekBarChangeListener(l: OnSeekBarChangeListener?) {
        listener = if (l == null) null else NestedListener(l)
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

    private val touchModeListener = ViewTreeObserver.OnTouchModeChangeListener { inTouchMode ->
        if (inTouchMode) {
            releaseTrack()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        treeObserver = viewTreeObserver
        treeObserver?.addOnTouchModeChangeListener(touchModeListener)
    }

    override fun onDetachedFromWindow() {
        if (treeObserver?.isAlive != true) {
            treeObserver = viewTreeObserver
        }
        treeObserver?.removeOnTouchModeChangeListener(touchModeListener)
        treeObserver = null
        super.onDetachedFromWindow()
    }

    private fun releaseTrack() {
        val l = listener
        if (l != null && l.isSeeking) {
            l.onStopTrackingTouch(this)
        }
    }

    private class NestedListener(private val delegate: OnSeekBarChangeListener) :
        OnSeekBarChangeListener {

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
