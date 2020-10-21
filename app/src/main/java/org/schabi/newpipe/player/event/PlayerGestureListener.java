package org.schabi.newpipe.player.event;

import android.app.Activity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;

import androidx.appcompat.content.res.AppCompatResources;

import org.jetbrains.annotations.NotNull;
import org.schabi.newpipe.R;
import org.schabi.newpipe.player.BasePlayer;
import org.schabi.newpipe.player.MainPlayer;
import org.schabi.newpipe.player.VideoPlayerImpl;
import org.schabi.newpipe.player.helper.PlayerHelper;

import static org.schabi.newpipe.player.BasePlayer.STATE_PLAYING;
import static org.schabi.newpipe.player.VideoPlayer.DEFAULT_CONTROLS_DURATION;
import static org.schabi.newpipe.player.VideoPlayer.DEFAULT_CONTROLS_HIDE_TIME;
import static org.schabi.newpipe.util.AnimationUtils.Type.SCALE_AND_ALPHA;
import static org.schabi.newpipe.util.AnimationUtils.animateView;

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
    private static final String TAG = ".PlayerGestureListener";
    private static final boolean DEBUG = BasePlayer.DEBUG;

    private final boolean isVolumeGestureEnabled;
    private final boolean isBrightnessGestureEnabled;
    private final int maxVolume;

    public PlayerGestureListener(final VideoPlayerImpl playerImpl, final MainPlayer service) {
        super(playerImpl, service);

        isVolumeGestureEnabled = PlayerHelper.isVolumeGestureEnabled(service);
        isBrightnessGestureEnabled = PlayerHelper.isBrightnessGestureEnabled(service);
        maxVolume = playerImpl.getAudioReactor().getMaxVolume();
    }

    @Override
    public void onDoubleTap(@NotNull final MotionEvent event,
                            @NotNull final DisplayPortion portion) {
        if (DEBUG) {
            Log.d(TAG, "onDoubleTap called with playerType = ["
                    + playerImpl.getPlayerType() + "], portion = ["
                    + portion + "]");
        }
        if (playerImpl.isSomePopupMenuVisible()) {
            playerImpl.hideControls(0, 0);
        }

        if (portion == DisplayPortion.LEFT) {
            playerImpl.onFastRewind();
        } else if (portion == DisplayPortion.RIGHT) {
            playerImpl.onFastForward();
        }
    }

    @Override
    public void onSingleTap(@NotNull final MainPlayer.PlayerType playerType) {
        if (DEBUG) {
            Log.d(TAG, "onSingleTap called with playerType = ["
                + playerImpl.getPlayerType() + "]");
        }
        if (playerType == MainPlayer.PlayerType.POPUP) {

            if (playerImpl.isControlsVisible()) {
                playerImpl.hideControls(100, 100);
            } else {
                playerImpl.getPlayPauseButton().requestFocus();
                playerImpl.showControlsThenHide();
            }

        } else /* playerType == MainPlayer.PlayerType.VIDEO */ {

            if (playerImpl.isControlsVisible()) {
                playerImpl.hideControls(150, 0);
            } else {
                if (playerImpl.getCurrentState() == BasePlayer.STATE_COMPLETED) {
                    playerImpl.showControls(0);
                } else {
                    playerImpl.showControlsThenHide();
                }
            }
        }
    }

    @Override
    public void onScroll(@NotNull final MainPlayer.PlayerType playerType,
                         @NotNull final DisplayPortion portion,
                         @NotNull final MotionEvent initialEvent,
                         @NotNull final MotionEvent movingEvent,
                         final float distanceX, final float distanceY) {
        if (DEBUG) {
            Log.d(TAG, "onScroll called with playerType = ["
                + playerImpl.getPlayerType() + "], portion = ["
                + portion + "]");
        }
        if (playerType == MainPlayer.PlayerType.VIDEO) {
            if (portion == DisplayPortion.LEFT_HALF) {
                onScrollMainVolume(distanceX, distanceY);

            } else /* DisplayPortion.RIGHT_HALF */ {
                onScrollMainBrightness(distanceX, distanceY);
            }

        } else /* MainPlayer.PlayerType.POPUP */ {
            final View closingOverlayView = playerImpl.getClosingOverlayView();
            if (playerImpl.isInsideClosingRadius(movingEvent)) {
                if (closingOverlayView.getVisibility() == View.GONE) {
                    animateView(closingOverlayView, true, 250);
                }
            } else {
                if (closingOverlayView.getVisibility() == View.VISIBLE) {
                    animateView(closingOverlayView, false, 0);
                }
            }
        }
    }

    private void onScrollMainVolume(final float distanceX, final float distanceY) {
        if (isVolumeGestureEnabled) {
            playerImpl.getVolumeProgressBar().incrementProgressBy((int) distanceY);
            final float currentProgressPercent = (float) playerImpl
                    .getVolumeProgressBar().getProgress() / playerImpl.getMaxGestureLength();
            final int currentVolume = (int) (maxVolume * currentProgressPercent);
            playerImpl.getAudioReactor().setVolume(currentVolume);

            if (DEBUG) {
                Log.d(TAG, "onScroll().volumeControl, currentVolume = " + currentVolume);
            }

            playerImpl.getVolumeImageView().setImageDrawable(
                    AppCompatResources.getDrawable(service, currentProgressPercent <= 0
                            ? R.drawable.ic_volume_off_white_24dp
                            : currentProgressPercent < 0.25 ? R.drawable.ic_volume_mute_white_24dp
                            : currentProgressPercent < 0.75 ? R.drawable.ic_volume_down_white_24dp
                            : R.drawable.ic_volume_up_white_24dp)
            );

            if (playerImpl.getVolumeRelativeLayout().getVisibility() != View.VISIBLE) {
                animateView(playerImpl.getVolumeRelativeLayout(), SCALE_AND_ALPHA, true, 200);
            }
            if (playerImpl.getBrightnessRelativeLayout().getVisibility() == View.VISIBLE) {
                playerImpl.getBrightnessRelativeLayout().setVisibility(View.GONE);
            }
        }
    }

    private void onScrollMainBrightness(final float distanceX, final float distanceY) {
        if (isBrightnessGestureEnabled) {
            final Activity parent = playerImpl.getParentActivity();
            if (parent == null) {
                return;
            }

            final Window window = parent.getWindow();
            final WindowManager.LayoutParams layoutParams = window.getAttributes();
            final ProgressBar bar = playerImpl.getBrightnessProgressBar();
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

            playerImpl.getBrightnessImageView().setImageDrawable(
                    AppCompatResources.getDrawable(service,
                            currentProgressPercent < 0.25
                                    ? R.drawable.ic_brightness_low_white_24dp
                                    : currentProgressPercent < 0.75
                                    ? R.drawable.ic_brightness_medium_white_24dp
                                    : R.drawable.ic_brightness_high_white_24dp)
            );

            if (playerImpl.getBrightnessRelativeLayout().getVisibility() != View.VISIBLE) {
                animateView(playerImpl.getBrightnessRelativeLayout(), SCALE_AND_ALPHA, true, 200);
            }
            if (playerImpl.getVolumeRelativeLayout().getVisibility() == View.VISIBLE) {
                playerImpl.getVolumeRelativeLayout().setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onScrollEnd(@NotNull final MainPlayer.PlayerType playerType,
                            @NotNull final MotionEvent event) {
        if (DEBUG) {
            Log.d(TAG, "onScrollEnd called with playerType = ["
                + playerImpl.getPlayerType() + "]");
        }
        if (playerType == MainPlayer.PlayerType.VIDEO) {
            if (DEBUG) {
                Log.d(TAG, "onScrollEnd() called");
            }

            if (playerImpl.getVolumeRelativeLayout().getVisibility() == View.VISIBLE) {
                animateView(playerImpl.getVolumeRelativeLayout(), SCALE_AND_ALPHA,
                        false, 200, 200);
            }
            if (playerImpl.getBrightnessRelativeLayout().getVisibility() == View.VISIBLE) {
                animateView(playerImpl.getBrightnessRelativeLayout(), SCALE_AND_ALPHA,
                        false, 200, 200);
            }

            if (playerImpl.isControlsVisible() && playerImpl.getCurrentState() == STATE_PLAYING) {
                playerImpl.hideControls(DEFAULT_CONTROLS_DURATION, DEFAULT_CONTROLS_HIDE_TIME);
            }
        } else {
            if (playerImpl == null) {
                return;
            }
            if (playerImpl.isControlsVisible() && playerImpl.getCurrentState() == STATE_PLAYING) {
                playerImpl.hideControls(DEFAULT_CONTROLS_DURATION, DEFAULT_CONTROLS_HIDE_TIME);
            }

            if (playerImpl.isInsideClosingRadius(event)) {
                playerImpl.closePopup();
            } else {
                animateView(playerImpl.getClosingOverlayView(), false, 0);

                if (!playerImpl.isPopupClosing) {
                    animateView(playerImpl.getCloseOverlayButton(), false, 200);
                }
            }
        }
    }

    @Override
    public void onPopupResizingStart() {
        if (DEBUG) {
            Log.d(TAG, "onPopupResizingStart called");
        }
        playerImpl.showAndAnimateControl(-1, true);
        playerImpl.getLoadingPanel().setVisibility(View.GONE);

        playerImpl.hideControls(0, 0);
        animateView(playerImpl.getCurrentDisplaySeek(), false, 0, 0);
        animateView(playerImpl.getResizingIndicator(), true, 200, 0);
    }

    @Override
    public void onPopupResizingEnd() {
        if (DEBUG) {
            Log.d(TAG, "onPopupResizingEnd called");
        }
        animateView(playerImpl.getResizingIndicator(), false, 100, 0);
    }
}


