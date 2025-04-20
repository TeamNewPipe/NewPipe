@file:JvmName("ViewUtils")

package org.schabi.newpipe.ktx

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.util.Log
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import androidx.core.animation.addListener
import androidx.core.view.ViewCompat
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

// logs in this class are disabled by default since it's usually not useful,
// you can enable them by setting this flag to MainActivity.DEBUG
private const val DEBUG = false
private const val TAG = "ViewUtils"

/**
 * Animate the view.
 *
 * @param enterOrExit   true to enter, false to exit
 * @param duration      how long the animation will take, in milliseconds
 * @param animationType Type of the animation
 * @param delay         how long the animation will wait to start, in milliseconds
 * @param execOnEnd     runnable that will be executed when the animation ends
 */
@JvmOverloads
fun View.animate(
    enterOrExit: Boolean,
    duration: Long,
    animationType: AnimationType = AnimationType.ALPHA,
    delay: Long = 0,
    execOnEnd: Runnable? = null
) {
    if (DEBUG) {
        val id = try {
            resources.getResourceEntryName(id)
        } catch (e: Exception) {
            id.toString()
        }
        val msg = String.format(
            "%8s →  [%s:%s] [%s %s:%s] execOnEnd=%s", enterOrExit,
            javaClass.simpleName, id, animationType, duration, delay, execOnEnd
        )
        Log.d(TAG, "animate(): $msg")
    }
    if (isVisible && enterOrExit) {
        if (DEBUG) {
            Log.d(TAG, "animate(): view was already visible > view = [$this]")
        }
        animate().setListener(null).cancel()
        isVisible = true
        alpha = 1f
        execOnEnd?.run()
        return
    } else if ((isGone || isInvisible) && !enterOrExit) {
        if (DEBUG) {
            Log.d(TAG, "animate(): view was already gone > view = [$this]")
        }
        animate().setListener(null).cancel()
        isGone = true
        alpha = 0f
        execOnEnd?.run()
        return
    }
    animate().setListener(null).cancel()
    isVisible = true

    when (animationType) {
        AnimationType.ALPHA -> animateAlpha(enterOrExit, duration, delay, execOnEnd)
        AnimationType.SCALE_AND_ALPHA -> animateScaleAndAlpha(enterOrExit, duration, delay, execOnEnd)
        AnimationType.LIGHT_SCALE_AND_ALPHA -> animateLightScaleAndAlpha(enterOrExit, duration, delay, execOnEnd)
        AnimationType.SLIDE_AND_ALPHA -> animateSlideAndAlpha(enterOrExit, duration, delay, execOnEnd)
        AnimationType.LIGHT_SLIDE_AND_ALPHA -> animateLightSlideAndAlpha(enterOrExit, duration, delay, execOnEnd)
    }
}

/**
 * Animate the background color of a view.
 *
 * @param duration   the duration of the animation
 * @param colorStart the background color to start with
 * @param colorEnd   the background color to end with
 */
fun View.animateBackgroundColor(duration: Long, @ColorInt colorStart: Int, @ColorInt colorEnd: Int) {
    if (DEBUG) {
        Log.d(
            TAG,
            "animateBackgroundColor() called with: view = [$this], duration = [$duration], " +
                "colorStart = [$colorStart], colorEnd = [$colorEnd]"
        )
    }
    val viewPropertyAnimator = ValueAnimator.ofObject(ArgbEvaluator(), colorStart, colorEnd)
    viewPropertyAnimator.interpolator = FastOutSlowInInterpolator()
    viewPropertyAnimator.duration = duration

    fun listenerAction(color: Int) {
        ViewCompat.setBackgroundTintList(this, ColorStateList.valueOf(color))
    }
    viewPropertyAnimator.addUpdateListener { listenerAction(it.animatedValue as Int) }
    viewPropertyAnimator.addListener(onCancel = { listenerAction(colorEnd) }, onEnd = { listenerAction(colorEnd) })
    viewPropertyAnimator.start()
}

fun View.animateHeight(duration: Long, targetHeight: Int): ValueAnimator {
    if (DEBUG) {
        Log.d(TAG, "animateHeight: duration = [$duration], from $height to → $targetHeight in: $this")
    }
    val animator = ValueAnimator.ofFloat(height.toFloat(), targetHeight.toFloat())
    animator.interpolator = FastOutSlowInInterpolator()
    animator.duration = duration

    fun listenerAction(value: Int) {
        layoutParams.height = value
        requestLayout()
    }
    animator.addUpdateListener { listenerAction((it.animatedValue as Float).toInt()) }
    animator.addListener(onCancel = { listenerAction(targetHeight) }, onEnd = { listenerAction(targetHeight) })
    animator.start()
    return animator
}

fun View.animateRotation(duration: Long, targetRotation: Int) {
    if (DEBUG) {
        Log.d(TAG, "animateRotation: duration = [$duration], from $rotation to → $targetRotation in: $this")
    }
    animate().setListener(null).cancel()
    animate()
        .rotation(targetRotation.toFloat()).setDuration(duration)
        .setInterpolator(FastOutSlowInInterpolator())
        .setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationCancel(animation: Animator) {
                rotation = targetRotation.toFloat()
            }

            override fun onAnimationEnd(animation: Animator) {
                rotation = targetRotation.toFloat()
            }
        }).start()
}

