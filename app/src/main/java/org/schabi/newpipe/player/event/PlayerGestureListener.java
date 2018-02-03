package org.schabi.newpipe.player.event;

import android.app.Activity;
import android.os.Build;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import org.schabi.newpipe.player.BasePlayer;
import org.schabi.newpipe.player.MainPlayerService;
import org.schabi.newpipe.player.VideoPlayer;
import org.schabi.newpipe.player.VideoPlayerImpl;
import org.schabi.newpipe.player.helper.PlayerHelper;

import static org.schabi.newpipe.player.BasePlayer.DEBUG;
import static org.schabi.newpipe.player.BasePlayer.TAG;
import static org.schabi.newpipe.util.AnimationUtils.animateView;

public class PlayerGestureListener extends GestureDetector.SimpleOnGestureListener implements View.OnTouchListener {
    private VideoPlayerImpl playerImpl;
    private MainPlayerService service;

    private int initialPopupX, initialPopupY;
    private boolean isMoving;

    private boolean isResizing;

    private int shutdownFlingVelocity;
    private int tossFlingVelocity;

    public PlayerGestureListener(final VideoPlayerImpl playerImpl, final MainPlayerService service) {
        this.playerImpl = playerImpl;
        this.service = service;
        this.shutdownFlingVelocity = PlayerHelper.getShutdownFlingVelocity(service);
        this.tossFlingVelocity = PlayerHelper.getTossFlingVelocity(service);
        this.isPlayerGestureEnabled = PlayerHelper.isPlayerGestureEnabled(service.getApplicationContext());
        this.maxVolume = playerImpl.getAudioReactor().getMaxVolume();
        this.stepVolume = (float) Math.ceil(maxVolume / stepsVolume);
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        if (DEBUG) Log.d(TAG, "onDoubleTap() called with: e = [" + e + "]" + "rawXy = " + e.getRawX() + ", " + e.getRawY() + ", xy = " + e.getX() + ", " + e.getY());
        if (playerImpl.getPlayer() == null || !playerImpl.isPlaying() || !playerImpl.isPlayerReady()) return false;

        float widthToCheck = playerImpl.popupPlayerSelected() ? playerImpl.getPopupWidth() / 2 : playerImpl.getRootView().getWidth() / 2;

        if (e.getX() > widthToCheck) {
            playerImpl.onFastForward();
        } else {
            playerImpl.onFastRewind();
        }

        return true;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        if (DEBUG) Log.d(TAG, "onSingleTapConfirmed() called with: e = [" + e + "]");

        if(playerImpl.popupPlayerSelected()) {
            playerImpl.onVideoPlayPause();
            return true;
        }

        if (playerImpl.getCurrentState() == BasePlayer.STATE_BLOCKED) return true;

        if (playerImpl.isControlsVisible()) playerImpl.hideControls(150, 0);
        else {
            if(playerImpl.getCurrentState() == BasePlayer.STATE_COMPLETED)
                playerImpl.showControls(0);
            else
                playerImpl.showControlsThenHide();

            Activity parent = playerImpl.getParentActivity();
            if (parent != null && playerImpl.isInFullscreen()) {
                Window window = parent.getWindow();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
                } else
                    window.getDecorView().setSystemUiVisibility(0);
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
        }
        return true;
    }



        /*//////////////////////////////////////////////////////////////////////////
        // Popup only
        //////////////////////////////////////////////////////////////////////////*/

    @Override
    public boolean onDown(MotionEvent e) {
        if (DEBUG) Log.d(TAG, "onDown() called with: e = [" + e + "]");

        if(!playerImpl.popupPlayerSelected()) return super.onDown(e);

        initialPopupX = playerImpl.getWindowLayoutParams().x;
        initialPopupY = playerImpl.getWindowLayoutParams().y;
        playerImpl.setPopupWidth(playerImpl.getWindowLayoutParams().width);
        playerImpl.setPopupHeight(playerImpl.getWindowLayoutParams().height);
        return super.onDown(e);
    }

