package org.schabi.newpipe.views

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.preference.PreferenceManager
import kotlin.math.PI
import kotlin.math.sin
import org.schabi.newpipe.extractor.stream.StreamSegment
import org.schabi.newpipe.util.DeviceUtils

/** Changes only the track drawing; native touch, keyboard, thumb, and accessibility remain intact. */
class WavySeekBar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FocusAwareSeekBar(context, attrs) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val flatDrawable = progressDrawable
    private val density = resources.displayMetrics.density
    private val wave: WaveTrack?
        get() = ((progressDrawable as? LayerDrawable)?.findDrawableByLayerId(android.R.id.progress) as? ClipDrawable)?.drawable as? WaveTrack
    private var wavy = false
    private var attached = false
    private var chapterSegments: List<StreamSegment> = emptyList()
    private val chapterGapPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val animation = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
        duration = 1600
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            // Native tinting may clone the layers, so animate the displayed track.
            wave?.phase = it.animatedValue as Float
            if (!isShown) cancel()
        }
    }
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == STYLE_KEY) post { applyStyle() }
    }

    var playbackActive: Boolean = false
        set(value) {
            field = value
            updateAnimation()
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        applyStyle()
    }

    override fun onDetachedFromWindow() {
        attached = false
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        animation.cancel()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        // View constructors can dispatch this callback before subclass fields exist.
        if (attached) updateAnimation()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (attached) updateAnimation()
    }

    private fun applyStyle() {
        val selected = preferences.getString(STYLE_KEY, "standard") == "wavy"
        if (selected != wavy) {
            wavy = selected
            progressDrawable = if (wavy) waveLayers() else flatDrawable
            updateTrackBounds()
        }
        updateAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateTrackBounds()
    }

    fun setChapters(newChapters: List<StreamSegment>) {
        chapterSegments = newChapters.toList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (chapterSegments.isEmpty() || max <= 0) {
            super.onDraw(canvas)
            return
        }

        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        super.onDraw(canvas)

        val trackLeft = paddingLeft.toFloat()
        val trackWidth = (width - paddingLeft - paddingRight).toFloat()
        val durationMillis = max.toLong()
        val gapWidth = 2 * density
        if (trackWidth > 0f) {
            chapterSegments.forEach { chapter ->
                val startMillis = org.schabi.newpipe.download.ChapterTimes.startMillis(chapter)
                if (startMillis > 0 && startMillis < durationMillis) {
                    val x = trackLeft + trackWidth * startMillis / durationMillis
                    canvas.drawRect(
                        x - gapWidth / 2,
                        0f,
                        x + gapWidth / 2,
                        height.toFloat(),
                        chapterGapPaint
                    )
                }
            }
        }
        canvas.restoreToCount(layer)
        // A chapter boundary can coincide with the thumb; keep the thumb above the gaps.
        thumb?.let {
            val saved = canvas.save()
            canvas.translate((paddingLeft - thumbOffset).toFloat(), paddingTop.toFloat())
            it.draw(canvas)
            canvas.restoreToCount(saved)
        }
    }

    private fun updateTrackBounds() {
        if (!wavy) return
        val center = (height - paddingTop - paddingBottom) / 2
        val halfHeight = (7 * density).toInt()
        progressDrawable.setBounds(0, center - halfHeight, (width - paddingLeft - paddingRight).coerceAtLeast(0), center + halfHeight)
    }

    private fun waveLayers(): Drawable {
        fun flat() = GradientDrawable().apply {
            setColor(Color.GRAY)
            cornerRadius = 2 * density
        }
        return LayerDrawable(
            arrayOf(flat(), ClipDrawable(flat(), Gravity.LEFT, ClipDrawable.HORIZONTAL), ClipDrawable(WaveTrack(density), Gravity.LEFT, ClipDrawable.HORIZONTAL))
        ).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.secondaryProgress)
            setId(2, android.R.id.progress)
            val inset = (5 * density).toInt()
            setLayerInset(0, 0, inset, 0, inset)
            setLayerInset(1, 0, inset, 0, inset)
        }
    }

    private fun updateAnimation() {
        val animate = attached && wavy && playbackActive && isShown && windowVisibility == VISIBLE &&
            DeviceUtils.hasAnimationsAnimatorDurationEnabled(context)
        if (animate && !animation.isStarted) animation.start()
        if (!animate) animation.cancel()
    }

    internal class WaveTrack(private val density: Float) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2 * density
            strokeCap = Paint.Cap.ROUND
        }
        private val path = Path()
        private var colors = ColorStateList.valueOf(Color.WHITE)
        var phase = 0f
            set(value) {
                field = value
                invalidateSelf()
            }

        override fun draw(canvas: Canvas) {
            if (bounds.width() <= 0 || bounds.height() <= 0) return
            paint.color = colors.getColorForState(state, colors.defaultColor)
            val amplitude = (bounds.height() / 2f - paint.strokeWidth).coerceIn(0f, 4 * density)
            val wavelength = 24 * density
            path.reset()
            var x = bounds.left.toFloat()
            while (x <= bounds.right) {
                val y = bounds.exactCenterY() + amplitude * sin((x - bounds.left) * 2f * PI.toFloat() / wavelength + phase)
                if (x == bounds.left.toFloat()) path.moveTo(x, y) else path.lineTo(x, y)
                x += density.coerceAtLeast(1f)
            }
            canvas.drawPath(path, paint)
        }

        override fun setTintList(tint: ColorStateList?) {
            colors = tint ?: ColorStateList.valueOf(Color.WHITE)
            invalidateSelf()
        }
        override fun isStateful() = colors.isStateful
        override fun onStateChange(state: IntArray): Boolean {
            invalidateSelf()
            return true
        }
        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
            invalidateSelf()
        }
        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
            invalidateSelf()
        }

        // Android 6 clones every LayerDrawable child when ProgressBar applies native tints.
        override fun getConstantState(): ConstantState = WaveState(density, colors, phase)

        @Deprecated("Required by Drawable")
        override fun getOpacity() = PixelFormat.TRANSLUCENT

        private class WaveState(private val density: Float, private val colors: ColorStateList, private val phase: Float) : ConstantState() {
            override fun newDrawable(): Drawable = create(density)

            override fun newDrawable(resources: Resources?): Drawable = create(resources?.displayMetrics?.density ?: density)

            override fun getChangingConfigurations(): Int = colors.changingConfigurations

            private fun create(targetDensity: Float) = WaveTrack(targetDensity).apply {
                setTintList(this@WaveState.colors)
                phase = this@WaveState.phase
            }
        }
    }

    companion object {
        const val STYLE_KEY = "player_seekbar_style"
    }
}
