package org.schabi.newpipe.views.player

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.PlayerFastSeekSecondsViewBinding
import org.schabi.newpipe.util.DeviceUtils

class SecondsView(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    companion object {
        const val ICON_ANIMATION_DURATION = 750L
    }

    var cycleDuration: Long = ICON_ANIMATION_DURATION
        set(value) {
            firstAnimator.duration = value / 5
            secondAnimator.duration = value / 5
            thirdAnimator.duration = value / 5
            fourthAnimator.duration = value / 5
            fifthAnimator.duration = value / 5
            field = value
        }

    var seconds: Int = 0
        set(value) {
            binding.tvSeconds.text = context.resources.getQuantityString(
                R.plurals.seconds, value, value
            )
            field = value
        }

    // Done as a field so that we don't have to compute on each tab if animations are enabled
    private val animationsEnabled = DeviceUtils.hasAnimationsAnimatorDurationEnabled(context)

    val binding = PlayerFastSeekSecondsViewBinding.inflate(LayoutInflater.from(context), this)

    init {
        orientation = VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    fun setForwarding(isForward: Boolean) {
        binding.triangleContainer.rotation = if (isForward) 0f else 180f
    }

    fun startAnimation() {
        stopAnimation()

        if (animationsEnabled) {
            firstAnimator.start()
        } else {
            // If no animations are enable show the arrow(s) without animation
            showWithoutAnimation()
        }
    }

    fun stopAnimation() {
        firstAnimator.cancel()
        secondAnimator.cancel()
        thirdAnimator.cancel()
        fourthAnimator.cancel()
        fifthAnimator.cancel()

        reset()
    }

    private fun reset() {
        binding.icon1.alpha = 0f
        binding.icon2.alpha = 0f
        binding.icon3.alpha = 0f
    }

    private fun showWithoutAnimation() {
        binding.icon1.alpha = 1f
        binding.icon2.alpha = 1f
        binding.icon3.alpha = 1f
    }

    private val firstAnimator: ValueAnimator = CustomValueAnimator(
        {
            binding.icon1.alpha = 0f
            binding.icon2.alpha = 0f
            binding.icon3.alpha = 0f
        },
        {
            binding.icon1.alpha = it
        },
        {
            secondAnimator.start()
        }
    )

    private val secondAnimator: ValueAnimator = CustomValueAnimator(
        {
            binding.icon1.alpha = 1f
            binding.icon2.alpha = 0f
            binding.icon3.alpha = 0f
        },
        {
            binding.icon2.alpha = it
        },
        {
            thirdAnimator.start()
        }
    )

    private val thirdAnimator: ValueAnimator = CustomValueAnimator(
        {
            binding.icon1.alpha = 1f
            binding.icon2.alpha = 1f
            binding.icon3.alpha = 0f
        },
        {
            binding.icon1.alpha = 1f - binding.icon3.alpha
            binding.icon3.alpha = it
        },
        {
            fourthAnimator.start()
        }
    )

    private val fourthAnimator: ValueAnimator = CustomValueAnimator(
        {
            binding.icon1.alpha = 0f
            binding.icon2.alpha = 1f
            binding.icon3.alpha = 1f
        },
        {
            binding.icon2.alpha = 1f - it
        },
        {
            fifthAnimator.start()
        }
    )

    private val fifthAnimator: ValueAnimator = CustomValueAnimator(
        {
            binding.icon1.alpha = 0f
            binding.icon2.alpha = 0f
            binding.icon3.alpha = 1f
        },
        {
            binding.icon3.alpha = 1f - it
        },
        {
            firstAnimator.start()
        }
    )

    private inner class CustomValueAnimator(
        start: () -> Unit,
        update: (value: Float) -> Unit,
        end: () -> Unit
    ) : ValueAnimator() {

        init {
            duration = cycleDuration / 5
            setFloatValues(0f, 1f)

            addUpdateListener { update(it.animatedValue as Float) }
            addListener(object : AnimatorListener {
                override fun onAnimationStart(animation: Animator?) {
                    start()
                }

                override fun onAnimationEnd(animation: Animator?) {
                    end()
                }

                override fun onAnimationCancel(animation: Animator?) = Unit

                override fun onAnimationRepeat(animation: Animator?) = Unit
            })
        }
    }
}
