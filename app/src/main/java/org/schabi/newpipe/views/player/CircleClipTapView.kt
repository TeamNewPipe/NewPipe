package org.schabi.newpipe.views.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class CircleClipTapView(context: Context?, attrs: AttributeSet) : View(context, attrs) {

    private var backgroundPaint = Paint()

    private var widthPx = 0
    private var heightPx = 0

    // Background

    private var shapePath = Path()
    private var arcSize: Float = 80f
    private var isLeft = true

    init {
        requireNotNull(context) { "Context is null." }

        backgroundPaint.apply {
            style = Paint.Style.FILL
            isAntiAlias = true
            color = 0x30000000
        }

        val dm = context.resources.displayMetrics
        widthPx = dm.widthPixels
        heightPx = dm.heightPixels

        updatePathShape()
    }

    fun updateArcSize(baseView: View) {
        val newArcSize = baseView.height / 11.4f
        if (arcSize != newArcSize) {
            arcSize = newArcSize
            updatePathShape()
        }
    }

    fun updatePosition(newIsLeft: Boolean) {
        if (isLeft != newIsLeft) {
            isLeft = newIsLeft
            updatePathShape()
        }
    }

    private fun updatePathShape() {
        val halfWidth = widthPx * 0.5f

        shapePath.reset()

        val w = if (isLeft) 0f else widthPx.toFloat()
        val f = if (isLeft) 1 else -1

        shapePath.moveTo(w, 0f)
        shapePath.lineTo(f * (halfWidth - arcSize) + w, 0f)
        shapePath.quadTo(
            f * (halfWidth + arcSize) + w,
            heightPx.toFloat() / 2,
            f * (halfWidth - arcSize) + w,
            heightPx.toFloat()
        )
        shapePath.lineTo(w, heightPx.toFloat())

        shapePath.close()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        widthPx = w
        heightPx = h
        updatePathShape()
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        canvas?.clipPath(shapePath)
        canvas?.drawPath(shapePath, backgroundPaint)
    }
}
