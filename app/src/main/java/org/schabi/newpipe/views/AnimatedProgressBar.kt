package org.schabi.newpipe.views

import android.content.Context
import android.util.AttributeSet
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.Transformation
import android.widget.ProgressBar

class AnimatedProgressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.progressBarStyle
) : ProgressBar(context, attrs, defStyleAttr) {

    private var animation: ProgressBarAnimation? = null

    @Synchronized
    fun setProgressAnimated(progress: Int) {
        cancelAnimation()
        animation = ProgressBarAnimation(this, getProgress().toFloat(), progress.toFloat()).apply {
            duration = 500
            interpolator = AccelerateDecelerateInterpolator()
        }
        startAnimation(animation)
    }

    private fun cancelAnimation() {
        animation?.cancel()
        animation = null
        clearAnimation()
    }

    private class ProgressBarAnimation(
        private val progressBar: AnimatedProgressBar,
        private val from: Float,
        private val to: Float
    ) : Animation() {

        override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            super.applyTransformation(interpolatedTime, t)
            val value = from + (to - from) * interpolatedTime
            progressBar.progress = value.toInt()
        }
    }
}
