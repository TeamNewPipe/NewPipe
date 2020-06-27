package org.schabi.newpipe.player.event;

import android.app.Activity;
import android.util.Log;
import android.view.*;
import androidx.appcompat.content.res.AppCompatResources;
import org.schabi.newpipe.R;
import org.schabi.newpipe.player.BasePlayer;
import org.schabi.newpipe.player.MainPlayer;
import org.schabi.newpipe.player.VideoPlayerImpl;
import org.schabi.newpipe.player.helper.PlayerHelper;

import static org.schabi.newpipe.player.BasePlayer.*;
import static org.schabi.newpipe.player.VideoPlayer.DEFAULT_CONTROLS_DURATION;
import static org.schabi.newpipe.player.VideoPlayer.DEFAULT_CONTROLS_HIDE_TIME;
import static org.schabi.newpipe.util.AnimationUtils.Type.SCALE_AND_ALPHA;
import static org.schabi.newpipe.util.AnimationUtils.animateView;

public class PlayerGestureListener extends GestureDetector.SimpleOnGestureListener implements View.OnTouchListener {
    private static final String TAG = ".PlayerGestureListener";
    private static final boolean DEBUG = BasePlayer.DEBUG;

    private VideoPlayerImpl playerImpl;
    private MainPlayer service;

    private int initialPopupX, initialPopupY;

    private boolean isMovingInMain, isMovingInPopup;

    private boolean isResizing;

    private int tossFlingVelocity;

    private final boolean isVolumeGestureEnabled;
    private final boolean isBrightnessGestureEnabled;
    private final int maxVolume;
    private static final int MOVEMENT_THRESHOLD = 40;