private fun View.animateAlpha(enterOrExit: Boolean, duration: Long, delay: Long, execOnEnd: Runnable?) {
    if (enterOrExit) {
        animate().setInterpolator(FastOutSlowInInterpolator()).alpha(1f)
            .setDuration(duration).setStartDelay(delay)
            .setListener(ExecOnEndListener(execOnEnd))
            .start()
    } else {
        animate().setInterpolator(FastOutSlowInInterpolator()).alpha(0f)
            .setDuration(duration).setStartDelay(delay)
            .setListener(HideAndExecOnEndListener(this, execOnEnd))
            .start()
    }
}

private fun View.animateScaleAndAlpha(enterOrExit: Boolean, duration: Long, delay: Long, execOnEnd: Runnable?) {
    if (enterOrExit) {
        scaleX = .8f
        scaleY = .8f
        animate()
            .setInterpolator(FastOutSlowInInterpolator())
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(duration).setStartDelay(delay)
            .setListener(ExecOnEndListener(execOnEnd))
            .start()
    } else {
        scaleX = 1f
        scaleY = 1f
        animate()
            .setInterpolator(FastOutSlowInInterpolator())
            .alpha(0f).scaleX(.8f).scaleY(.8f)
            .setDuration(duration).setStartDelay(delay)
            .setListener(HideAndExecOnEndListener(this, execOnEnd))
            .start()
    }
}

private fun View.animateLightScaleAndAlpha(enterOrExit: Boolean, duration: Long, delay: Long, execOnEnd: Runnable?) {
    if (enterOrExit) {
        alpha = .5f
        scaleX = .95f
        scaleY = .95f
        animate()
            .setInterpolator(FastOutSlowInInterpolator())
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(duration).setStartDelay(delay)
            .setListener(ExecOnEndListener(execOnEnd))
            .start()
    } else {
        alpha = 1f
        scaleX = 1f
        scaleY = 1f
        animate()
            .setInterpolator(FastOutSlowInInterpolator())
            .alpha(0f).scaleX(.95f).scaleY(.95f)
            .setDuration(duration).setStartDelay(delay)
            .setListener(HideAndExecOnEndListener(this, execOnEnd))
            .start()
    }
}

private fun View.animateSlideAndAlpha(enterOrExit: Boolean, duration: Long, delay: Long, execOnEnd: Runnable?) {
    if (enterOrExit) {
        translationY = -height.toFloat()
        alpha = 0f
        animate()
            .setInterpolator(FastOutSlowInInterpolator()).alpha(1f).translationY(0f)
            .setDuration(duration).setStartDelay(delay)
            .setListener(ExecOnEndListener(execOnEnd))
            .start()
    } else {
        animate()
            .setInterpolator(FastOutSlowInInterpolator())
            .alpha(0f).translationY(-height.toFloat())
            .setDuration(duration).setStartDelay(delay)
            .setListener(HideAndExecOnEndListener(this, execOnEnd))
            .start()
    }
}

private fun View.animateLightSlideAndAlpha(enterOrExit: Boolean, duration: Long, delay: Long, execOnEnd: Runnable?) {
    if (enterOrExit) {
        translationY = -height / 2.0f
        alpha = 0f
        animate()
            .setInterpolator(FastOutSlowInInterpolator()).alpha(1f).translationY(0f)
            .setDuration(duration).setStartDelay(delay)
            .setListener(ExecOnEndListener(execOnEnd))
            .start()
    } else {
        animate().setInterpolator(FastOutSlowInInterpolator())
            .alpha(0f).translationY(-height / 2.0f)
            .setDuration(duration).setStartDelay(delay)
            .setListener(HideAndExecOnEndListener(this, execOnEnd))
            .start()
    }
}

@JvmOverloads
fun View.slideUp(
    duration: Long,
    delay: Long = 0L,
    @FloatRange(from = 0.0, to = 1.0) translationPercent: Float = 1.0F,
    execOnEnd: Runnable? = null
) {
    val newTranslationY = (resources.displayMetrics.heightPixels * translationPercent).toInt()
    animate().setListener(null).cancel()
    alpha = 0f
    translationY = newTranslationY.toFloat()
    isVisible = true
    animate()
        .alpha(1f)
        .translationY(0f)
        .setStartDelay(delay)
        .setDuration(duration)
        .setInterpolator(FastOutSlowInInterpolator())
        .setListener(ExecOnEndListener(execOnEnd))
        .start()
}

/**
 * Instead of hiding normally using [animate], which would make
 * the recycler view unable to capture touches after being hidden, this just animates the alpha
 * value setting it to `0.0` after `200` milliseconds.
 */
fun View.animateHideRecyclerViewAllowingScrolling() {
    // not hiding normally because the view needs to still capture touches and allow scroll
    animate().alpha(0.0f).setDuration(200).start()
}

private open class ExecOnEndListener(private val execOnEnd: Runnable?) : AnimatorListenerAdapter() {
    override fun onAnimationEnd(animation: Animator) {
        execOnEnd?.run()
    }
}

private class HideAndExecOnEndListener(private val view: View, execOnEnd: Runnable?) :
    ExecOnEndListener(execOnEnd) {
    override fun onAnimationEnd(animation: Animator) {
        view.isGone = true
        super.onAnimationEnd(animation)
    }
}

enum class AnimationType {
    ALPHA, SCALE_AND_ALPHA, LIGHT_SCALE_AND_ALPHA, SLIDE_AND_ALPHA, LIGHT_SLIDE_AND_ALPHA
}
