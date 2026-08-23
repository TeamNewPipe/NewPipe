package org.schabi.newpipe.player.event;

import static org.schabi.newpipe.ktx.AnimationType.ALPHA;
import static org.schabi.newpipe.ktx.AnimationType.SCALE_AND_ALPHA;
import static org.schabi.newpipe.ktx.ViewUtils.animate;
import static org.schabi.newpipe.player.Player.DEFAULT_CONTROLS_DURATION;
import static org.schabi.newpipe.player.Player.DEFAULT_CONTROLS_HIDE_TIME;
import static org.schabi.newpipe.player.Player.STATE_PLAYING;

import java.util.Locale;

import android.app.Activity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.player.PlayerService;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.mediasession.PlayerServiceInterface;

/**
 * GestureListener for the player
 *
 * While {@link BasePlayerGestureListener} contains the logic behind the single gestures
 * this class focuses on the visual aspect like hiding and showing the controls or changing
 * volume/brightness during scrolling for specific events.
 */
public class PlayerGestureListener
        extends BasePlayerGestureListener
        implements View.OnTouchListener {
    private static final String TAG = PlayerGestureListener.class.getSimpleName();
    private static final boolean DEBUG = MainActivity.DEBUG;

    private final int maxVolume;

    private boolean isSwipeSeeking = false;
    private float accumulatedSeek = 0f;
    private static final float SEEK_SWIPE_FACTOR = 100f; // ms per pixel
    private static final float SEEK_SWIPE_FAST_MULTIPLIER = 10f;
    private static final long SEEK_SWIPE_FAST_THRESHOLD_MS = 60_000L;
    private long swipeSeekStartPosition = 0L;
    private long swipeSeekTargetPosition = 0L;
    private boolean isChangingVolume = false;
    private boolean isChangingBrightness = false;
    private boolean isChangingSpeed = false;
    private float speedGestureStartSpeed = 1.0f;
    private float accumulatedSpeedScroll = 0f;
    private static final float SPEED_SWIPE_PIXELS_PER_STEP = 20f;
    private static final float SPEED_MIN = 0.1f;
    private static final float SPEED_MAX = 4.0f;

    private boolean isPendingScreenRotation = false;
    private boolean isFullscreenRotationGesture = false;

    public PlayerGestureListener(final Player player, final PlayerServiceInterface service) {
        super(player, service.getInstance());
        maxVolume = player.getAudioReactor().getMaxVolume();
    }

    @Override
    public void onDoubleTap(@NonNull final MotionEvent event,
                            @NonNull final DisplayPortion portion) {
        if (DEBUG) {
            Log.d(TAG, "onDoubleTap called with playerType = ["
                    + player.getPlayerType() + "], portion = [" + portion + "]");
        }
        if (player.isSomePopupMenuVisible()) {
            player.hideControls(0, 0);
        }

        if (portion == DisplayPortion.LEFT || portion == DisplayPortion.RIGHT) {
            startMultiDoubleTap(event);
        } else if (portion == DisplayPortion.MIDDLE) {
            player.playPause();
        }
    }

    @Override
    public void onSingleTap(@NonNull final PlayerService.PlayerType playerType) {
        if (DEBUG) {
            Log.d(TAG, "onSingleTap called with playerType = [" + player.getPlayerType() + "]");
        }

        if (player.isControlsVisible()) {
            player.hideControls(150, 0);
            return;
        }
        // -- Controls are not visible --

        // When player is completed show controls and don't hide them later
        if (player.getCurrentState() == Player.STATE_COMPLETED) {
            player.showControls(0);
        } else {
            player.showControlsThenHide();
        }
    }

    @Override
    public void onScroll(@NonNull final PlayerService.PlayerType playerType,
                         @NonNull final DisplayPortion portion,
                         @NonNull final MotionEvent initialEvent,
                         @NonNull final MotionEvent movingEvent,
                         final float distanceX, final float distanceY) {
        if (DEBUG) {
            Log.d(TAG, "onScroll called with playerType = ["
                    + player.getPlayerType() + "], portion = [" + portion + "]");
        }
        if (playerType == PlayerService.PlayerType.VIDEO) {
            final boolean isFullscreenGestureEnabled =
                    PlayerHelper.isFullscreenGestureEnabled(service);
            final boolean isSwipeSeekGestureEnabled =
                    PlayerHelper.isSwipeSeekGestureEnabled(service);

            if (isSwipeSeekGestureEnabled && isSwipeSeeking) {
                onScrollMainSeek(distanceX);
                return;
            }

            if (isChangingVolume) {
                onScrollMainVolume(distanceX, distanceY);
                return;
            }

            if (isChangingBrightness) {
                onScrollMainBrightness(distanceX, distanceY);
                return;
            }

            if (isChangingSpeed) {
                onScrollMainSpeed(distanceY);
                return;
            }

            final boolean isHorizontal = Math.abs(distanceX) > Math.abs(distanceY);
            if (!isHorizontal && isFullscreenGestureEnabled &&
                    ((player.isFullscreen() && distanceY < 0 && portion == DisplayPortion.MIDDLE) ||
                            (!player.isFullscreen() && distanceY > 0))) {
                isPendingScreenRotation = true;
                isFullscreenRotationGesture = true;
                return;
            }

            if(!player.isFullscreen()) {
                return;
            }

            final boolean isPlaybackSpeedGestureEnabled =
                    PlayerHelper.isPlaybackSpeedGestureEnabled(service);
            if (!isHorizontal && isPlaybackSpeedGestureEnabled && player.isFullscreen()
                    && portion == DisplayPortion.MIDDLE) {
                onScrollMainSpeed(distanceY);
                return;
            }

            if (isSwipeSeekGestureEnabled && isHorizontal) {
                onScrollMainSeek(distanceX);
                return;
            }
            // -- Brightness and Volume control --
            final boolean isBrightnessGestureEnabled =
                    PlayerHelper.isBrightnessGestureEnabled(service);
            final boolean isVolumeGestureEnabled = PlayerHelper.isVolumeGestureEnabled(service);

            if (isBrightnessGestureEnabled && isVolumeGestureEnabled) {
                if (portion == DisplayPortion.LEFT) {
                    onScrollMainBrightness(distanceX, distanceY);

                } else if (portion == DisplayPortion.RIGHT) {
                    onScrollMainVolume(distanceX, distanceY);
                }
            } else if (isBrightnessGestureEnabled) {
                onScrollMainBrightness(distanceX, distanceY);
            } else if (isVolumeGestureEnabled) {
                onScrollMainVolume(distanceX, distanceY);
            }

        } else /* MainPlayer.PlayerType.POPUP */ {

            // -- Determine if the ClosingOverlayView (red X) has to be shown or hidden --
            final View closingOverlayView = player.getClosingOverlayView();
            final boolean showClosingOverlayView = player.isInsideClosingRadius(movingEvent);
            // Check if an view is in expected state and if not animate it into the correct state
            final int expectedVisibility = showClosingOverlayView ? View.VISIBLE : View.GONE;
            if (closingOverlayView.getVisibility() != expectedVisibility) {
                animate(closingOverlayView, showClosingOverlayView, 200);
            }
        }
    }

    private void onScrollMainVolume(final float distanceX, final float distanceY) {
        if (!isChangingVolume) {
            isChangingVolume = true;
        }
        // If we just started sliding, change the progress bar to match the system volume
        if (player.getVolumeRelativeLayout().getVisibility() != View.VISIBLE) {
            final float volumePercent = player
                    .getAudioReactor().getVolume() / (float) maxVolume;
            player.getVolumeProgressBar().setProgress(
                    (int) (volumePercent * player.getMaxGestureLength()));
        }

        player.getVolumeProgressBar().incrementProgressBy((int) distanceY);
        final float currentProgressPercent = (float) player
                .getVolumeProgressBar().getProgress() / player.getMaxGestureLength();
        final int currentVolume = (int) (maxVolume * currentProgressPercent);
        player.getAudioReactor().setVolume(currentVolume);

        if (DEBUG) {
            Log.d(TAG, "onScroll().volumeControl, currentVolume = " + currentVolume);
        }

        player.getVolumeImageView().setImageDrawable(
                AppCompatResources.getDrawable(service, currentProgressPercent <= 0
                        ? R.drawable.ic_volume_off
                        : currentProgressPercent < 0.25 ? R.drawable.ic_volume_mute
                        : currentProgressPercent < 0.75 ? R.drawable.ic_volume_down
                        : R.drawable.ic_volume_up)
        );

        if (player.getVolumeRelativeLayout().getVisibility() != View.VISIBLE) {
            animate(player.getVolumeRelativeLayout(), true, 200, SCALE_AND_ALPHA);
        }
        if (player.getBrightnessRelativeLayout().getVisibility() == View.VISIBLE) {
            player.getBrightnessRelativeLayout().setVisibility(View.GONE);
        }
    }

    private void onScrollMainBrightness(final float distanceX, final float distanceY) {
        final Activity parent = player.getParentActivity();
        if (parent == null) {
            return;
        }

        if (!isChangingBrightness) {
            isChangingBrightness = true;
        }

        final Window window = parent.getWindow();
        final WindowManager.LayoutParams layoutParams = window.getAttributes();
        final ProgressBar bar = player.getBrightnessProgressBar();
        final float oldBrightness = layoutParams.screenBrightness;
        bar.setProgress((int) (bar.getMax() * Math.max(0, Math.min(1, oldBrightness))));
        bar.incrementProgressBy((int) distanceY);

        final float currentProgressPercent = (float) bar.getProgress() / bar.getMax();
        layoutParams.screenBrightness = currentProgressPercent;
        window.setAttributes(layoutParams);

        // Save current brightness level
        PlayerHelper.setScreenBrightness(parent, currentProgressPercent);

        if (DEBUG) {
            Log.d(TAG, "onScroll().brightnessControl, "
                    + "currentBrightness = " + currentProgressPercent);
        }

        player.getBrightnessImageView().setImageDrawable(
                AppCompatResources.getDrawable(service,
                        currentProgressPercent < 0.25
                                ? R.drawable.ic_brightness_low
                                : currentProgressPercent < 0.75
                                ? R.drawable.ic_brightness_medium
                                : R.drawable.ic_brightness_high)
        );

        if (player.getBrightnessRelativeLayout().getVisibility() != View.VISIBLE) {
            animate(player.getBrightnessRelativeLayout(), true, 200, SCALE_AND_ALPHA);
        }
        if (player.getVolumeRelativeLayout().getVisibility() == View.VISIBLE) {
            player.getVolumeRelativeLayout().setVisibility(View.GONE);
        }
    }

    private void onScrollMainSpeed(final float distanceY) {
        if (!isChangingSpeed) {
            isChangingSpeed = true;
            accumulatedSpeedScroll = 0f;
            speedGestureStartSpeed = player.getPlaybackSpeed();
            animate(player.getSwipeSpeedDisplay(), true, DEFAULT_CONTROLS_DURATION, SCALE_AND_ALPHA);
            if (player.getVolumeRelativeLayout().getVisibility() == View.VISIBLE) {
                animate(player.getVolumeRelativeLayout(), false, 200, SCALE_AND_ALPHA);
                isChangingVolume = false;
            }
            if (player.getBrightnessRelativeLayout().getVisibility() == View.VISIBLE) {
                animate(player.getBrightnessRelativeLayout(), false, 200, SCALE_AND_ALPHA);
                isChangingBrightness = false;
            }
        }

        accumulatedSpeedScroll += distanceY; // positive = swipe up = faster
        final float rawSpeed = speedGestureStartSpeed
                + (accumulatedSpeedScroll / SPEED_SWIPE_PIXELS_PER_STEP) * 0.1f;
        final float speed = Math.max(SPEED_MIN, Math.min(SPEED_MAX,
                Math.round(rawSpeed * 10) / 10.0f));
        player.setPlaybackSpeed(speed);
        player.getSwipeSpeedDisplay().setText(String.format(Locale.getDefault(), "%.1fx", speed));
    }

    private void onScrollMainSeek(final float distanceX) {
        // The first swipe determines the active overlay; once seeking is engaged
        // we hide volume and brightness controls so mixed movements do not trigger them.
        if (!isSwipeSeeking) {
            isSwipeSeeking = true;
            accumulatedSeek = 0f;
            swipeSeekStartPosition = player.getCurrentPosition();
            swipeSeekTargetPosition = swipeSeekStartPosition;
            animate(player.getSwipeSeekDisplay(), true, DEFAULT_CONTROLS_DURATION, SCALE_AND_ALPHA);
            if (player.getVolumeRelativeLayout().getVisibility() == View.VISIBLE) {
                animate(player.getVolumeRelativeLayout(), false, 200, SCALE_AND_ALPHA);
                isChangingVolume = false;
            }
            if (player.getBrightnessRelativeLayout().getVisibility() == View.VISIBLE) {
                animate(player.getBrightnessRelativeLayout(), false, 200, SCALE_AND_ALPHA);
                isChangingBrightness = false;
            }
        }

        accumulatedSeek -= distanceX;
        float thresholdPx = SEEK_SWIPE_FAST_THRESHOLD_MS / SEEK_SWIPE_FACTOR;
        long deltaMs;
        if (Math.abs(accumulatedSeek) <= thresholdPx) {
            deltaMs = (long) (accumulatedSeek * SEEK_SWIPE_FACTOR);
        } else {
            // Large scrubs should travel faster so long videos remain easy to navigate
            float beyond = Math.abs(accumulatedSeek) - thresholdPx;
            deltaMs = (long) (Math.signum(accumulatedSeek) *
                    (SEEK_SWIPE_FAST_THRESHOLD_MS + beyond * SEEK_SWIPE_FACTOR * SEEK_SWIPE_FAST_MULTIPLIER));
        }

        swipeSeekTargetPosition = swipeSeekStartPosition + deltaMs;
        long duration = player.getDuration();
        if (duration > 0 && swipeSeekTargetPosition > duration) swipeSeekTargetPosition = duration;
        if (swipeSeekTargetPosition < 0) swipeSeekTargetPosition = 0;
        long delta = swipeSeekTargetPosition - swipeSeekStartPosition;
        String deltaStr = (delta >= 0 ? "+" : "-")
                + PlayerHelper.getTimeString((int) Math.abs(delta));
        String posStr = PlayerHelper.getTimeString((int) swipeSeekTargetPosition);
        player.getSwipeSeekDisplay().setText(deltaStr + " (" + posStr + ")");
    }

    @Override
    public void onScrollEnd(@NonNull final PlayerService.PlayerType playerType,
                            @NonNull final MotionEvent event) {
        if (DEBUG) {
            Log.d(TAG, "onScrollEnd called with playerType = ["
                    + player.getPlayerType() + "]");
        }

        if (player.isControlsVisible() && player.getCurrentState() == STATE_PLAYING) {
            player.hideControls(DEFAULT_CONTROLS_DURATION, DEFAULT_CONTROLS_HIDE_TIME);
        }

        if (playerType == PlayerService.PlayerType.VIDEO) {
            // Handle pending screen rotation gesture
            if (isPendingScreenRotation && isFullscreenRotationGesture) {
                player.onScreenRotationButtonClicked();
                isPendingScreenRotation = false;
                isFullscreenRotationGesture = false;
                return; // Exit early to avoid other cleanup actions
            }

            if (isSwipeSeeking) {
                // apply the buffered target only when the gesture ends to keep playback smooth
                player.seekTo(swipeSeekTargetPosition);
                animate(player.getSwipeSeekDisplay(), false, 200, SCALE_AND_ALPHA);
                isSwipeSeeking = false;
            }
            if (isChangingSpeed) {
                animate(player.getSwipeSpeedDisplay(), false, 200, SCALE_AND_ALPHA, 200);
                isChangingSpeed = false;
            }
            if (player.getVolumeRelativeLayout().getVisibility() == View.VISIBLE) {
                animate(player.getVolumeRelativeLayout(), false, 200, SCALE_AND_ALPHA,
                        200);
                isChangingVolume = false;
            }
            if (player.getBrightnessRelativeLayout().getVisibility() == View.VISIBLE) {
                animate(player.getBrightnessRelativeLayout(), false, 200, SCALE_AND_ALPHA,
                        200);
                isChangingBrightness = false;
            }
        } else /* Popup-Player */ {
            if (player.isInsideClosingRadius(event)) {
                player.closePopup();
            } else if (!player.isPopupClosing()) {
                animate(player.getCloseOverlayButton(), false, 200);
                animate(player.getClosingOverlayView(), false, 200);
            }
        }
    }

    @Override
    public void onPopupResizingStart() {
        if (DEBUG) {
            Log.d(TAG, "onPopupResizingStart called");
        }
        player.getLoadingPanel().setVisibility(View.GONE);

        player.hideControls(0, 0);
        animate(player.getFastSeekOverlay(), false, 0);
        animate(player.getSwipeSeekDisplay(), false, 0, ALPHA, 0);
        animate(player.getSwipeSpeedDisplay(), false, 0, ALPHA, 0);
        animate(player.getVolumeRelativeLayout(), false, 0, ALPHA, 0);
        animate(player.getBrightnessRelativeLayout(), false, 0, ALPHA, 0);
        isChangingVolume = false;
        isChangingBrightness = false;
        isChangingSpeed = false;
    }

    @Override
    public void onPopupResizingEnd() {
        if (DEBUG) {
            Log.d(TAG, "onPopupResizingEnd called");
        }
    }
}
