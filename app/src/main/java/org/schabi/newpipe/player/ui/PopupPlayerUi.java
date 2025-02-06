package org.schabi.newpipe.player.ui;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static org.schabi.newpipe.MainActivity.DEBUG;
import static org.schabi.newpipe.player.helper.PlayerHelper.getMinimumVideoHeight;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.AnticipateInterpolator;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.math.MathUtils;

import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.SubtitleView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.PlayerBinding;
import org.schabi.newpipe.databinding.PlayerPopupCloseOverlayBinding;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.gesture.BasePlayerGestureListener;
import org.schabi.newpipe.player.gesture.PopupPlayerGestureListener;
import org.schabi.newpipe.player.helper.PlayerHelper;

public final class PopupPlayerUi extends VideoPlayerUi {
    private static final String TAG = PopupPlayerUi.class.getSimpleName();

    /**
     * Maximum opacity allowed for Android 12 and higher to allow touches on other apps when using
     * NewPipe's popup player.
     *
     * <p>
     * This value is hardcoded instead of being get dynamically with the method linked of the
     * constant documentation below, because it is not static and popup player layout parameters
     * are generated with static methods.
     * </p>
     *
     * @see WindowManager.LayoutParams#FLAG_NOT_TOUCHABLE
     */
    private static final float MAXIMUM_OPACITY_ALLOWED_FOR_S_AND_HIGHER = 0.8f;

    /*//////////////////////////////////////////////////////////////////////////
    // Popup player
    //////////////////////////////////////////////////////////////////////////*/

    private PlayerPopupCloseOverlayBinding closeOverlayBinding;

    private boolean isPopupClosing = false;

    private int screenWidth;
    private int screenHeight;

    /*//////////////////////////////////////////////////////////////////////////
    // Popup player window manager
    //////////////////////////////////////////////////////////////////////////*/

    public static final int IDLE_WINDOW_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
    public static final int ONGOING_PLAYBACK_WINDOW_FLAGS = IDLE_WINDOW_FLAGS
            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;

    private WindowManager.LayoutParams popupLayoutParams; // null if player is not popup
    private final WindowManager windowManager;


    /*//////////////////////////////////////////////////////////////////////////
    // Constructor, setup, destroy
    //////////////////////////////////////////////////////////////////////////*/
    //region Constructor, setup, destroy

    public PopupPlayerUi(@NonNull final Player player,
                         @NonNull final PlayerBinding playerBinding) {
        super(player, playerBinding);
        windowManager = ContextCompat.getSystemService(context, WindowManager.class);
    }

    @Override
    public void setupAfterIntent() {
        super.setupAfterIntent();
        initPopup();
        initPopupCloseOverlay();
    }

    @Override
    BasePlayerGestureListener buildGestureListener() {
        return new PopupPlayerGestureListener(this);
    }

    @SuppressLint("RtlHardcoded")
    private void initPopup() {
        if (DEBUG) {
            Log.d(TAG, "initPopup() called");
        }

        // Popup is already added to windowManager
        if (popupHasParent()) {
            return;
        }

        updateScreenSize();

        popupLayoutParams = retrievePopupLayoutParamsFromPrefs();
        binding.surfaceView.setHeights(popupLayoutParams.height, popupLayoutParams.height);

        checkPopupPositionBounds();

        binding.loadingPanel.setMinimumWidth(popupLayoutParams.width);
        binding.loadingPanel.setMinimumHeight(popupLayoutParams.height);

        windowManager.addView(binding.getRoot(), popupLayoutParams);
        setupVideoSurfaceIfNeeded(); // now there is a parent, we can setup video surface

        // Popup doesn't have aspectRatio selector, using FIT automatically
        setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
    }

    @SuppressLint("RtlHardcoded")
    private void initPopupCloseOverlay() {
        if (DEBUG) {
            Log.d(TAG, "initPopupCloseOverlay() called");
        }

        // closeOverlayView is already added to windowManager
        if (closeOverlayBinding != null) {
            return;
        }

        closeOverlayBinding = PlayerPopupCloseOverlayBinding.inflate(LayoutInflater.from(context));

        final WindowManager.LayoutParams closeOverlayLayoutParams = buildCloseOverlayLayoutParams();
        closeOverlayBinding.closeButton.setVisibility(View.GONE);
        windowManager.addView(closeOverlayBinding.getRoot(), closeOverlayLayoutParams);
    }

