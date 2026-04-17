/*
 * Copyright (C) NewPipe Contributors
 * ChaptersSeekBar.kt is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.schabi.newpipe.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import org.schabi.newpipe.extractor.stream.StreamSegment

/**
 * A [FocusAwareSeekBar] that renders narrow transparent gaps at chapter boundaries,
 * giving the seekbar a segmented "chopped" appearance.
 * Call [setChapters] whenever a new stream loads.
 */
class ChaptersSeekBar : FocusAwareSeekBar {

    private val gapPaint = Paint().apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private var chapters: List<StreamSegment> = emptyList()
    private var durationSeconds: Long = 0

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr)

    /**
     * Stores chapter data for rendering segment gaps.
     *
     * @param newChapters     list of [StreamSegment]s; may be empty but never null
     * @param newDurationSecs total duration in seconds; used to compute fractional positions
     */
    fun setChapters(newChapters: List<StreamSegment>, newDurationSecs: Long) {
        chapters = newChapters
        durationSeconds = newDurationSecs
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (chapters.isEmpty() || durationSeconds <= 0) {
            super.onDraw(canvas)
            return
        }

        // Draw the seekbar into an offscreen layer so CLEAR mode can punch transparent gaps
        val sc = canvas.saveLayer(null, null)
        super.onDraw(canvas)

        val density = resources.displayMetrics.density
        val gapHalfWidth = (GAP_WIDTH_DP * density) / 2f
        val left = paddingLeft
        val trackWidth = (width - left - paddingRight).toFloat()

        if (trackWidth > 0) {
            for (seg in chapters) {
                val startSec = seg.startTimeSeconds
                // Skip the very first position and anything at or past the end
                if (startSec <= 0 || startSec.toLong() >= durationSeconds) {
                    continue
                }
                val x = left + (startSec.toFloat() / durationSeconds.toFloat()) * trackWidth
                canvas.drawRect(x - gapHalfWidth, 0f, x + gapHalfWidth, height.toFloat(), gapPaint)
            }
        }

        canvas.restoreToCount(sc)

        // Redraw the thumb on top so it visually overlaps the gaps
        val t = thumb
        if (t != null) {
            val thumbSave = canvas.save()
            canvas.translate((paddingLeft - thumbOffset).toFloat(), paddingTop.toFloat())
            t.draw(canvas)
            canvas.restoreToCount(thumbSave)
        }
    }

    companion object {
        private const val GAP_WIDTH_DP = 2f
    }
}
