/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.feed

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.google.android.material.color.MaterialColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Lightweight Material 3 Expressive-style loading dots for the What's New refresh header.
 *
 * The SwipeRefreshLayout drag spinner remains intact for gesture feedback; after triggering
 * refresh this view displays the themed, animated loading state. No video frames are sampled.
 */
class ExpressiveRefreshIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val brush = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = MaterialColors.getColor(
            this@ExpressiveRefreshIndicator,
            com.google.android.material.R.attr.colorPrimary
        )
    }
    private var angleDegrees = 0f
    private var animator: ValueAnimator? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val size = min(width, height).toFloat()
        val orbit = size * 0.30f
        val baseRadius = size * 0.095f
        for (petal in 0 until PETALS) {
            val phase = (angleDegrees / 360f + petal.toFloat() / PETALS) * 2 * PI
            val sizePulse = (1.0 + sin(phase * 1.3)) / 2.0
            val radius = baseRadius * (0.75 + sizePulse * 0.45).toFloat()
            val theta = phase - PI / 2
            brush.alpha = (100 + 155 * sizePulse).toInt()
            canvas.drawCircle(
                centerX + (orbit * cos(theta)).toFloat(),
                centerY + (orbit * sin(theta)).toFloat(),
                radius,
                brush
            )
        }
        brush.alpha = 255
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimation()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateAnimation()
    }

    private fun updateAnimation() {
        val animationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            ValueAnimator.areAnimatorsEnabled()
        if (!isAttachedToWindow || !isShown || !animationsAllowed) {
            animator?.cancel()
            animator = null
            angleDegrees = 0f
            invalidate()
            return
        }
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 1_400L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                angleDegrees = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private companion object {
        const val PETALS = 7
    }
}