    public PlayerGestureListener(final VideoPlayerImpl playerImpl, final MainPlayer service) {
        this.playerImpl = playerImpl;
        this.service = service;
        this.tossFlingVelocity = PlayerHelper.getTossFlingVelocity(service);

        isVolumeGestureEnabled = PlayerHelper.isVolumeGestureEnabled(service);
        isBrightnessGestureEnabled = PlayerHelper.isBrightnessGestureEnabled(service);
        maxVolume = playerImpl.getAudioReactor().getMaxVolume();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Helpers
    //////////////////////////////////////////////////////////////////////////*/

    /*
    * Main and popup players' gesture listeners is too different.
    * So it will be better to have different implementations of them
    * */
    @Override
    public boolean onDoubleTap(MotionEvent e) {
        if (DEBUG) Log.d(TAG, "onDoubleTap() called with: e = [" + e + "]" + "rawXy = " + e.getRawX() + ", " + e.getRawY() + ", xy = " + e.getX() + ", " + e.getY());

        if (playerImpl.popupPlayerSelected()) return onDoubleTapInPopup(e);
        else return onDoubleTapInMain(e);
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        if (DEBUG) Log.d(TAG, "onSingleTapConfirmed() called with: e = [" + e + "]");

        if (playerImpl.popupPlayerSelected()) return onSingleTapConfirmedInPopup(e);
        else return onSingleTapConfirmedInMain(e);
    }

    @Override
    public boolean onDown(MotionEvent e) {
        if (DEBUG) Log.d(TAG, "onDown() called with: e = [" + e + "]");

        if (playerImpl.popupPlayerSelected()) return onDownInPopup(e);
        else return true;
    }
    @Override
    public void onLongPress(MotionEvent e) {
        if (DEBUG) Log.d(TAG, "onLongPress() called with: e = [" + e + "]");

        if (playerImpl.popupPlayerSelected()) onLongPressInPopup(e);
    }

    @Override
    public boolean onScroll(MotionEvent initialEvent, MotionEvent movingEvent, float distanceX, float distanceY) {
        if (playerImpl.popupPlayerSelected()) return onScrollInPopup(initialEvent, movingEvent, distanceX, distanceY);
        else return onScrollInMain(initialEvent, movingEvent, distanceX, distanceY);
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (DEBUG) Log.d(TAG, "onFling() called with velocity: dX=[" + velocityX + "], dY=[" + velocityY + "]");

        if (playerImpl.popupPlayerSelected()) return onFlingInPopup(e1, e2, velocityX, velocityY);
        else return true;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        //noinspection PointlessBooleanExpression,ConstantConditions
        if (DEBUG && false) Log.d(TAG, "onTouch() called with: v = [" + v + "], event = [" + event + "]");

        if (playerImpl.popupPlayerSelected()) return onTouchInPopup(v, event);
        else return onTouchInMain(v, event);
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Main player listener
    //////////////////////////////////////////////////////////////////////////*/

    private boolean onDoubleTapInMain(MotionEvent e) {
        if (e.getX() > playerImpl.getRootView().getWidth() * 2 / 3) {
            playerImpl.onFastForward();
        } else if (e.getX() < playerImpl.getRootView().getWidth() / 3) {
            playerImpl.onFastRewind();
        } else {
            playerImpl.getPlayPauseButton().performClick();
        }

        return true;
    }


    private boolean onSingleTapConfirmedInMain(MotionEvent e) {
        if (DEBUG) Log.d(TAG, "onSingleTapConfirmed() called with: e = [" + e + "]");

        if (playerImpl.getCurrentState() == BasePlayer.STATE_BLOCKED) return true;

        if (playerImpl.isControlsVisible()) {
            playerImpl.hideControls(150, 0);
        } else {
            if (playerImpl.getCurrentState() == BasePlayer.STATE_COMPLETED) {
                playerImpl.showControls(0);
            } else {
                playerImpl.showControlsThenHide();
            }
        }
        return true;
    }

    private boolean onScrollInMain(MotionEvent initialEvent, MotionEvent movingEvent, float distanceX, float distanceY) {
        if (!isVolumeGestureEnabled && !isBrightnessGestureEnabled) return false;

        //noinspection PointlessBooleanExpression
        if (DEBUG && false) Log.d(TAG, "MainVideoPlayer.onScroll = " +
                ", e1.getRaw = [" + initialEvent.getRawX() + ", " + initialEvent.getRawY() + "]" +
                ", e2.getRaw = [" + movingEvent.getRawX() + ", " + movingEvent.getRawY() + "]" +
                ", distanceXy = [" + distanceX + ", " + distanceY + "]");

        final boolean insideThreshold = Math.abs(movingEvent.getY() - initialEvent.getY()) <= MOVEMENT_THRESHOLD;
        if (!isMovingInMain && (insideThreshold || Math.abs(distanceX) > Math.abs(distanceY))
                || playerImpl.getCurrentState() == BasePlayer.STATE_COMPLETED) {
            return false;
        }

        isMovingInMain = true;

        if (isVolumeGestureEnabled && initialEvent.getX() > playerImpl.getRootView().getWidth() / 2.0) {
            playerImpl.getVolumeProgressBar().incrementProgressBy((int) distanceY);
            float currentProgressPercent =
                    (float) playerImpl.getVolumeProgressBar().getProgress() / playerImpl.getMaxGestureLength();
            int currentVolume = (int) (maxVolume * currentProgressPercent);
            playerImpl.getAudioReactor().setVolume(currentVolume);

            if (DEBUG) Log.d(TAG, "onScroll().volumeControl, currentVolume = " + currentVolume);

            playerImpl.getVolumeImageView().setImageDrawable(
                    AppCompatResources.getDrawable(service, currentProgressPercent <= 0 ? R.drawable.ic_volume_off_white_72dp
                            : currentProgressPercent < 0.25 ? R.drawable.ic_volume_mute_white_72dp
                            : currentProgressPercent < 0.75 ? R.drawable.ic_volume_down_white_72dp
                            : R.drawable.ic_volume_up_white_72dp)
            );

            if (playerImpl.getVolumeRelativeLayout().getVisibility() != View.VISIBLE) {
                animateView(playerImpl.getVolumeRelativeLayout(), SCALE_AND_ALPHA, true, 200);
            }
            if (playerImpl.getBrightnessRelativeLayout().getVisibility() == View.VISIBLE) {
                playerImpl.getBrightnessRelativeLayout().setVisibility(View.GONE);
            }
        } else if (isBrightnessGestureEnabled && initialEvent.getX() <= playerImpl.getRootView().getWidth() / 2.0) {
            Activity parent = playerImpl.getParentActivity();
            if (parent == null) return true;

            Window window = parent.getWindow();

            playerImpl.getBrightnessProgressBar().incrementProgressBy((int) distanceY);
            float currentProgressPercent =
                    (float) playerImpl.getBrightnessProgressBar().getProgress() / playerImpl.getMaxGestureLength();
            WindowManager.LayoutParams layoutParams = window.getAttributes();
            layoutParams.screenBrightness = currentProgressPercent;
            window.setAttributes(layoutParams);

            if (DEBUG) Log.d(TAG, "onScroll().brightnessControl, currentBrightness = " + currentProgressPercent);

            playerImpl.getBrightnessImageView().setImageDrawable(
                    AppCompatResources.getDrawable(service,
                            currentProgressPercent < 0.25 ? R.drawable.ic_brightness_low_white_72dp
                            : currentProgressPercent < 0.75 ? R.drawable.ic_brightness_medium_white_72dp
                                    : R.drawable.ic_brightness_high_white_72dp)
            );

            if (playerImpl.getBrightnessRelativeLayout().getVisibility() != View.VISIBLE) {
                animateView(playerImpl.getBrightnessRelativeLayout(), SCALE_AND_ALPHA, true, 200);
            }
            if (playerImpl.getVolumeRelativeLayout().getVisibility() == View.VISIBLE) {
                playerImpl.getVolumeRelativeLayout().setVisibility(View.GONE);
            }
        }
        return true;
    }

    private void onScrollEndInMain() {
        if (DEBUG) Log.d(TAG, "onScrollEnd() called");

        if (playerImpl.getVolumeRelativeLayout().getVisibility() == View.VISIBLE) {
            animateView(playerImpl.getVolumeRelativeLayout(), SCALE_AND_ALPHA, false, 200, 200);
        }
        if (playerImpl.getBrightnessRelativeLayout().getVisibility() == View.VISIBLE) {
            animateView(playerImpl.getBrightnessRelativeLayout(), SCALE_AND_ALPHA, false, 200, 200);
        }

        if (playerImpl.isControlsVisible() && playerImpl.getCurrentState() == STATE_PLAYING) {
            playerImpl.hideControls(DEFAULT_CONTROLS_DURATION, DEFAULT_CONTROLS_HIDE_TIME);
        }
    }

    private boolean onTouchInMain(View v, MotionEvent event) {
        playerImpl.getGestureDetector().onTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_UP && isMovingInMain) {
            isMovingInMain = false;
            onScrollEndInMain();
        }
        // This hack allows to stop receiving touch events on appbar while touching video player view
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                v.getParent().requestDisallowInterceptTouchEvent(playerImpl.isFullscreen());
                return true;
            case MotionEvent.ACTION_UP:
                v.getParent().requestDisallowInterceptTouchEvent(false);
                return false;
            default:
                return true;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Popup player listener
    //////////////////////////////////////////////////////////////////////////*/

    private boolean onDoubleTapInPopup(MotionEvent e) {
        if (playerImpl == null || !playerImpl.isPlaying()) return false;

        playerImpl.hideControls(0, 0);

        if (e.getX() > playerImpl.getPopupWidth() / 2) {
            playerImpl.onFastForward();
        } else {
            playerImpl.onFastRewind();
        }

        return true;
    }

    private boolean onSingleTapConfirmedInPopup(MotionEvent e) {
        if (playerImpl == null || playerImpl.getPlayer() == null) return false;
        if (playerImpl.isControlsVisible()) {
            playerImpl.hideControls(100, 100);
        } else {
            playerImpl.showControlsThenHide();

        }
        return true;
    }

    private boolean onDownInPopup(MotionEvent e) {
        // Fix popup position when the user touch it, it may have the wrong one
        // because the soft input is visible (the draggable area is currently resized).
        playerImpl.updateScreenSize();
        playerImpl.checkPopupPositionBounds();

        initialPopupX = playerImpl.getPopupLayoutParams().x;
        initialPopupY = playerImpl.getPopupLayoutParams().y;
        playerImpl.setPopupWidth(playerImpl.getPopupLayoutParams().width);
        playerImpl.setPopupHeight(playerImpl.getPopupLayoutParams().height);
        return super.onDown(e);
    }

    private void onLongPressInPopup(MotionEvent e) {
        playerImpl.updateScreenSize();
        playerImpl.checkPopupPositionBounds();
        playerImpl.updatePopupSize((int) playerImpl.getScreenWidth(), -1);
    }

    private boolean onScrollInPopup(MotionEvent initialEvent, MotionEvent movingEvent, float distanceX, float distanceY) {
        if (isResizing || playerImpl == null) return super.onScroll(initialEvent, movingEvent, distanceX, distanceY);

        if (!isMovingInPopup) {
            animateView(playerImpl.getCloseOverlayButton(), true, 200);
        }

        isMovingInPopup = true;

        float diffX = (int) (movingEvent.getRawX() - initialEvent.getRawX()), posX = (int) (initialPopupX + diffX);
        float diffY = (int) (movingEvent.getRawY() - initialEvent.getRawY()), posY = (int) (initialPopupY + diffY);

        if (posX > (playerImpl.getScreenWidth() - playerImpl.getPopupWidth())) posX = (int) (playerImpl.getScreenWidth() - playerImpl.getPopupWidth());
        else if (posX < 0) posX = 0;

        if (posY > (playerImpl.getScreenHeight() - playerImpl.getPopupHeight())) posY = (int) (playerImpl.getScreenHeight() - playerImpl.getPopupHeight());
        else if (posY < 0) posY = 0;

        playerImpl.getPopupLayoutParams().x = (int) posX;
        playerImpl.getPopupLayoutParams().y = (int) posY;

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

        //noinspection PointlessBooleanExpression
        if (DEBUG && false) {
            Log.d(TAG, "PopupVideoPlayer.onScroll = " +
                    ", e1.getRaw = [" + initialEvent.getRawX() + ", " + initialEvent.getRawY() + "]" + ", e1.getX,Y = [" + initialEvent.getX() + ", " + initialEvent.getY() + "]" +
                    ", e2.getRaw = [" + movingEvent.getRawX() + ", " + movingEvent.getRawY() + "]" + ", e2.getX,Y = [" + movingEvent.getX() + ", " + movingEvent.getY() + "]" +
                    ", distanceX,Y = [" + distanceX + ", " + distanceY + "]" +
                    ", posX,Y = [" + posX + ", " + posY + "]" +
                    ", popupW,H = [" + playerImpl.getPopupWidth() + " x " + playerImpl.getPopupHeight() + "]");
        }
        playerImpl.windowManager.updateViewLayout(playerImpl.getRootView(), playerImpl.getPopupLayoutParams());
        return true;
    }

    private void onScrollEndInPopup(MotionEvent event) {
        if (playerImpl == null) return;
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

    private boolean onFlingInPopup(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (playerImpl == null) return false;

        final float absVelocityX = Math.abs(velocityX);
        final float absVelocityY = Math.abs(velocityY);
        if (Math.max(absVelocityX, absVelocityY) > tossFlingVelocity) {
            if (absVelocityX > tossFlingVelocity) playerImpl.getPopupLayoutParams().x = (int) velocityX;
            if (absVelocityY > tossFlingVelocity) playerImpl.getPopupLayoutParams().y = (int) velocityY;
            playerImpl.checkPopupPositionBounds();
            playerImpl.windowManager.updateViewLayout(playerImpl.getRootView(), playerImpl.getPopupLayoutParams());
            return true;
        }
        return false;
    }

    private boolean onTouchInPopup(View v, MotionEvent event) {
        playerImpl.getGestureDetector().onTouchEvent(event);
        if (playerImpl == null) return false;
        if (event.getPointerCount() == 2 && !isMovingInPopup && !isResizing) {
            if (DEBUG) Log.d(TAG, "onTouch() 2 finger pointer detected, enabling resizing.");
            playerImpl.showAndAnimateControl(-1, true);
            playerImpl.getLoadingPanel().setVisibility(View.GONE);

            playerImpl.hideControls(0, 0);
            animateView(playerImpl.getCurrentDisplaySeek(), false, 0, 0);
            animateView(playerImpl.getResizingIndicator(), true, 200, 0);
            isResizing = true;
        }

        if (event.getAction() == MotionEvent.ACTION_MOVE && !isMovingInPopup && isResizing) {
            if (DEBUG) Log.d(TAG, "onTouch() ACTION_MOVE > v = [" + v + "],  e1.getRaw = [" + event.getRawX() + ", " + event.getRawY() + "]");
            return handleMultiDrag(event);
        }

        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (DEBUG)
                Log.d(TAG, "onTouch() ACTION_UP > v = [" + v + "],  e1.getRaw = [" + event.getRawX() + ", " + event.getRawY() + "]");
            if (isMovingInPopup) {
                isMovingInPopup = false;
                onScrollEndInPopup(event);
            }

            if (isResizing) {
                isResizing = false;
                animateView(playerImpl.getResizingIndicator(), false, 100, 0);
                playerImpl.changeState(playerImpl.getCurrentState());
            }

            if (!playerImpl.isPopupClosing) {
                playerImpl.savePositionAndSize();
            }
        }

        v.performClick();
        return true;
    }

    private boolean handleMultiDrag(final MotionEvent event) {
        if (event.getPointerCount() != 2) return false;

        final float firstPointerX = event.getX(0);
        final float secondPointerX = event.getX(1);

        final float diff = Math.abs(firstPointerX - secondPointerX);
        if (firstPointerX > secondPointerX) {
            // second pointer is the anchor (the leftmost pointer)
            playerImpl.getPopupLayoutParams().x = (int) (event.getRawX() - diff);
        } else {
            // first pointer is the anchor
            playerImpl.getPopupLayoutParams().x = (int) event.getRawX();
        }

        playerImpl.checkPopupPositionBounds();
        playerImpl.updateScreenSize();

        final int width = (int) Math.min(playerImpl.getScreenWidth(), diff);
        playerImpl.updatePopupSize(width, -1);

        return true;
    }

}


