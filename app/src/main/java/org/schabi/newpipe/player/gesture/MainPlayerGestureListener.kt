package org.schabi.newpipe.player.gesture

import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.ktx.AnimationType
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.helper.AudioReactor
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.ui.MainPlayerUi
import org.schabi.newpipe.util.ThemeHelper.getAndroidDimenPx
import kotlin.math.abs

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
        val bar: ProgressBar = binding.volumeProgressBar
        val audioReactor: AudioReactor = player.audioReactor

        // If we just started sliding, change the progress bar to match the system volume
        if (!binding.volumeRelativeLayout.isVisible) {
            val volumePercent: Float = audioReactor.volume / audioReactor.maxVolume.toFloat()
            bar.progress = (volumePercent * bar.max).toInt()
        }

        // Update progress bar
        binding.volumeProgressBar.incrementProgressBy(distanceY.toInt())

        // Update volume
        val currentProgressPercent: Float = bar.progress / bar.max.toFloat()
        val currentVolume = (audioReactor.maxVolume * currentProgressPercent).toInt()
        audioReactor.volume = currentVolume
        if (DEBUG) {
            Log.d(TAG, "onScroll().volumeControl, currentVolume = $currentVolume")
        }

        // Update player center image
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

        // Make sure the correct layout is visible
        if (!binding.volumeRelativeLayout.isVisible) {
            binding.volumeRelativeLayout.animate(true, 200, AnimationType.SCALE_AND_ALPHA)
        }
        binding.brightnessRelativeLayout.isVisible = false
    }

    private fun onScrollBrightness(distanceY: Float) {
        val parent: AppCompatActivity = playerUi.parentActivity.orElse(null) ?: return
        val window = parent.window
        val layoutParams = window.attributes
        val bar: ProgressBar = binding.brightnessProgressBar

        // Update progress bar
        val oldBrightness = layoutParams.screenBrightness
        bar.progress = (bar.max * oldBrightness.coerceIn(0f, 1f)).toInt()
        bar.incrementProgressBy(distanceY.toInt())

        // Update brightness
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

        // Update player center image
        binding.brightnessImageView.setImageDrawable(
            AppCompatResources.getDrawable(
                player.context,
                when {
                    currentProgressPercent < 0.25 -> R.drawable.ic_brightness_low
                    currentProgressPercent < 0.75 -> R.drawable.ic_brightness_medium
                    else -> R.drawable.ic_brightness_high
                }
            )
        )

        // Make sure the correct layout is visible
        if (!binding.brightnessRelativeLayout.isVisible) {
            binding.brightnessRelativeLayout.animate(true, 200, AnimationType.SCALE_AND_ALPHA)
        }
        binding.volumeRelativeLayout.isVisible = false
    }

    override fun onScrollEnd(event: MotionEvent) {
        super.onScrollEnd(event)
        if (binding.volumeRelativeLayout.isVisible) {
            binding.volumeRelativeLayout.animate(false, 200, AnimationType.SCALE_AND_ALPHA, 200)
        }
        if (binding.brightnessRelativeLayout.isVisible) {
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

        // Calculate heights of status and navigation bars
        val statusBarHeight = getAndroidDimenPx(player.context, "status_bar_height")
        val navigationBarHeight = getAndroidDimenPx(player.context, "navigation_bar_height")

        // Do not handle this event if initially it started from status or navigation bars
        val isTouchingStatusBar = initialEvent.y < statusBarHeight
        val isTouchingNavigationBar = initialEvent.y > (binding.root.height - navigationBarHeight)
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
        if (getDisplayHalfPortion(initialEvent) == DisplayPortion.RIGHT_HALF) {
            when (PlayerHelper.getActionForRightGestureSide(player.context)) {
                player.context.getString(R.string.volume_control_key) ->
                    onScrollVolume(distanceY)
                player.context.getString(R.string.brightness_control_key) ->
                    onScrollBrightness(distanceY)
            }
        } else {
            when (PlayerHelper.getActionForLeftGestureSide(player.context)) {
                player.context.getString(R.string.volume_control_key) ->
                    onScrollVolume(distanceY)
                player.context.getString(R.string.brightness_control_key) ->
                    onScrollBrightness(distanceY)
            }
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
    }
}
