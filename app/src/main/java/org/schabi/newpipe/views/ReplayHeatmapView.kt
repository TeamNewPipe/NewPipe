/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import org.schabi.newpipe.extractor.stream.ReplayHeatmapSample

/** Passive cached timeline drawing; native seek-bar interaction remains on the seek bar below. */
class ReplayHeatmapView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 1.5f
        strokeJoin = Paint.Join.ROUND
    }
    private val area = Path()
    private val line = Path()
    private var samples = emptyList<ReplayHeatmapSample>()
    private var durationMs = 0L
    private var accent = Color.TRANSPARENT
    private var pathDirty = true

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setSamples(value: List<ReplayHeatmapSample>, duration: Long) {
        samples = value
        durationMs = duration
        pathDirty = true
        invalidate()
    }

    fun setTrackInsets(left: Int, right: Int) {
        if (left == paddingLeft && right == paddingRight) return
        setPadding(left, paddingTop, right, paddingBottom)
        pathDirty = true
    }

    fun setAccent(color: Int) {
        if (accent == color) return
        accent = color
        fill.color = ColorUtils.setAlphaComponent(color, 110)
        outline.color = ColorUtils.setAlphaComponent(color, 255)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pathDirty = true
    }

    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        super.onRtlPropertiesChanged(layoutDirection)
        pathDirty = true
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pathDirty) rebuildPath()
        canvas.drawPath(area, fill)
        canvas.drawPath(line, outline)
    }

    private fun rebuildPath() {
        pathDirty = false
        area.rewind()
        line.rewind()
        val trackWidth = width - paddingLeft - paddingRight
        val bottom = height - paddingBottom - outline.strokeWidth
        val availableHeight = bottom - paddingTop - outline.strokeWidth
        if (durationMs <= 0 || trackWidth <= 0 || availableHeight <= 0) return
        fun x(time: Long): Float {
            val fraction = time.coerceIn(0, durationMs).toDouble() / durationMs
            val position = if (layoutDirection == LAYOUT_DIRECTION_RTL) 1.0 - fraction else fraction
            return paddingLeft + (trackWidth * position).toFloat()
        }
        var previousEnd = -1L
        samples.forEach { sample ->
            if (sample.startTimeMs < 0 || sample.endTimeMs <= sample.startTimeMs ||
                sample.startTimeMs >= durationMs || !sample.intensity.isFinite()
            ) {
                return@forEach
            }
            val start = x(sample.startTimeMs)
            val end = x(sample.endTimeMs)
            val middle = (start + end) / 2
            val top = bottom - availableHeight * sample.intensity.coerceIn(0f, 1f)
            if (previousEnd < 0 || sample.startTimeMs > previousEnd) {
                if (previousEnd >= 0) {
                    area.lineTo(x(previousEnd), bottom)
                    area.close()
                    line.lineTo(x(previousEnd), bottom)
                }
                area.moveTo(start, bottom)
                line.moveTo(start, bottom)
            }
            area.lineTo(middle, top)
            line.lineTo(middle, top)
            previousEnd = sample.endTimeMs.coerceAtMost(durationMs)
        }
        if (previousEnd >= 0) {
            area.lineTo(x(previousEnd), bottom)
            area.close()
            line.lineTo(x(previousEnd), bottom)
        }
    }
}
