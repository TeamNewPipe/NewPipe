package org.schabi.newpipe.player.gesture

import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.round
import kotlin.math.sign
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.ktx.AnimationType
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.helper.AudioReactor
import org.schabi.newpipe.player.helper.PlaybackParameterPreferences
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.ui.MainPlayerUi
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.ThemeHelper.getAndroidDimenPx

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

    private var isSwipeSeeking = false
    private var accumulatedSeek = 0f
    private var swipeSeekStartPosition = 0L
    private var swipeSeekTargetPosition = 0L

    private var isPendingFullscreenSwipe = false
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDelegatingToBottomSheet = false
    private val singleFingerGestureClassifier =
        SingleFingerGestureClassifier(MOVEMENT_THRESHOLD.toFloat())

    private var twoFingerGestureState = TwoFingerGestureState.IDLE
    private var suppressSingleTouchUntilUp = false
    private var twoFingerInitialCenterX = 0f
    private var twoFingerInitialCenterY = 0f
    private var twoFingerInitialSpan = 0f
    private var twoFingerStartSpeed = 1f
    private var twoFingerSpeedStep = PlaybackParameterPreferences.DEFAULT_ADJUSTMENT_STEP
    private var lastGestureSpeed = 1f
    private var twoFingerStartZoom = 1f
    private var lastGestureZoom = 1f
    private var twoFingerStartTranslationX = 0f
    private var twoFingerStartTranslationY = 0f

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (playerUi.isTouchLocked) {
            v.parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }
        if (playerUi.handleSphericalTouch(v, event)) return true
        if (handleTwoFingerGesture(v, event)) {
            return true
        }

        if (isDelegatingToBottomSheet) {
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                super.onTouch(v, event)
                isDelegatingToBottomSheet = false
            }
            v.parent?.requestDisallowInterceptTouchEvent(false)
            return false
        }

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            initialTouchX = event.x
            initialTouchY = event.y
            singleFingerGestureClassifier.reset()
        }

        val fullscreenGestureEnabled = PlayerHelper.isFullscreenGestureEnabled(player.context)
        val swipeDownToMiniplayerEnabled = isSwipeDownToMiniplayerEnabled()

        super.onTouch(v, event)

        if (event.actionMasked == MotionEvent.ACTION_MOVE &&
            shouldDelegateDownwardSwipeToBottomSheet(
                playerUi.isFullscreen,
                swipeDownToMiniplayerEnabled,
                event.x - initialTouchX,
                event.y - initialTouchY
            )
        ) {
            isDelegatingToBottomSheet = true
            isMoving = false
            isPendingFullscreenSwipe = false
            singleFingerGestureClassifier.reset()
            v.parent?.requestDisallowInterceptTouchEvent(false)
            return false
        }

        val gestureEnded = event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        if (gestureEnded && isMoving) {
            isMoving = false
            onScrollEnd(event)
        }
        if (gestureEnded) {
            singleFingerGestureClassifier.reset()
        }
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                v.parent?.requestDisallowInterceptTouchEvent(
                    playerUi.isFullscreen ||
                        fullscreenGestureEnabled ||
                        !swipeDownToMiniplayerEnabled
                )
                true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.parent?.requestDisallowInterceptTouchEvent(false)
                false
            }

            else -> true
        }
    }

    private fun handleTwoFingerGesture(v: View, event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN &&
            event.pointerCount == 2 &&
            !player.exoPlayerIsNull() &&
            player.currentState != Player.STATE_COMPLETED &&
            binding.surfaceView.isVisible
        ) {
            cancelSingleFingerGesture(event)
            restoreHoldToSpeed()
            isMoving = false
            isSwipeSeeking = false
            isPendingFullscreenSwipe = false
            isDelegatingToBottomSheet = false
            singleFingerGestureClassifier.reset()
            binding.swipeSeekDisplay.isVisible = false
            binding.volumeRelativeLayout.isVisible = false
            binding.brightnessRelativeLayout.isVisible = false

            twoFingerInitialCenterX = centerX(event)
            twoFingerInitialCenterY = centerY(event)
            twoFingerInitialSpan = pointerSpan(event)
            twoFingerStartSpeed = player.playbackSpeed
            twoFingerSpeedStep = PlaybackParameterPreferences.getAdjustmentStep(player.context)
            lastGestureSpeed = twoFingerStartSpeed
            twoFingerStartZoom = binding.surfaceView.userZoomScale
            lastGestureZoom = twoFingerStartZoom
            twoFingerStartTranslationX = binding.surfaceView.userTranslationX
            twoFingerStartTranslationY = binding.surfaceView.userTranslationY
            twoFingerGestureState = TwoFingerGestureState.PENDING
            suppressSingleTouchUntilUp = true
            v.parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }

        if (!suppressSingleTouchUntilUp) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    updateTwoFingerGesture(v, event)
                }
            }

            MotionEvent.ACTION_POINTER_UP -> finishTwoFingerGesture()

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                finishTwoFingerGesture()
                suppressSingleTouchUntilUp = false
                v.parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun cancelSingleFingerGesture(event: MotionEvent) {
        val cancelEvent = MotionEvent.obtain(event)
        cancelEvent.action = MotionEvent.ACTION_CANCEL
        playerUi.gestureDetector.onTouchEvent(cancelEvent)
        cancelEvent.recycle()
    }

    private fun updateTwoFingerGesture(v: View, event: MotionEvent) {
        val verticalMovement = twoFingerInitialCenterY - centerY(event)
        val horizontalMovement = centerX(event) - twoFingerInitialCenterX
        val spanChange = abs(pointerSpan(event) - twoFingerInitialSpan)
        val lockThreshold = TWO_FINGER_LOCK_THRESHOLD_DP *
            player.context.resources.displayMetrics.density

        if (twoFingerGestureState == TwoFingerGestureState.PENDING) {
            val pinchToZoomEnabled = PlayerHelper.isPinchToZoomGestureEnabled(player.context)
            twoFingerGestureState = applyGestureSettings(
                classifyTwoFingerGesture(
                    verticalMovement,
                    horizontalMovement,
                    spanChange,
                    lockThreshold,
                    twoFingerStartZoom > 1f && pinchToZoomEnabled
                ),
                pinchToZoomEnabled,
                PlayerHelper.isTwoFingerSpeedGestureEnabled(player.context)
            )
            if (twoFingerGestureState == TwoFingerGestureState.SPEED) {
                binding.speedGestureDisplay.text =
                    PlayerHelper.formatSpeed(twoFingerStartSpeed.toDouble())
                binding.speedGestureDisplay.animate(
                    true,
                    150,
                    AnimationType.SCALE_AND_ALPHA
                )
            } else if (twoFingerGestureState == TwoFingerGestureState.ZOOM) {
                binding.speedGestureDisplay.text = formatZoom(twoFingerStartZoom)
                binding.speedGestureDisplay.animate(
                    true,
                    150,
                    AnimationType.SCALE_AND_ALPHA
                )
            }
        }

        when (twoFingerGestureState) {
            TwoFingerGestureState.SPEED -> updateTwoFingerSpeed(v, verticalMovement)
            TwoFingerGestureState.ZOOM -> updateTwoFingerZoom(v, event)
            else -> Unit
        }
    }

    private fun updateTwoFingerSpeed(v: View, verticalMovement: Float) {
        val pixelsPerStep = TWO_FINGER_SPEED_STEP_DP *
            player.context.resources.displayMetrics.density
        val newSpeed = calculatePlaybackSpeed(
            twoFingerStartSpeed,
            verticalMovement,
            pixelsPerStep,
            twoFingerSpeedStep
        )
        if (newSpeed == lastGestureSpeed) {
            return
        }

        player.setPlaybackSpeed(newSpeed)
        binding.speedGestureDisplay.text = PlayerHelper.formatSpeed(newSpeed.toDouble())
        if (newSpeed == NORMAL_PLAYBACK_SPEED && lastGestureSpeed != NORMAL_PLAYBACK_SPEED) {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        lastGestureSpeed = newSpeed
    }

    private fun updateTwoFingerZoom(v: View, event: MotionEvent) {
        val newZoom = calculateZoomScale(
            twoFingerStartZoom,
            pointerSpan(event),
            twoFingerInitialSpan
        )
        binding.surfaceView.setUserTransform(
            newZoom,
            twoFingerStartTranslationX + centerX(event) - twoFingerInitialCenterX,
            twoFingerStartTranslationY + centerY(event) - twoFingerInitialCenterY
        )
        binding.speedGestureDisplay.text = formatZoom(binding.surfaceView.userZoomScale)
        if (binding.surfaceView.userZoomScale == 1f && lastGestureZoom != 1f) {
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        lastGestureZoom = binding.surfaceView.userZoomScale
    }

    private fun finishTwoFingerGesture() {
        val wasActive = twoFingerGestureState == TwoFingerGestureState.SPEED ||
            twoFingerGestureState == TwoFingerGestureState.ZOOM
        if (wasActive && binding.speedGestureDisplay.isVisible) {
            binding.speedGestureDisplay.animate(
                false,
                200,
                AnimationType.SCALE_AND_ALPHA,
                200
            )
        }
        twoFingerGestureState = TwoFingerGestureState.IDLE
    }

    private fun formatZoom(zoomScale: Float): String = "${round(zoomScale * 100).toInt()}%"

    private fun centerX(event: MotionEvent): Float = (event.getX(0) + event.getX(1)) / 2f

    private fun centerY(event: MotionEvent): Float = (event.getY(0) + event.getY(1)) / 2f

    private fun pointerSpan(event: MotionEvent): Float = hypot(
        event.getX(0) - event.getX(1),
        event.getY(0) - event.getY(1)
    )

    private fun isSwipeDownToMiniplayerEnabled(): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(player.context)
            .getBoolean(player.context.getString(R.string.swipe_down_to_miniplayer_key), true)
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        if (DEBUG) {
            Log.d(TAG, "onSingleTapConfirmed() called with: e = [$e]")
        }

        if (isDoubleTapping) {
            return true
        }
        super.onSingleTapConfirmed(e)

        if (player.currentState != Player.STATE_BLOCKED) {
            onSingleTap()
        }
        return true
    }

    private fun onScrollVolume(distanceY: Float) {
        val bar: ProgressBar = binding.volumeProgressBar
        val audioReactor: AudioReactor = player.audioReactor ?: return

        val independentVolume = player.isIndependentPlayerVolumeEnabled
        // Initialize from the selected volume mode at the start of each gesture.
        if (!binding.volumeRelativeLayout.isVisible) {
            val volumePercent: Float = if (independentVolume) {
                player.independentPlayerVolume
            } else {
                audioReactor.volume / audioReactor.maxVolume.toFloat()
            }
            bar.progress = (volumePercent * bar.max).toInt()
        }

        // Update progress bar
        binding.volumeProgressBar.incrementProgressBy(distanceY.toInt())

        // Update volume
        val currentProgressPercent: Float = bar.progress / bar.max.toFloat()
        val currentVolume = (audioReactor.maxVolume * currentProgressPercent).toInt()
        if (independentVolume) {
            player.independentPlayerVolume = currentProgressPercent
        } else {
            audioReactor.volume = currentVolume
        }
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
        val bar: ProgressBar = binding.brightnessProgressBar
        val currentProgressPercent = BrightnessGesture.apply(parent, distanceY, bar.max) ?: return
        // Round only for display: preserve fractional movements in the actual brightness.
        bar.progress = round(bar.max * currentProgressPercent).toInt()
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
        if (isPendingFullscreenSwipe) {
            playerUi.toggleFullscreenWithOrientation()
            isPendingFullscreenSwipe = false
            return
        }
        if (isSwipeSeeking) {
            player.seekTo(swipeSeekTargetPosition)
            binding.swipeSeekDisplay.animate(false, 200, AnimationType.SCALE_AND_ALPHA)
            isSwipeSeeking = false
        }
        if (binding.volumeRelativeLayout.isVisible) {
            binding.volumeRelativeLayout.animate(false, 200, AnimationType.SCALE_AND_ALPHA, 200)
        }
        if (binding.brightnessRelativeLayout.isVisible) {
            binding.brightnessRelativeLayout.animate(false, 200, AnimationType.SCALE_AND_ALPHA, 200)
        }
    }

    private fun onScrollSeek(distanceX: Float) {
        val duration = player.exoPlayer?.duration ?: 0L
        if (duration <= 0L) {
            return
        }

        if (!isSwipeSeeking) {
            isSwipeSeeking = true
            accumulatedSeek = 0f
            swipeSeekStartPosition = player.exoPlayer?.currentPosition ?: 0L
            swipeSeekTargetPosition = swipeSeekStartPosition
            binding.swipeSeekDisplay.animate(true, 200, AnimationType.SCALE_AND_ALPHA)
            binding.volumeRelativeLayout.isVisible = false
            binding.brightnessRelativeLayout.isVisible = false
        }

        accumulatedSeek -= distanceX
        val thresholdPx = SEEK_SWIPE_FAST_THRESHOLD_MS / SEEK_SWIPE_FACTOR
        val deltaMs = if (abs(accumulatedSeek) <= thresholdPx) {
            (accumulatedSeek * SEEK_SWIPE_FACTOR).toLong()
        } else {
            val beyond = abs(accumulatedSeek) - thresholdPx
            (
                sign(accumulatedSeek) *
                    (
                        SEEK_SWIPE_FAST_THRESHOLD_MS +
                            beyond * SEEK_SWIPE_FACTOR * SEEK_SWIPE_FAST_MULTIPLIER
                        )
                ).toLong()
        }

        swipeSeekTargetPosition = (swipeSeekStartPosition + deltaMs).coerceIn(0L, duration)

        val delta = swipeSeekTargetPosition - swipeSeekStartPosition
        val deltaText = (if (delta >= 0) "+" else "-") +
            Localization.getDurationString(abs(delta) / 1000L)
        val targetText = Localization.getDurationString(swipeSeekTargetPosition / 1000L)
        binding.swipeSeekDisplay.text = "$deltaText ($targetText)"
    }

    override fun onScroll(
        initialEvent: MotionEvent?,
        movingEvent: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        if (initialEvent == null) {
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

        val totalDeltaX = movingEvent.x - initialEvent.x
        val totalDeltaY = movingEvent.y - initialEvent.y
        val gestureState = singleFingerGestureClassifier.update(
            totalDeltaX,
            totalDeltaY,
            isFullscreenSwipeEligible(initialEvent, totalDeltaY)
        )
        if (gestureState == SingleFingerGestureClassifier.State.PENDING) {
            return false
        }

        isMoving = true

        when (gestureState) {
            SingleFingerGestureClassifier.State.HORIZONTAL_SEEK -> {
                if (player.currentState == Player.STATE_COMPLETED) {
                    return false
                }
                if (!playerUi.isFullscreen) {
                    return false
                }
                if (PlayerHelper.isSwipeSeekGestureEnabled(player.context)) {
                    onScrollSeek(distanceX)
                }
                return true
            }

            SingleFingerGestureClassifier.State.FULLSCREEN_SWIPE -> {
                isPendingFullscreenSwipe = true
                return true
            }

            SingleFingerGestureClassifier.State.VERTICAL_ADJUSTMENT -> {
                if (player.currentState == Player.STATE_COMPLETED) {
                    return false
                }
                if (!playerUi.isFullscreen) {
                    return false
                }

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

            SingleFingerGestureClassifier.State.PENDING -> return false
        }
    }

    private fun isFullscreenSwipeEligible(initialEvent: MotionEvent, totalDeltaY: Float): Boolean {
        if (!PlayerHelper.isFullscreenGestureEnabled(player.context)) {
            return false
        }
        return if (playerUi.isFullscreen) {
            totalDeltaY > 0 && getDisplayPortion(initialEvent) == DisplayPortion.MIDDLE
        } else {
            totalDeltaY < 0
        }
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
        private const val SEEK_SWIPE_FACTOR = 100f // ms per pixel
        private const val SEEK_SWIPE_FAST_MULTIPLIER = 10f
        private const val SEEK_SWIPE_FAST_THRESHOLD_MS = 60_000L
        private const val TWO_FINGER_LOCK_THRESHOLD_DP = 12f
        private const val TWO_FINGER_SPEED_STEP_DP = 24f
        private const val MIN_PLAYBACK_SPEED = 0.10f
        internal const val MAX_PLAYBACK_SPEED = 10.00f
        private const val NORMAL_PLAYBACK_SPEED = 1.00f

        @JvmStatic
        fun classifyTwoFingerGesture(
            verticalMovement: Float,
            horizontalMovement: Float,
            spanChange: Float,
            threshold: Float,
            alreadyZoomed: Boolean
        ): TwoFingerGestureState {
            val vertical = abs(verticalMovement)
            val horizontal = abs(horizontalMovement)
            if (vertical < threshold && horizontal < threshold && spanChange < threshold) {
                return TwoFingerGestureState.PENDING
            }
            return when {
                spanChange > vertical * 1.25f && spanChange > horizontal * 1.25f ->
                    TwoFingerGestureState.ZOOM

                alreadyZoomed && (vertical >= threshold || horizontal >= threshold) ->
                    TwoFingerGestureState.ZOOM

                vertical > horizontal * 1.25f && vertical > spanChange * 1.25f ->
                    TwoFingerGestureState.SPEED

                else -> TwoFingerGestureState.IGNORED
            }
        }

        @JvmStatic
        fun applyGestureSettings(
            classifiedGesture: TwoFingerGestureState,
            pinchToZoomEnabled: Boolean,
            twoFingerSpeedEnabled: Boolean
        ): TwoFingerGestureState {
            return when (classifiedGesture) {
                TwoFingerGestureState.ZOOM -> if (pinchToZoomEnabled) {
                    classifiedGesture
                } else {
                    TwoFingerGestureState.IGNORED
                }

                TwoFingerGestureState.SPEED -> if (twoFingerSpeedEnabled) {
                    classifiedGesture
                } else {
                    TwoFingerGestureState.IGNORED
                }

                else -> classifiedGesture
            }
        }

        @JvmStatic
        fun calculateZoomScale(
            startZoom: Float,
            currentSpan: Float,
            initialSpan: Float
        ): Float {
            if (initialSpan <= 0f || !initialSpan.isFinite() || !currentSpan.isFinite()) {
                return startZoom.coerceIn(1f, 4f)
            }
            return (startZoom * currentSpan / initialSpan).coerceIn(1f, 4f)
        }

        @JvmStatic
        fun calculatePlaybackSpeed(
            startSpeed: Float,
            verticalMovement: Float,
            pixelsPerStep: Float,
            adjustmentStep: Float
        ): Float {
            if (pixelsPerStep <= 0f) {
                return startSpeed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
            }
            val sanitizedStep =
                PlaybackParameterPreferences.sanitizeAdjustmentStep(adjustmentStep)
            val rawSpeed = startSpeed +
                verticalMovement / pixelsPerStep * sanitizedStep
            return (round(rawSpeed / sanitizedStep) * sanitizedStep)
                .coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
        }

        @JvmStatic
        fun shouldDelegateDownwardSwipeToBottomSheet(
            isFullscreen: Boolean,
            swipeDownToMiniplayerEnabled: Boolean,
            deltaX: Float,
            deltaY: Float
        ): Boolean {
            return swipeDownToMiniplayerEnabled &&
                !isFullscreen &&
                deltaY > MOVEMENT_THRESHOLD &&
                abs(deltaY) > abs(deltaX)
        }
    }

    enum class TwoFingerGestureState {
        IDLE,
        PENDING,
        SPEED,
        ZOOM,
        IGNORED
    }
}
