package org.schabi.newpipe.player.gesture

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.ktx.AnimationType
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.ui.MainPlayerUi
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * GestureListener for the player
 *
 * While [BasePlayerGestureListener] contains the logic behind the single gestures
 * this class focuses on the visual aspect like hiding and showing the controls or changing
 * volume/brightness during scrolling for specific events.
 */
class MainPlayerGestureListener(
    private val playerUi: MainPlayerUi
) : BasePlayerGestureListener(playerUi), OnTouchListener {
    private var isMoving = false

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        super.onTouch(v, event)
        if (event.action == MotionEvent.ACTION_UP && isMoving) {
            isMoving = false
            onScrollEnd(event)
        }
        return when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                v.parent?.requestDisallowInterceptTouchEvent(playerUi.isFullscreen)
                true
            }
            MotionEvent.ACTION_UP -> {
                v.parent?.requestDisallowInterceptTouchEvent(false)
                false
            }
            else -> true
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        if (DEBUG)
            Log.d(TAG, "onSingleTapConfirmed() called with: e = [$e]")

        if (isDoubleTapping)
            return true
        super.onSingleTapConfirmed(e)

        if (player.currentState != Player.STATE_BLOCKED)
            onSingleTap()
        return true
    }

    private fun onScrollVolume(distanceY: Float) {
        // If we just started sliding, change the progress bar to match the system volume
        if (binding.volumeRelativeLayout.visibility != View.VISIBLE) {
            val volumePercent: Float =
                player.audioReactor.volume / player.audioReactor.maxVolume.toFloat()
            binding.volumeProgressBar.progress = (volumePercent * MAX_GESTURE_LENGTH).toInt()
        }

        binding.volumeProgressBar.incrementProgressBy(distanceY.toInt())
        val currentProgressPercent: Float =
            binding.volumeProgressBar.progress.toFloat() / MAX_GESTURE_LENGTH
        val currentVolume = (player.audioReactor.maxVolume * currentProgressPercent).toInt()
        player.audioReactor.volume = currentVolume
        if (DEBUG) {
            Log.d(TAG, "onScroll().volumeControl, currentVolume = $currentVolume")
        }

        binding.volumeImageView.setImageDrawable(
            AppCompatResources.getDrawable(
                player.context,
                when {
                    currentProgressPercent <= 0 -> R.drawable.ic_volume_off
                    currentProgressPercent < 0.25 -> R.drawable.ic_volume_mute
                    currentProgressPercent < 0.75 -> R.drawable.ic_volume_down
                    else -> R.drawable.ic_volume_up
                }
            )
        )

        if (binding.volumeRelativeLayout.visibility != View.VISIBLE) {
            binding.volumeRelativeLayout.animate(true, 200, AnimationType.SCALE_AND_ALPHA)
        }
        if (binding.brightnessRelativeLayout.visibility == View.VISIBLE) {
            binding.volumeRelativeLayout.visibility = View.GONE
        }
    }

    private fun onScrollBrightness(distanceY: Float) {
        val parent: AppCompatActivity = playerUi.parentActivity.orElse(null) ?: return
        val window = parent.window
        val layoutParams = window.attributes
        val bar: ProgressBar = binding.brightnessProgressBar
        val oldBrightness = layoutParams.screenBrightness
        bar.progress = (bar.max * max(0f, min(1f, oldBrightness))).toInt()
        bar.incrementProgressBy(distanceY.toInt())
        val currentProgressPercent = bar.progress.toFloat() / bar.max
        layoutParams.screenBrightness = currentProgressPercent
        window.attributes = layoutParams

        // Save current brightness level
        PlayerHelper.setScreenBrightness(parent, currentProgressPercent)
        if (DEBUG) {
            Log.d(
                TAG,
                "onScroll().brightnessControl, " +
                    "currentBrightness = " + currentProgressPercent
            )
        }
        binding.brightnessImageView.setImageDrawable(
            AppCompatResources.getDrawable(
                player.context,
                if (currentProgressPercent < 0.25) R.drawable.ic_brightness_low else if (currentProgressPercent < 0.75) R.drawable.ic_brightness_medium else R.drawable.ic_brightness_high
            )
        )
        if (binding.brightnessRelativeLayout.visibility != View.VISIBLE) {
            binding.brightnessRelativeLayout.animate(true, 200, AnimationType.SCALE_AND_ALPHA)
        }
        if (binding.volumeRelativeLayout.visibility == View.VISIBLE) {
            binding.volumeRelativeLayout.visibility = View.GONE
        }
    }

    override fun onScrollEnd(event: MotionEvent) {
        super.onScrollEnd(event)
        if (binding.volumeRelativeLayout.visibility == View.VISIBLE) {
            binding.volumeRelativeLayout.animate(false, 200, AnimationType.SCALE_AND_ALPHA, 200)
        }
        if (binding.brightnessRelativeLayout.visibility == View.VISIBLE) {
            binding.brightnessRelativeLayout.animate(false, 200, AnimationType.SCALE_AND_ALPHA, 200)
        }
    }

    override fun onScroll(
        initialEvent: MotionEvent,
        movingEvent: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {

        if (!playerUi.isFullscreen) {
            return false
        }

        val isTouchingStatusBar: Boolean = initialEvent.y < getStatusBarHeight(player.context)
        val isTouchingNavigationBar: Boolean =
            initialEvent.y > (binding.root.height - getNavigationBarHeight(player.context))
        if (isTouchingStatusBar || isTouchingNavigationBar) {
            return false
        }

        val insideThreshold = abs(movingEvent.y - initialEvent.y) <= MOVEMENT_THRESHOLD
        if (
            !isMoving && (insideThreshold || abs(distanceX) > abs(distanceY)) ||
            player.currentState == Player.STATE_COMPLETED
        ) {
            return false
        }

        isMoving = true

        // -- Brightness and Volume control --
        val isBrightnessGestureEnabled = PlayerHelper.isBrightnessGestureEnabled(player.context)
        val isVolumeGestureEnabled = PlayerHelper.isVolumeGestureEnabled(player.context)
        if (isBrightnessGestureEnabled && isVolumeGestureEnabled) {
            if (getDisplayHalfPortion(initialEvent) === DisplayPortion.LEFT_HALF) {
                onScrollBrightness(distanceY)
            } else /* DisplayPortion.RIGHT_HALF */ {
                onScrollVolume(distanceY)
            }
        } else if (isBrightnessGestureEnabled) {
            onScrollBrightness(distanceY)
        } else if (isVolumeGestureEnabled) {
            onScrollVolume(distanceY)
        }

        return true
    }

    override fun getDisplayPortion(e: MotionEvent): DisplayPortion {
        return when {
            e.x < binding.root.width / 3.0 -> DisplayPortion.LEFT
            e.x > binding.root.width * 2.0 / 3.0 -> DisplayPortion.RIGHT
            else -> DisplayPortion.MIDDLE
        }
    }

    override fun getDisplayHalfPortion(e: MotionEvent): DisplayPortion {
        return when {
            e.x < binding.root.width / 2.0 -> DisplayPortion.LEFT_HALF
            else -> DisplayPortion.RIGHT_HALF
        }
    }

    companion object {
        private val TAG = MainPlayerGestureListener::class.java.simpleName
        private val DEBUG = MainActivity.DEBUG
        private const val MOVEMENT_THRESHOLD = 40
        const val MAX_GESTURE_LENGTH = 0.75f

        private fun getNavigationBarHeight(context: Context): Int {
            val resId = context.resources
                .getIdentifier("navigation_bar_height", "dimen", "android")
            return if (resId > 0) {
                context.resources.getDimensionPixelSize(resId)
            } else 0
        }

        private fun getStatusBarHeight(context: Context): Int {
            val resId = context.resources
                .getIdentifier("status_bar_height", "dimen", "android")
            return if (resId > 0) {
                context.resources.getDimensionPixelSize(resId)
            } else 0
        }
    }
}