    @Override
    public void onLongPress(MotionEvent e) {
        if (DEBUG) Log.d(TAG, "onLongPress() called with: e = [" + e + "]");

        if(!playerImpl.popupPlayerSelected()) return;

        playerImpl.updateScreenSize();
        playerImpl.checkPositionBounds();
        playerImpl.updatePopupSize(playerImpl.getWindowLayoutParams(), (int) playerImpl.getScreenWidth(), -1);
    }

    private boolean handleOnScrollInPopup(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        if (isResizing) return super.onScroll(e1, e2, distanceX, distanceY);

        if (playerImpl.getCurrentState() != BasePlayer.STATE_BUFFERING
                && (!isMoving || playerImpl.getControlsRoot().getAlpha() != 1f)) playerImpl.showControls(0);
        isMoving = true;

        float diffX = (int) (e2.getRawX() - e1.getRawX()), posX = (int) (initialPopupX + diffX);
        float diffY = (int) (e2.getRawY() - e1.getRawY()), posY = (int) (initialPopupY + diffY);

        if (posX > (playerImpl.getScreenWidth() - playerImpl.getPopupWidth())) posX = (int) (playerImpl.getScreenWidth() - playerImpl.getPopupWidth());
        else if (posX < 0) posX = 0;

        if (posY > (playerImpl.getScreenHeight() - playerImpl.getPopupHeight())) posY = (int) (playerImpl.getScreenHeight() - playerImpl.getPopupHeight());
        else if (posY < 0) posY = 0;

        playerImpl.getWindowLayoutParams().x = (int) posX;
        playerImpl.getWindowLayoutParams().y = (int) posY;

        //noinspection PointlessBooleanExpression
        if (DEBUG && false) Log.d(TAG, "MainPlayer.onScroll = " +
                ", e1.getRaw = [" + e1.getRawX() + ", " + e1.getRawY() + "]" +
                ", e2.getRaw = [" + e2.getRawX() + ", " + e2.getRawY() + "]" +
                ", distanceXy = [" + distanceX + ", " + distanceY + "]" +
                ", posXy = [" + posX + ", " + posY + "]" +
                ", popupWh = [" + playerImpl.getPopupWidth() + " x " + playerImpl.getPopupHeight() + "]");
        playerImpl.updateViewLayout(service.getView(), playerImpl.getWindowLayoutParams());
        return true;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (DEBUG) Log.d(TAG, "Fling velocity: dX=[" + velocityX + "], dY=[" + velocityY + "]");
        if(!playerImpl.popupPlayerSelected()) return true;

        final float absVelocityX = Math.abs(velocityX);
        final float absVelocityY = Math.abs(velocityY);
        if (absVelocityX > shutdownFlingVelocity) {
            service.onDestroy();
            return true;
        } else if (Math.max(absVelocityX, absVelocityY) > tossFlingVelocity) {
            if (absVelocityX > tossFlingVelocity) playerImpl.getWindowLayoutParams().x = (int) velocityX;
            if (absVelocityY > tossFlingVelocity) playerImpl.getWindowLayoutParams().y = (int) velocityY;
            playerImpl.checkPositionBounds();
            playerImpl.updateViewLayout(service.getView(), playerImpl.getWindowLayoutParams());
            return true;
        }
        return false;
    }

    private boolean handleTouchInPopup(View v, MotionEvent event) {
        playerImpl.getGestureDetector().onTouchEvent(event);

        if (event.getPointerCount() == 2 && !isResizing) {
            if (DEBUG) Log.d(TAG, "onTouch() 2 finger pointer detected, enabling resizing.");
            playerImpl.showAndAnimateControl(-1, true);
            playerImpl.getLoadingPanel().setVisibility(View.GONE);

            playerImpl.hideControls(0, 0);
            animateView(playerImpl.getCurrentDisplaySeek(), false, 0, 0);
            animateView(playerImpl.getResizingIndicator(), true, 200, 0);
            isResizing = true;
        }

        if (event.getAction() == MotionEvent.ACTION_MOVE && !isMoving && isResizing) {
            if (DEBUG) Log.d(TAG, "onTouch() ACTION_MOVE > v = [" + v + "],  e1.getRaw = [" + event.getRawX() + ", " + event.getRawY() + "]");
            return handleMultiDrag(event);
        }

        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (DEBUG)
                Log.d(TAG, "onTouch() ACTION_UP > v = [" + v + "],  e1.getRaw = [" + event.getRawX() + ", " + event.getRawY() + "]");
            if (isMoving) {
                isMoving = false;
                onScrollEnd();
            }

            if (isResizing) {
                isResizing = false;
                animateView(playerImpl.getResizingIndicator(), false, 100, 0);
                playerImpl.changeState(playerImpl.getCurrentState());
            }
            playerImpl.savePositionAndSize();
        }