    @Override
    protected void setupElementsVisibility() {
        binding.fullScreenButton.setVisibility(View.VISIBLE);
        binding.screenRotationButton.setVisibility(View.GONE);
        binding.resizeTextView.setVisibility(View.GONE);
        binding.getRoot().findViewById(R.id.metadataView).setVisibility(View.GONE);
        binding.queueButton.setVisibility(View.GONE);
        binding.segmentsButton.setVisibility(View.GONE);
        binding.moreOptionsButton.setVisibility(View.GONE);
        binding.topControls.setOrientation(LinearLayout.HORIZONTAL);
        binding.primaryControls.getLayoutParams().width = WRAP_CONTENT;
        binding.secondaryControls.setAlpha(1.0f);
        binding.secondaryControls.setVisibility(View.VISIBLE);
        binding.secondaryControls.setTranslationY(0);
        binding.share.setVisibility(View.GONE);
        binding.playWithKodi.setVisibility(View.GONE);
        binding.openInBrowser.setVisibility(View.GONE);
        binding.switchMute.setVisibility(View.GONE);
        binding.playerCloseButton.setVisibility(View.GONE);
        binding.topControls.bringToFront();
        binding.topControls.setClickable(false);
        binding.topControls.setFocusable(false);
        binding.bottomControls.bringToFront();
        super.setupElementsVisibility();
    }

    @Override
    protected void setupElementsSize(final Resources resources) {
        setupElementsSize(
                0,
                0,
                resources.getDimensionPixelSize(R.dimen.player_popup_controls_padding),
                resources.getDimensionPixelSize(R.dimen.player_popup_buttons_padding)
        );
    }

    @Override
    public void removeViewFromParent() {
        // view was added by windowManager for popup player
        windowManager.removeViewImmediate(binding.getRoot());
    }

    @Override
    public void destroy() {
        super.destroy();
        removePopupFromView();
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Broadcast receiver
    //////////////////////////////////////////////////////////////////////////*/
    //region Broadcast receiver

    @Override
    public void onBroadcastReceived(final Intent intent) {
        super.onBroadcastReceived(intent);
        if (Intent.ACTION_CONFIGURATION_CHANGED.equals(intent.getAction())) {
            updateScreenSize();
            changePopupSize(popupLayoutParams.width);
            checkPopupPositionBounds();
        } else if (player.isPlaying() || player.isLoading()) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                // Use only audio source when screen turns off while popup player is playing
                player.useVideoSource(false);
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                // Restore video source when screen turns on and user was watching video in popup
                player.useVideoSource(true);
            }
        }
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Popup position and size
    //////////////////////////////////////////////////////////////////////////*/
    //region Popup position and size

    /**
     * Check if {@link #popupLayoutParams}' position is within a arbitrary boundary
     * that goes from (0, 0) to (screenWidth, screenHeight).
     * <p>
     * If it's out of these boundaries, {@link #popupLayoutParams}' position is changed
     * and {@code true} is returned to represent this change.
     * </p>
     */
    public void checkPopupPositionBounds() {
        if (DEBUG) {
            Log.d(TAG, "checkPopupPositionBounds() called with: "
                    + "screenWidth = [" + screenWidth + "], "
                    + "screenHeight = [" + screenHeight + "]");
        }
        if (popupLayoutParams == null) {
            return;
        }

        popupLayoutParams.x = MathUtils.clamp(popupLayoutParams.x, 0, screenWidth
                - popupLayoutParams.width);
        popupLayoutParams.y = MathUtils.clamp(popupLayoutParams.y, 0, screenHeight
                - popupLayoutParams.height);
    }

