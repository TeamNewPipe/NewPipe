@file:JvmName("TextViewUtils")

package org.schabi.newpipe.ktx

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.util.Log
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.core.animation.addListener
import androidx.core.text.PrecomputedTextCompat
import androidx.core.widget.TextViewCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.MainActivity

private const val TAG = "TextViewUtils"

inline val TextView.textMetricsParamsCompat: PrecomputedTextCompat.Params
    get() = TextViewCompat.getTextMetricsParams(this)

/**
 * Animate the text color of any view that extends [TextView] (Buttons, EditText...).
 *
 * @param duration   the duration of the animation
 * @param colorStart the text color to start with
 * @param colorEnd   the text color to end with
 */
fun TextView.animateTextColor(duration: Long, @ColorInt colorStart: Int, @ColorInt colorEnd: Int) {
    if (MainActivity.DEBUG) {
        Log.d(
            TAG,
            "animateTextColor() called with: " +
                "view = [" + this + "], duration = [" + duration + "], " +
                "colorStart = [" + colorStart + "], colorEnd = [" + colorEnd + "]"
        )
    }
    val viewPropertyAnimator = ValueAnimator.ofObject(ArgbEvaluator(), colorStart, colorEnd)
    viewPropertyAnimator.interpolator = FastOutSlowInInterpolator()
    viewPropertyAnimator.duration = duration
    viewPropertyAnimator.addUpdateListener { setTextColor(it.animatedValue as Int) }
    viewPropertyAnimator.addListener(onCancel = { setTextColor(colorEnd) }, onEnd = { setTextColor(colorEnd) })
    viewPropertyAnimator.start()
}

/**
 * Pre-computes the text measurement and glyph positioning information of a given block of text
 * before displaying it in the given [TextView] to improve layout performance.
 *
 * Since this can be computationally intensive, it is done off the UI thread.
 */
fun TextView.computeAndSetPrecomputedText(text: CharSequence): Disposable {
    return Single.fromCallable { PrecomputedTextCompat.create(text, textMetricsParamsCompat) }
        .subscribeOn(Schedulers.computation())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe { precomputedText ->
            TextViewCompat.setPrecomputedText(this, precomputedText)
        }
}

fun TextView.computeAndSetPrecomputedText(@StringRes stringId: Int) = computeAndSetPrecomputedText(context.getString(stringId))