        v.performClick();
        return true;
    }

    private boolean handleMultiDrag(final MotionEvent event) {
        if(!playerImpl.popupPlayerSelected()) return true;


        if (event.getPointerCount() != 2) return false;

        final float firstPointerX = event.getX(0);
        final float secondPointerX = event.getX(1);

        final float diff = Math.abs(firstPointerX - secondPointerX);
        if (firstPointerX > secondPointerX) {
            // second pointer is the anchor (the leftmost pointer)
            playerImpl.getWindowLayoutParams().x = (int) (event.getRawX() - diff);
        } else {
            // first pointer is the anchor
            playerImpl.getWindowLayoutParams().x = (int) event.getRawX();
        }

        playerImpl.checkPositionBounds();
        playerImpl.updateScreenSize();

        final int width = (int) Math.min(playerImpl.getScreenWidth(), diff);
        playerImpl.updatePopupSize(playerImpl.getWindowLayoutParams(), width, -1);

        return true;
    }


    private final boolean isPlayerGestureEnabled;

    private final float stepsBrightness = 15, stepBrightness = (1f / stepsBrightness), minBrightness = .01f;
    private float currentBrightness = .5f;

    private int currentVolume;
    private final int maxVolume;
    private final float stepsVolume = 15, stepVolume, minVolume = 0;

    private final String brightnessUnicode = new String(Character.toChars(0x2600));
    private final String volumeUnicode = new String(Character.toChars(0x1F508));

    private final int MOVEMENT_THRESHOLD = 40;
    private final int eventsThreshold = 8;
    private boolean triggeredX = false;
    private boolean triggeredY = false;
    private int eventsNum;

    // TODO: Improve video gesture controls
    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        if(playerImpl.popupPlayerSelected()) return handleOnScrollInPopup(e1, e2, distanceX, distanceY);

        if (!isPlayerGestureEnabled) return false;

        //noinspection PointlessBooleanExpression
        if (DEBUG && false) Log.d(TAG, "MainPlayerService.onScroll = " +
                ", e1.getRaw = [" + e1.getRawX() + ", " + e1.getRawY() + "]" +
                ", e2.getRaw = [" + e2.getRawX() + ", " + e2.getRawY() + "]" +
                ", distanceXy = [" + distanceX + ", " + distanceY + "]");
        float absX = Math.abs(e2.getX() - e1.getX());
        float absY = Math.abs(e2.getY() - e1.getY());

        if (!triggeredX && !triggeredY) {
            triggeredX = absX > MOVEMENT_THRESHOLD && absX > absY;
            triggeredY = absY > MOVEMENT_THRESHOLD && absY > absX;
            return false;
        }

        // It will help to drop two events at a time
        if(absX > absY && !triggeredX) return false;
        if(absX < absY && !triggeredY) return false;

        if(absX > absY) {
            isMoving = true;
            boolean right = distanceX < 0;
            float duration = playerImpl.getPlayer().getDuration();
            float distance = right? absX : -absX;
            float currentPosition = playerImpl.getPlayer().getCurrentPosition();
            float position = currentPosition + distance * 1000 / 200;
            position = position >= duration ? duration - 5000 : position;
            position = position <= 0 ? 0 : position;
            if(!playerImpl.isControlsVisible())
                playerImpl.showControls(0);

            playerImpl.getPlayer().seekTo((long)position);
        }
        else {
            if (eventsNum++ % eventsThreshold != 0 || playerImpl.getCurrentState() == BasePlayer.STATE_COMPLETED) return false;
            isMoving = true;
//            boolean up = !((e2.getY() - e1.getY()) > 0) && distanceY > 0; // Android's origin point is on top
            boolean up = distanceY > 0;

            if (e1.getX() > playerImpl.getRootView().getWidth() / 2) {
                double floor = Math.floor(up ? stepVolume : -stepVolume);
                currentVolume = (int) (playerImpl.getAudioReactor().getVolume() + floor);
                if (currentVolume >= maxVolume) currentVolume = maxVolume;
                if (currentVolume <= minVolume) currentVolume = (int) minVolume;
                playerImpl.getAudioReactor().setVolume(currentVolume);

                currentVolume = playerImpl.getAudioReactor().getVolume();
                if (DEBUG) Log.d(TAG, "onScroll().volumeControl, currentVolume = " + currentVolume);
                final String volumeText = volumeUnicode + " " + Math.round((((float) currentVolume) / maxVolume) * 100) + "%";
                playerImpl.getVolumeTextView().setText(volumeText);

                if (playerImpl.getVolumeTextView().getVisibility() != View.VISIBLE) animateView(playerImpl.getVolumeTextView(), true, 200);
                if (playerImpl.getBrightnessTextView().getVisibility() == View.VISIBLE) playerImpl.getBrightnessTextView().setVisibility(View.GONE);
            } else if(service.getView().getContext() != null) {
                Activity parent = playerImpl.getParentActivity();
                if(parent == null) return true;

                Window window = parent.getWindow();

                WindowManager.LayoutParams lp = window.getAttributes();
                currentBrightness += up ? stepBrightness : -stepBrightness;
                if (currentBrightness >= 1f) currentBrightness = 1f;
                if (currentBrightness <= minBrightness) currentBrightness = minBrightness;

                lp.screenBrightness = currentBrightness;
                window.setAttributes(lp);
                if (DEBUG) Log.d(TAG, "onScroll().brightnessControl, currentBrightness = " + currentBrightness);
                int brightnessNormalized = Math.round(currentBrightness * 100);

                final String brightnessText = brightnessUnicode + " " + (brightnessNormalized == 1 ? 0 : brightnessNormalized) + "%";
                playerImpl.getBrightnessTextView().setText(brightnessText);

                if (playerImpl.getBrightnessTextView().getVisibility() != View.VISIBLE) animateView(playerImpl.getBrightnessTextView(), true, 200);
                if (playerImpl.getVolumeTextView().getVisibility() == View.VISIBLE) playerImpl.getVolumeTextView().setVisibility(View.GONE);
            }
        }
        return true;
    }

    private void onScrollEnd() {
        if (DEBUG) Log.d(TAG, "onScrollEnd() called");
        triggeredX = false;
        triggeredY = false;
        eventsNum = 0;
            /* if (playerImpl.getVolumeTextView().getVisibility() == View.VISIBLE) playerImpl.getVolumeTextView().setVisibility(View.GONE);
            if (playerImpl.getBrightnessTextView().getVisibility() == View.VISIBLE) playerImpl.getBrightnessTextView().setVisibility(View.GONE);*/
        if (playerImpl.getVolumeTextView().getVisibility() == View.VISIBLE) animateView(playerImpl.getVolumeTextView(), false, 200, 200);
        if (playerImpl.getBrightnessTextView().getVisibility() == View.VISIBLE) animateView(playerImpl.getBrightnessTextView(), false, 200, 200);

        if (playerImpl.isControlsVisible() && playerImpl.getCurrentState() == BasePlayer.STATE_PLAYING) {
            playerImpl.hideControls(300, VideoPlayer.DEFAULT_CONTROLS_HIDE_TIME);
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if(playerImpl.popupPlayerSelected()) return handleTouchInPopup(v, event);

        //noinspection PointlessBooleanExpression
        if (DEBUG && false) Log.d(TAG, "onTouch() called with: v = [" + v + "], event = [" + event + "]");
        playerImpl.getGestureDetector().onTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_UP && isMoving) {
            isMoving = false;
            onScrollEnd();
        }

        // This hack allows to stop receiving touch events on scrollview while touching video player view
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                v.getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_UP:
                v.getParent().requestDisallowInterceptTouchEvent(false);
                return false;
            case MotionEvent.ACTION_MOVE:
                v.getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            default:
                return true;
        }
    }

}