    public void updateScreenSize() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            final var windowMetrics = windowManager.getCurrentWindowMetrics();
            final var bounds = windowMetrics.getBounds();
            final var windowInsets = windowMetrics.getWindowInsets();
            final var insets = windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.navigationBars() | WindowInsets.Type.displayCutout());
            screenWidth = bounds.width() - (insets.left + insets.right);
            screenHeight = bounds.height() - (insets.top + insets.bottom);
        } else {
            final DisplayMetrics metrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(metrics);
            screenWidth = metrics.widthPixels;
            screenHeight = metrics.heightPixels;
        }
        if (DEBUG) {
            Log.d(TAG, "updateScreenSize() called: screenWidth = ["
                    + screenWidth + "], screenHeight = [" + screenHeight + "]");
        }
    }

    /**
     * Changes the size of the popup based on the width.
     * @param width the new width, height is calculated with
     *              {@link PlayerHelper#getMinimumVideoHeight(float)}
     */
    public void changePopupSize(final int width) {
        if (DEBUG) {
            Log.d(TAG, "changePopupSize() called with: width = [" + width + "]");
        }

        if (anyPopupViewIsNull()) {
            return;
        }

        final float minimumWidth = context.getResources().getDimension(R.dimen.popup_minimum_width);
        final int actualWidth = MathUtils.clamp(width, (int) minimumWidth, screenWidth);
        final int actualHeight = (int) getMinimumVideoHeight(width);
        if (DEBUG) {
            Log.d(TAG, "updatePopupSize() updated values:"
                    + "  width = [" + actualWidth + "], height = [" + actualHeight + "]");
        }

        popupLayoutParams.width = actualWidth;
        popupLayoutParams.height = actualHeight;
        binding.surfaceView.setHeights(popupLayoutParams.height, popupLayoutParams.height);
        windowManager.updateViewLayout(binding.getRoot(), popupLayoutParams);
    }

    @Override
    protected float calculateMaxEndScreenThumbnailHeight(@NonNull final Bitmap bitmap) {
        // no need for the end screen thumbnail to be resized on popup player: it's only needed
        // for the main player so that it is enlarged correctly inside the fragment
        return bitmap.getHeight();
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Popup closing
    //////////////////////////////////////////////////////////////////////////*/
    //region Popup closing

    public void closePopup() {
        if (DEBUG) {
            Log.d(TAG, "closePopup() called, isPopupClosing = " + isPopupClosing);
        }
        if (isPopupClosing) {
            return;
        }
        isPopupClosing = true;

        player.saveStreamProgressState();
        windowManager.removeView(binding.getRoot());

        animatePopupOverlayAndFinishService();
    }

    public boolean isPopupClosing() {
        return isPopupClosing;
    }

    public void removePopupFromView() {
        // wrap in try-catch since it could sometimes generate errors randomly
        try {
            if (popupHasParent()) {
                windowManager.removeView(binding.getRoot());
            }
        } catch (final IllegalArgumentException e) {
            Log.w(TAG, "Failed to remove popup from window manager", e);
        }

        try {
            final boolean closeOverlayHasParent = closeOverlayBinding != null
                    && closeOverlayBinding.getRoot().getParent() != null;
            if (closeOverlayHasParent) {
                windowManager.removeView(closeOverlayBinding.getRoot());
            }
        } catch (final IllegalArgumentException e) {
            Log.w(TAG, "Failed to remove popup overlay from window manager", e);
        }
    }

    private void animatePopupOverlayAndFinishService() {
        final int targetTranslationY =
                (int) (closeOverlayBinding.closeButton.getRootView().getHeight()
                        - closeOverlayBinding.closeButton.getY());

        closeOverlayBinding.closeButton.animate().setListener(null).cancel();
        closeOverlayBinding.closeButton.animate()
                .setInterpolator(new AnticipateInterpolator())
                .translationY(targetTranslationY)
                .setDuration(400)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationCancel(final Animator animation) {
                        end();
                    }

                    @Override
                    public void onAnimationEnd(final Animator animation) {
                        end();
                    }

                    private void end() {
                        windowManager.removeView(closeOverlayBinding.getRoot());
                        closeOverlayBinding = null;
                        player.getService().stopService();
                    }
                }).start();
    }
    //endregion

    /*//////////////////////////////////////////////////////////////////////////
    // Playback states
    //////////////////////////////////////////////////////////////////////////*/
    //region Playback states

    private void changePopupWindowFlags(final int flags) {
        if (DEBUG) {
            Log.d(TAG, "changePopupWindowFlags() called with: flags = [" + flags + "]");
        }

        if (!anyPopupViewIsNull()) {
            popupLayoutParams.flags = flags;
            windowManager.updateViewLayout(binding.getRoot(), popupLayoutParams);
        }
    }

    @Override
    public void onPlaying() {
        super.onPlaying();
        changePopupWindowFlags(ONGOING_PLAYBACK_WINDOW_FLAGS);
    }

    @Override
    public void onPaused() {
        super.onPaused();
        changePopupWindowFlags(IDLE_WINDOW_FLAGS);
    }

    @Override
    public void onCompleted() {
        super.onCompleted();
        changePopupWindowFlags(IDLE_WINDOW_FLAGS);
    }

    @Override
    protected void setupSubtitleView(final float captionScale) {
        binding.subtitleView.setFractionalTextSize(
                SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * captionScale);
    }

    @Override
    protected void onPlaybackSpeedClicked() {
        playbackSpeedPopupMenu.show();
        isSomePopupMenuVisible = true;
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Gestures
    //////////////////////////////////////////////////////////////////////////*/
    //region Gestures

    private int distanceFromCloseButton(@NonNull final MotionEvent popupMotionEvent) {
        final int closeOverlayButtonX = closeOverlayBinding.closeButton.getLeft()
                + closeOverlayBinding.closeButton.getWidth() / 2;
        final int closeOverlayButtonY = closeOverlayBinding.closeButton.getTop()
                + closeOverlayBinding.closeButton.getHeight() / 2;

        final float fingerX = popupLayoutParams.x + popupMotionEvent.getX();
        final float fingerY = popupLayoutParams.y + popupMotionEvent.getY();

        return (int) Math.sqrt(Math.pow(closeOverlayButtonX - fingerX, 2)
                + Math.pow(closeOverlayButtonY - fingerY, 2));
    }

    private float getClosingRadius() {
        final int buttonRadius = closeOverlayBinding.closeButton.getWidth() / 2;
        // 20% wider than the button itself
        return buttonRadius * 1.2f;
    }

    public boolean isInsideClosingRadius(@NonNull final MotionEvent popupMotionEvent) {
        return distanceFromCloseButton(popupMotionEvent) <= getClosingRadius();
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Popup & closing overlay layout params + saving popup position and size
    //////////////////////////////////////////////////////////////////////////*/
    //region Popup & closing overlay layout params + saving popup position and size

    /**
     * {@code screenWidth} and {@code screenHeight} must have been initialized.
     * @return the popup starting layout params
     */
    @SuppressLint("RtlHardcoded")
    public WindowManager.LayoutParams retrievePopupLayoutParamsFromPrefs() {
        final SharedPreferences prefs = getPlayer().getPrefs();
        final Context context = getPlayer().getContext();

        final boolean popupRememberSizeAndPos = prefs.getBoolean(
                context.getString(R.string.popup_remember_size_pos_key), true);
        final float defaultSize = context.getResources().getDimension(R.dimen.popup_default_width);
        final float popupWidth = popupRememberSizeAndPos
                ? prefs.getFloat(context.getString(R.string.popup_saved_width_key), defaultSize)
                : defaultSize;
        final float popupHeight = getMinimumVideoHeight(popupWidth);

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                (int) popupWidth, (int) popupHeight,
                popupLayoutParamType(),
                IDLE_WINDOW_FLAGS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.LEFT | Gravity.TOP;
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

        final int centerX = (int) (screenWidth / 2f - popupWidth / 2f);
        final int centerY = (int) (screenHeight / 2f - popupHeight / 2f);
        params.x = popupRememberSizeAndPos
                ? prefs.getInt(context.getString(R.string.popup_saved_x_key), centerX) : centerX;
        params.y = popupRememberSizeAndPos
                ? prefs.getInt(context.getString(R.string.popup_saved_y_key), centerY) : centerY;

        return params;
    }

    public void savePopupPositionAndSizeToPrefs() {
        if (getPopupLayoutParams() != null) {
            final Context context = getPlayer().getContext();
            getPlayer().getPrefs().edit()
                    .putFloat(context.getString(R.string.popup_saved_width_key),
                            popupLayoutParams.width)
                    .putInt(context.getString(R.string.popup_saved_x_key),
                            popupLayoutParams.x)
                    .putInt(context.getString(R.string.popup_saved_y_key),
                            popupLayoutParams.y)
                    .apply();
        }
    }

    @SuppressLint("RtlHardcoded")
    public static WindowManager.LayoutParams buildCloseOverlayLayoutParams() {
        final int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;

        final WindowManager.LayoutParams closeOverlayLayoutParams = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                popupLayoutParamType(),
                flags,
                PixelFormat.TRANSLUCENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Setting maximum opacity allowed for touch events to other apps for Android 12 and
            // higher to prevent non interaction when using other apps with the popup player
            closeOverlayLayoutParams.alpha = MAXIMUM_OPACITY_ALLOWED_FOR_S_AND_HIGHER;
        }

        closeOverlayLayoutParams.gravity = Gravity.LEFT | Gravity.TOP;
        closeOverlayLayoutParams.softInputMode =
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        return closeOverlayLayoutParams;
    }

    public static int popupLayoutParamType() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_PHONE
                : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Getters
    //////////////////////////////////////////////////////////////////////////*/
    //region Getters

    private boolean popupHasParent() {
        return binding != null
                && binding.getRoot().getLayoutParams() instanceof WindowManager.LayoutParams
                && binding.getRoot().getParent() != null;
    }

    private boolean anyPopupViewIsNull() {
        return popupLayoutParams == null || windowManager == null
                || binding.getRoot().getParent() == null;
    }

    public PlayerPopupCloseOverlayBinding getCloseOverlayBinding() {
        return closeOverlayBinding;
    }

    public WindowManager.LayoutParams getPopupLayoutParams() {
        return popupLayoutParams;
    }

    public WindowManager getWindowManager() {
        return windowManager;
    }

    public int getScreenHeight() {
        return screenHeight;
    }

    public int getScreenWidth() {
        return screenWidth;
    }
    //endregion
}
