package org.schabi.newpipe.player.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Insets
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowMetrics
import android.view.animation.AnticipateInterpolator
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.math.MathUtils
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.SubtitleView
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.PlayerBinding
import org.schabi.newpipe.databinding.PlayerPopupCloseOverlayBinding
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.gesture.BasePlayerGestureListener
import org.schabi.newpipe.player.gesture.PopupPlayerGestureListener
import org.schabi.newpipe.player.helper.PlayerHelper
import kotlin.math.sqrt

class PopupPlayerUi(player: Player,
                    playerBinding: PlayerBinding) : VideoPlayerUi(player, playerBinding) {
    /*//////////////////////////////////////////////////////////////////////////
    // Popup player
    ////////////////////////////////////////////////////////////////////////// */
    var closeOverlayBinding: PlayerPopupCloseOverlayBinding? = null
        private set
    var isPopupClosing: Boolean = false
        private set

    //endregion
    var screenWidth: Int = 0
        private set
    var screenHeight: Int = 0
        private set
    var popupLayoutParams: WindowManager.LayoutParams? = null // null if player is not popup
        private set
    val windowManager: WindowManager?

    /*//////////////////////////////////////////////////////////////////////////
    // Constructor, setup, destroy
    ////////////////////////////////////////////////////////////////////////// */
    //region Constructor, setup, destroy
    init {
        windowManager = ContextCompat.getSystemService(context, WindowManager::class.java)
    }

    public override fun setupAfterIntent() {
        super.setupAfterIntent()
        initPopup()
        initPopupCloseOverlay()
    }

    public override fun buildGestureListener(): BasePlayerGestureListener {
        return PopupPlayerGestureListener(this)
    }

    @SuppressLint("RtlHardcoded")
    private fun initPopup() {
        if (MainActivity.Companion.DEBUG) {
            Log.d(TAG, "initPopup() called")
        }

        // Popup is already added to windowManager
        if (popupHasParent()) {
            return
        }
        updateScreenSize()
        popupLayoutParams = retrievePopupLayoutParamsFromPrefs()
        binding!!.surfaceView.setHeights(popupLayoutParams!!.height, popupLayoutParams!!.height)
        checkPopupPositionBounds()
        binding!!.loadingPanel.setMinimumWidth(popupLayoutParams!!.width)
        binding!!.loadingPanel.setMinimumHeight(popupLayoutParams!!.height)
        windowManager!!.addView(binding!!.getRoot(), popupLayoutParams)
        setupVideoSurfaceIfNeeded() // now there is a parent, we can setup video surface

        // Popup doesn't have aspectRatio selector, using FIT automatically
        setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    }

    @SuppressLint("RtlHardcoded")
    private fun initPopupCloseOverlay() {
        if (MainActivity.Companion.DEBUG) {
            Log.d(TAG, "initPopupCloseOverlay() called")
        }

        // closeOverlayView is already added to windowManager
        if (closeOverlayBinding != null) {
            return
        }
        closeOverlayBinding = PlayerPopupCloseOverlayBinding.inflate(LayoutInflater.from(context))
        val closeOverlayLayoutParams: WindowManager.LayoutParams = buildCloseOverlayLayoutParams()
        closeOverlayBinding!!.closeButton.setVisibility(View.GONE)
        windowManager!!.addView(closeOverlayBinding!!.getRoot(), closeOverlayLayoutParams)
    }

    override fun setupElementsVisibility() {
        binding!!.fullScreenButton.setVisibility(View.VISIBLE)
        binding!!.screenRotationButton.setVisibility(View.GONE)
        binding!!.resizeTextView.setVisibility(View.GONE)
        binding!!.getRoot().findViewById<View>(R.id.metadataView).setVisibility(View.GONE)
        binding!!.queueButton.setVisibility(View.GONE)
        binding!!.segmentsButton.setVisibility(View.GONE)
        binding!!.moreOptionsButton.setVisibility(View.GONE)
        binding!!.topControls.setOrientation(LinearLayout.HORIZONTAL)
        binding!!.primaryControls.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT
        binding!!.secondaryControls.setAlpha(1.0f)
        binding!!.secondaryControls.setVisibility(View.VISIBLE)
        binding!!.secondaryControls.setTranslationY(0f)
        binding!!.share.setVisibility(View.GONE)
        binding!!.playWithKodi.setVisibility(View.GONE)
        binding!!.openInBrowser.setVisibility(View.GONE)
        binding!!.switchMute.setVisibility(View.GONE)
        binding!!.playerCloseButton.setVisibility(View.GONE)
        binding!!.topControls.bringToFront()
        binding!!.topControls.setClickable(false)
        binding!!.topControls.setFocusable(false)
        binding!!.bottomControls.bringToFront()
        super.setupElementsVisibility()
    }

    override fun setupElementsSize(resources: Resources) {
        setupElementsSize(
                0,
                0,
                resources.getDimensionPixelSize(R.dimen.player_popup_controls_padding),
                resources.getDimensionPixelSize(R.dimen.player_popup_buttons_padding)
        )
    }

    public override fun removeViewFromParent() {
        // view was added by windowManager for popup player
        windowManager!!.removeViewImmediate(binding!!.getRoot())
    }

    public override fun destroy() {
        super.destroy()
        removePopupFromView()
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Broadcast receiver
    ////////////////////////////////////////////////////////////////////////// */
    //region Broadcast receiver
    public override fun onBroadcastReceived(intent: Intent) {
        super.onBroadcastReceived(intent)
        if ((Intent.ACTION_CONFIGURATION_CHANGED == intent.getAction())) {
            updateScreenSize()
            changePopupSize(popupLayoutParams!!.width)
            checkPopupPositionBounds()
        } else if (player.isPlaying() || player.isLoading()) {
            if ((Intent.ACTION_SCREEN_OFF == intent.getAction())) {
                // Use only audio source when screen turns off while popup player is playing
                player.useVideoSource(false)
            } else if ((Intent.ACTION_SCREEN_ON == intent.getAction())) {
                // Restore video source when screen turns on and user was watching video in popup
                player.useVideoSource(true)
            }
        }
    }
    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Popup position and size
    ////////////////////////////////////////////////////////////////////////// */
    //region Popup position and size
    /**
     * Check if [.popupLayoutParams]' position is within a arbitrary boundary
     * that goes from (0, 0) to (screenWidth, screenHeight).
     *
     *
     * If it's out of these boundaries, [.popupLayoutParams]' position is changed
     * and `true` is returned to represent this change.
     *
     */
    fun checkPopupPositionBounds() {
        if (MainActivity.Companion.DEBUG) {
            Log.d(TAG, ("checkPopupPositionBounds() called with: "
                    + "screenWidth = [" + screenWidth + "], "
                    + "screenHeight = [" + screenHeight + "]"))
        }
        if (popupLayoutParams == null) {
            return
        }
        popupLayoutParams!!.x = MathUtils.clamp(popupLayoutParams!!.x, 0, (screenWidth
                - popupLayoutParams!!.width))
        popupLayoutParams!!.y = MathUtils.clamp(popupLayoutParams!!.y, 0, (screenHeight
                - popupLayoutParams!!.height))
    }

    fun updateScreenSize() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics: WindowMetrics = windowManager!!.getCurrentWindowMetrics()
            val bounds: Rect = windowMetrics.getBounds()
            val windowInsets: WindowInsets = windowMetrics.getWindowInsets()
            val insets: Insets = windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout())
            screenWidth = bounds.width() - (insets.left + insets.right)
            screenHeight = bounds.height() - (insets.top + insets.bottom)
        } else {
            val metrics: DisplayMetrics = DisplayMetrics()
            windowManager!!.getDefaultDisplay().getMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
        }
        if (MainActivity.Companion.DEBUG) {
            Log.d(TAG, ("updateScreenSize() called: screenWidth = ["
                    + screenWidth + "], screenHeight = [" + screenHeight + "]"))
        }
    }

    /**
     * Changes the size of the popup based on the width.
     * @param width the new width, height is calculated with
     * [PlayerHelper.getMinimumVideoHeight]
     */
    fun changePopupSize(width: Int) {
        if (MainActivity.Companion.DEBUG) {
            Log.d(TAG, "changePopupSize() called with: width = [" + width + "]")
        }
        if (anyPopupViewIsNull()) {
            return
        }
        val minimumWidth: Float = context.getResources().getDimension(R.dimen.popup_minimum_width)
        val actualWidth: Int = MathUtils.clamp(width, minimumWidth.toInt(), screenWidth)
        val actualHeight: Int = PlayerHelper.getMinimumVideoHeight(width.toFloat()).toInt()
        if (MainActivity.Companion.DEBUG) {
            Log.d(TAG, ("updatePopupSize() updated values:"
                    + "  width = [" + actualWidth + "], height = [" + actualHeight + "]"))
        }
        popupLayoutParams!!.width = actualWidth
        popupLayoutParams!!.height = actualHeight
        binding!!.surfaceView.setHeights(popupLayoutParams!!.height, popupLayoutParams!!.height)
        windowManager!!.updateViewLayout(binding!!.getRoot(), popupLayoutParams)
    }

    override fun calculateMaxEndScreenThumbnailHeight(bitmap: Bitmap): Float {
        // no need for the end screen thumbnail to be resized on popup player: it's only needed
        // for the main player so that it is enlarged correctly inside the fragment
        return bitmap.getHeight().toFloat()
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Popup closing
    ////////////////////////////////////////////////////////////////////////// */
    //region Popup closing
    fun closePopup() {
        if (MainActivity.Companion.DEBUG) {
            Log.d(TAG, "closePopup() called, isPopupClosing = " + isPopupClosing)
        }
        if (isPopupClosing) {
            return
        }
        isPopupClosing = true
        player.saveStreamProgressState()
        windowManager!!.removeView(binding!!.getRoot())
        animatePopupOverlayAndFinishService()
    }

    fun removePopupFromView() {
        // wrap in try-catch since it could sometimes generate errors randomly
        try {
            if (popupHasParent()) {
                windowManager!!.removeView(binding!!.getRoot())
            }
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Failed to remove popup from window manager", e)
        }
        try {
            val closeOverlayHasParent: Boolean = (closeOverlayBinding != null
                    && closeOverlayBinding!!.getRoot().getParent() != null)
            if (closeOverlayHasParent) {
                windowManager!!.removeView(closeOverlayBinding!!.getRoot())
            }
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Failed to remove popup overlay from window manager", e)
        }
    }

    private fun animatePopupOverlayAndFinishService() {
        val targetTranslationY: Int = ((closeOverlayBinding!!.closeButton.getRootView().getHeight()
                - closeOverlayBinding!!.closeButton.getY())).toInt()
        closeOverlayBinding!!.closeButton.animate().setListener(null).cancel()
        closeOverlayBinding!!.closeButton.animate()
                .setInterpolator(AnticipateInterpolator())
                .translationY(targetTranslationY.toFloat())
                .setDuration(400)
                .setListener(object : AnimatorListenerAdapter() {
                    public override fun onAnimationCancel(animation: Animator) {
                        end()
                    }

                    public override fun onAnimationEnd(animation: Animator) {
                        end()
                    }

                    private fun end() {
                        windowManager!!.removeView(closeOverlayBinding!!.getRoot())
                        closeOverlayBinding = null
                        player.getService().stopService()
                    }
                }).start()
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Playback states
    ////////////////////////////////////////////////////////////////////////// */
    //region Playback states
    private fun changePopupWindowFlags(flags: Int) {
        if (MainActivity.Companion.DEBUG) {
            Log.d(TAG, "changePopupWindowFlags() called with: flags = [" + flags + "]")
        }
        if (!anyPopupViewIsNull()) {
            popupLayoutParams!!.flags = flags
            windowManager!!.updateViewLayout(binding!!.getRoot(), popupLayoutParams)
        }
    }

    public override fun onPlaying() {
        super.onPlaying()
        changePopupWindowFlags(ONGOING_PLAYBACK_WINDOW_FLAGS)
    }

    public override fun onPaused() {
        super.onPaused()
        changePopupWindowFlags(IDLE_WINDOW_FLAGS)
    }

    public override fun onCompleted() {
        super.onCompleted()
        changePopupWindowFlags(IDLE_WINDOW_FLAGS)
    }

    override fun setupSubtitleView(captionScale: Float) {
        val captionRatio: Float = (captionScale - 1.0f) / 5.0f + 1.0f
        binding!!.subtitleView.setFractionalTextSize(
                SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * captionRatio)
    }

    override fun onPlaybackSpeedClicked() {
        playbackSpeedPopupMenu!!.show()
        isSomePopupMenuVisible = true
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Gestures
    ////////////////////////////////////////////////////////////////////////// */
    //region Gestures
    private fun distanceFromCloseButton(popupMotionEvent: MotionEvent): Int {
        val closeOverlayButtonX: Int = (closeOverlayBinding!!.closeButton.getLeft()
                + closeOverlayBinding!!.closeButton.getWidth() / 2)
        val closeOverlayButtonY: Int = (closeOverlayBinding!!.closeButton.getTop()
                + closeOverlayBinding!!.closeButton.getHeight() / 2)
        val fingerX: Float = popupLayoutParams!!.x + popupMotionEvent.getX()
        val fingerY: Float = popupLayoutParams!!.y + popupMotionEvent.getY()
        return sqrt(((closeOverlayButtonX - fingerX).pow(2.0) + (closeOverlayButtonY - fingerY).pow(2.0))).toInt()
    }

    private val closingRadius: Float
        private get() {
            val buttonRadius: Int = closeOverlayBinding!!.closeButton.getWidth() / 2
            // 20% wider than the button itself
            return buttonRadius * 1.2f
        }

    fun isInsideClosingRadius(popupMotionEvent: MotionEvent): Boolean {
        return distanceFromCloseButton(popupMotionEvent) <= closingRadius
    }
    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Popup & closing overlay layout params + saving popup position and size
    ////////////////////////////////////////////////////////////////////////// */
    //region Popup & closing overlay layout params + saving popup position and size
    /**
     * `screenWidth` and `screenHeight` must have been initialized.
     * @return the popup starting layout params
     */
    @SuppressLint("RtlHardcoded")
    fun retrievePopupLayoutParamsFromPrefs(): WindowManager.LayoutParams {
        val prefs: SharedPreferences = getPlayer().getPrefs()
        val context: Context = getPlayer().getContext()
        val popupRememberSizeAndPos: Boolean = prefs.getBoolean(
                context.getString(R.string.popup_remember_size_pos_key), true)
        val defaultSize: Float = context.getResources().getDimension(R.dimen.popup_default_width)
        val popupWidth: Float = if (popupRememberSizeAndPos) prefs.getFloat(context.getString(R.string.popup_saved_width_key), defaultSize) else defaultSize
        val popupHeight: Float = PlayerHelper.getMinimumVideoHeight(popupWidth)
        val params: WindowManager.LayoutParams = WindowManager.LayoutParams(popupWidth.toInt(), popupHeight.toInt(),
                popupLayoutParamType(),
                IDLE_WINDOW_FLAGS,
                PixelFormat.TRANSLUCENT)
        params.gravity = Gravity.LEFT or Gravity.TOP
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        val centerX: Int = (screenWidth / 2f - popupWidth / 2f).toInt()
        val centerY: Int = (screenHeight / 2f - popupHeight / 2f).toInt()
        params.x = if (popupRememberSizeAndPos) prefs.getInt(context.getString(R.string.popup_saved_x_key), centerX) else centerX
        params.y = if (popupRememberSizeAndPos) prefs.getInt(context.getString(R.string.popup_saved_y_key), centerY) else centerY
        return params
    }

    fun savePopupPositionAndSizeToPrefs() {
        if (popupLayoutParams != null) {
            val context: Context = getPlayer().getContext()
            getPlayer().getPrefs().edit()
                    .putFloat(context.getString(R.string.popup_saved_width_key),
                            popupLayoutParams!!.width.toFloat())
                    .putInt(context.getString(R.string.popup_saved_x_key),
                            popupLayoutParams!!.x)
                    .putInt(context.getString(R.string.popup_saved_y_key),
                            popupLayoutParams!!.y)
                    .apply()
        }
    }

    //endregion
    /*//////////////////////////////////////////////////////////////////////////
    // Getters
    ////////////////////////////////////////////////////////////////////////// */
    //region Getters
    private fun popupHasParent(): Boolean {
        return ((binding != null
                ) && binding!!.getRoot().getLayoutParams() is WindowManager.LayoutParams
                && (binding!!.getRoot().getParent() != null))
    }

    private fun anyPopupViewIsNull(): Boolean {
        return (popupLayoutParams == null) || (windowManager == null
                ) || (binding!!.getRoot().getParent() == null)
    }

    companion object {
        private val TAG: String = PopupPlayerUi::class.java.getSimpleName()

        /**
         * Maximum opacity allowed for Android 12 and higher to allow touches on other apps when using
         * NewPipe's popup player.
         *
         *
         *
         * This value is hardcoded instead of being get dynamically with the method linked of the
         * constant documentation below, because it is not static and popup player layout parameters
         * are generated with static methods.
         *
         *
         * @see WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
         */
        private val MAXIMUM_OPACITY_ALLOWED_FOR_S_AND_HIGHER: Float = 0.8f

        /*//////////////////////////////////////////////////////////////////////////
    // Popup player window manager
    ////////////////////////////////////////////////////////////////////////// */
        val IDLE_WINDOW_FLAGS: Int = (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        val ONGOING_PLAYBACK_WINDOW_FLAGS: Int = (IDLE_WINDOW_FLAGS
                or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        @SuppressLint("RtlHardcoded")
        fun buildCloseOverlayLayoutParams(): WindowManager.LayoutParams {
            val flags: Int = (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            val closeOverlayLayoutParams: WindowManager.LayoutParams = WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                    popupLayoutParamType(),
                    flags,
                    PixelFormat.TRANSLUCENT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Setting maximum opacity allowed for touch events to other apps for Android 12 and
                // higher to prevent non interaction when using other apps with the popup player
                closeOverlayLayoutParams.alpha = MAXIMUM_OPACITY_ALLOWED_FOR_S_AND_HIGHER
            }
            closeOverlayLayoutParams.gravity = Gravity.LEFT or Gravity.TOP
            closeOverlayLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            return closeOverlayLayoutParams
        }

        fun popupLayoutParamType(): Int {
            return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_PHONE else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }
    }
}
