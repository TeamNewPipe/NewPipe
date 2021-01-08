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
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.player.MainPlayer;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.helper.PlayerHelper;

import static org.schabi.newpipe.player.Player.STATE_PLAYING;
import static org.schabi.newpipe.player.Player.DEFAULT_CONTROLS_DURATION;
import static org.schabi.newpipe.player.Player.DEFAULT_CONTROLS_HIDE_TIME;
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
    private static final String TAG = PlayerGestureListener.class.getSimpleName();
    private static final boolean DEBUG = MainActivity.DEBUG;

    private final int maxVolume;

    public PlayerGestureListener(final Player player, final MainPlayer service) {
        super(player, service);
        maxVolume = player.getAudioReactor().getMaxVolume();
    }

    @Override
    public void onDoubleTap(@NotNull final MotionEvent event,
                            @NotNull final DisplayPortion portion) {
        if (DEBUG) {
            Log.d(TAG, "onDoubleTap called with playerType = ["
                    + player.getPlayerType() + "], portion = [" + portion + "]");
        }
        if (player.isSomePopupMenuVisible()) {
            player.hideControls(0, 0);
        }

        if (portion == DisplayPortion.LEFT) {
            player.fastRewind();
        } else if (portion == DisplayPortion.MIDDLE) {
            player.playPause();
        } else if (portion == DisplayPortion.RIGHT) {
            player.fastForward();
        }
    }

    @Override
    public void onSingleTap(@NotNull final MainPlayer.PlayerType playerType) {
        if (DEBUG) {
            Log.d(TAG, "onSingleTap called with playerType = [" + player.getPlayerType() + "]");
        }
        if (playerType == MainPlayer.PlayerType.POPUP) {

            if (player.isControlsVisible()) {
                player.hideControls(100, 100);
            } else {
                player.getPlayPauseButton().requestFocus();
                player.showControlsThenHide();
            }

        } else /* playerType == MainPlayer.PlayerType.VIDEO */ {

            if (player.isControlsVisible()) {
                player.hideControls(150, 0);
            } else {
                if (player.getCurrentState() == Player.STATE_COMPLETED) {
                    player.showControls(0);
                } else {
                    player.showControlsThenHide();
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
                + player.getPlayerType() + "], portion = [" + portion + "]");
        }
        if (playerType == MainPlayer.PlayerType.VIDEO) {
            final boolean isBrightnessGestureEnabled =
                PlayerHelper.isBrightnessGestureEnabled(service);
            final boolean isVolumeGestureEnabled = PlayerHelper.isVolumeGestureEnabled(service);

            if (isBrightnessGestureEnabled && isVolumeGestureEnabled) {
                if (portion == DisplayPortion.LEFT_HALF) {
                    onScrollMainBrightness(distanceX, distanceY);

                } else /* DisplayPortion.RIGHT_HALF */ {
                    onScrollMainVolume(distanceX, distanceY);
                }
            } else if (isBrightnessGestureEnabled) {
                onScrollMainBrightness(distanceX, distanceY);
            } else if (isVolumeGestureEnabled) {
                onScrollMainVolume(distanceX, distanceY);
            }

        } else /* MainPlayer.PlayerType.POPUP */ {
            final View closingOverlayView = player.getClosingOverlayView();
            if (player.isInsideClosingRadius(movingEvent)) {
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
                        ? R.drawable.ic_volume_off_white_24dp
                        : currentProgressPercent < 0.25 ? R.drawable.ic_volume_mute_white_24dp
                        : currentProgressPercent < 0.75 ? R.drawable.ic_volume_down_white_24dp
                        : R.drawable.ic_volume_up_white_24dp)
        );

        if (player.getVolumeRelativeLayout().getVisibility() != View.VISIBLE) {
            animateView(player.getVolumeRelativeLayout(), SCALE_AND_ALPHA, true, 200);
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
                                ? R.drawable.ic_brightness_low_white_24dp
                                : currentProgressPercent < 0.75
                                ? R.drawable.ic_brightness_medium_white_24dp
                                : R.drawable.ic_brightness_high_white_24dp)
        );

        if (player.getBrightnessRelativeLayout().getVisibility() != View.VISIBLE) {
            animateView(player.getBrightnessRelativeLayout(), SCALE_AND_ALPHA, true, 200);
        }
        if (player.getVolumeRelativeLayout().getVisibility() == View.VISIBLE) {
            player.getVolumeRelativeLayout().setVisibility(View.GONE);
        }
    }

    @Override
    public void onScrollEnd(@NotNull final MainPlayer.PlayerType playerType,
                            @NotNull final MotionEvent event) {
        if (DEBUG) {
            Log.d(TAG, "onScrollEnd called with playerType = ["
                + player.getPlayerType() + "]");
        }
        if (playerType == MainPlayer.PlayerType.VIDEO) {
            if (DEBUG) {
                Log.d(TAG, "onScrollEnd() called");
            }

            if (player.getVolumeRelativeLayout().getVisibility() == View.VISIBLE) {
                animateView(player.getVolumeRelativeLayout(), SCALE_AND_ALPHA,
                        false, 200, 200);
            }
            if (player.getBrightnessRelativeLayout().getVisibility() == View.VISIBLE) {
                animateView(player.getBrightnessRelativeLayout(), SCALE_AND_ALPHA,
                        false, 200, 200);
            }

            if (player.isControlsVisible() && player.getCurrentState() == STATE_PLAYING) {
                player.hideControls(DEFAULT_CONTROLS_DURATION, DEFAULT_CONTROLS_HIDE_TIME);
            }
        } else {
            if (player == null) {
                return;
            }
            if (player.isControlsVisible() && player.getCurrentState() == STATE_PLAYING) {
                player.hideControls(DEFAULT_CONTROLS_DURATION, DEFAULT_CONTROLS_HIDE_TIME);
            }

            if (player.isInsideClosingRadius(event)) {
                player.closePopup();
            } else {
                animateView(player.getClosingOverlayView(), false, 0);

                if (!player.isPopupClosing()) {
                    animateView(player.getCloseOverlayButton(), false, 200);
                }
            }
        }
    }

    @Override
    public void onPopupResizingStart() {
        if (DEBUG) {
            Log.d(TAG, "onPopupResizingStart called");
        }
        player.showAndAnimateControl(-1, true);
        player.getLoadingPanel().setVisibility(View.GONE);

        player.hideControls(0, 0);
        animateView(player.getCurrentDisplaySeek(), false, 0, 0);
        animateView(player.getResizingIndicator(), true, 200, 0);
    }

    @Override
    public void onPopupResizingEnd() {
        if (DEBUG) {
            Log.d(TAG, "onPopupResizingEnd called");
        }
        animateView(player.getResizingIndicator(), false, 100, 0);
    }
}


