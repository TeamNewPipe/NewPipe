package us.shandian.giga.ui.common

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import androidx.annotation.ColorInt

class ProgressDrawable // marquee disabled
() : Drawable() {
    private var mProgress: Float = 0f
    private var mBackgroundColor: Int = 0
    private var mForegroundColor: Int = 0
    private var mMarqueeHandler: Handler? = null
    private var mMarqueeProgress: Float = 0.0f
    private var mMarqueeLine: Path? = null
    private var mMarqueeSize: Int = 0
    private var mMarqueeNext: Long = 0
    fun setColors(@ColorInt background: Int, @ColorInt foreground: Int) {
        mBackgroundColor = background
        mForegroundColor = foreground
    }

    fun setProgress(progress: Double) {
        mProgress = progress.toFloat()
        invalidateSelf()
    }

    fun setMarquee(marquee: Boolean) {
        if (marquee == (mMarqueeLine != null)) {
            return
        }
        mMarqueeLine = if (marquee) Path() else null
        mMarqueeHandler = if (marquee) Handler(Looper.getMainLooper()) else null
        mMarqueeSize = 0
        mMarqueeNext = 0
    }

    public override fun draw(canvas: Canvas) {
        var width: Int = getBounds().width()
        val height: Int = getBounds().height()
        val paint: Paint = Paint()
        paint.setColor(mBackgroundColor)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.setColor(mForegroundColor)
        if (mMarqueeLine != null) {
            if (mMarqueeSize < 1) setupMarquee(width, height)
            var size: Int = mMarqueeSize
            val paint2: Paint = Paint()
            paint2.setColor(mForegroundColor)
            paint2.setStrokeWidth(size.toFloat())
            paint2.setStyle(Paint.Style.STROKE)
            size *= 2
            if (mMarqueeProgress >= size) {
                mMarqueeProgress = 1f
            } else {
                mMarqueeProgress++
            }

            // render marquee
            width += size * 2
            val marquee: Path = Path()
            var i: Int = -size
            while (i < width) {
                marquee.addPath(mMarqueeLine!!, (i.toFloat() + mMarqueeProgress), 0f)
                i += size
            }
            marquee.close()
            canvas.drawPath(marquee, paint2) // draw marquee
            if (System.currentTimeMillis() >= mMarqueeNext) {
                // program next update
                mMarqueeNext = System.currentTimeMillis() + MARQUEE_INTERVAL
                mMarqueeHandler!!.postDelayed(Runnable({ invalidateSelf() }), MARQUEE_INTERVAL.toLong())
            }
            return
        }
        canvas.drawRect(0f, 0f, ((mProgress * width).toInt()).toFloat(), height.toFloat(), paint)
    }

    public override fun setAlpha(alpha: Int) {
        // Unsupported
    }

    public override fun setColorFilter(filter: ColorFilter?) {
        // Unsupported
    }

    public override fun getOpacity(): Int {
        return PixelFormat.OPAQUE
    }

    public override fun onBoundsChange(rect: Rect) {
        if (mMarqueeLine != null) setupMarquee(rect.width(), rect.height())
    }

    private fun setupMarquee(width: Int, height: Int) {
        mMarqueeSize = ((width * 10.0f) / 100.0f).toInt() // the size is 10% of the width
        mMarqueeLine!!.rewind()
        mMarqueeLine!!.moveTo(-mMarqueeSize.toFloat(), -mMarqueeSize.toFloat())
        mMarqueeLine!!.lineTo((-mMarqueeSize * 4).toFloat(), (height + mMarqueeSize).toFloat())
        mMarqueeLine!!.close()
    }

    companion object {
        private val MARQUEE_INTERVAL: Int = 150
    }
}
