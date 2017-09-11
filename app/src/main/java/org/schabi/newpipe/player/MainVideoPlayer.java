/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * MainVideoPlayer.java is part of NewPipe
 *
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe.player;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.Player;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.util.AnimationUtils;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.PermissionHelper;
import org.schabi.newpipe.util.ThemeHelper;

import static org.schabi.newpipe.util.AnimationUtils.animateView;

/**
 * Activity Player implementing VideoPlayer
 *
 * @author mauriciocolli
 */
public class MainVideoPlayer extends Activity {
    private static final String TAG = ".MainVideoPlayer";
    private static final boolean DEBUG = BasePlayer.DEBUG;

    private AudioManager audioManager;
    private GestureDetector gestureDetector;

    private boolean activityPaused;
    private VideoPlayerImpl playerImpl;

    /*//////////////////////////////////////////////////////////////////////////
    // Activity LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreate() called with: savedInstanceState = [" + savedInstanceState + "]");
        ThemeHelper.setTheme(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) getWindow().setStatusBarColor(Color.BLACK);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        if (getIntent() == null) {
            Toast.makeText(this, R.string.general_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        showSystemUi();
        setContentView(R.layout.activity_main_player);
        playerImpl = new VideoPlayerImpl();
        playerImpl.setup(findViewById(android.R.id.content));
        playerImpl.handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (DEBUG) Log.d(TAG, "onNewIntent() called with: intent = [" + intent + "]");
        super.onNewIntent(intent);
        playerImpl.handleIntent(intent);
    }

    @Override
    public void onBackPressed() {
        if (DEBUG) Log.d(TAG, "onBackPressed() called");
        super.onBackPressed();
        if (playerImpl.isPlaying()) playerImpl.getPlayer().setPlayWhenReady(false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (DEBUG) Log.d(TAG, "onStop() called");
        activityPaused = true;
        if (playerImpl.getPlayer() != null) {
            playerImpl.setRecovery(
                    playerImpl.getCurrentQueueIndex(),
                    (int) playerImpl.getPlayer().getCurrentPosition()
            );
            playerImpl.destroyPlayer();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (DEBUG) Log.d(TAG, "onResume() called");
        if (activityPaused) {
            playerImpl.initPlayer();
            playerImpl.getPlayPauseButton().setImageResource(R.drawable.ic_play_arrow_white);
            playerImpl.playQueue.init();
            //playerImpl.play(false);
            activityPaused = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (DEBUG) Log.d(TAG, "onDestroy() called");
        if (playerImpl != null) playerImpl.destroy();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void showSystemUi() {
        if (DEBUG) Log.d(TAG, "showSystemUi() called");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );
        } else getWindow().getDecorView().setSystemUiVisibility(0);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    private void hideSystemUi() {
        if (DEBUG) Log.d(TAG, "hideSystemUi() called");
        if (android.os.Build.VERSION.SDK_INT >= 16) {
            int visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) visibility |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            getWindow().getDecorView().setSystemUiVisibility(visibility);
        }
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    private void toggleOrientation() {
        setRequestedOrientation(getResources().getDisplayMetrics().heightPixels > getResources().getDisplayMetrics().widthPixels
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
    }

    ///////////////////////////////////////////////////////////////////////////

    @SuppressWarnings({"unused", "WeakerAccess"})
    private class VideoPlayerImpl extends VideoPlayer {
        private TextView titleTextView;
        private TextView channelTextView;
        private TextView volumeTextView;
        private TextView brightnessTextView;
        private ImageButton repeatButton;

        private ImageButton screenRotationButton;
        private ImageButton playPauseButton;

        VideoPlayerImpl() {
            super("VideoPlayerImpl" + MainVideoPlayer.TAG, MainVideoPlayer.this);
        }

        @Override
        public void initViews(View rootView) {
            super.initViews(rootView);
            this.titleTextView = rootView.findViewById(R.id.titleTextView);
            this.channelTextView = rootView.findViewById(R.id.channelTextView);
            this.volumeTextView = rootView.findViewById(R.id.volumeTextView);
            this.brightnessTextView = rootView.findViewById(R.id.brightnessTextView);
            this.repeatButton = rootView.findViewById(R.id.repeatButton);

            this.screenRotationButton = rootView.findViewById(R.id.screenRotationButton);
            this.playPauseButton = rootView.findViewById(R.id.playPauseButton);

            // Due to a bug on lower API, lets set the alpha instead of using a drawable
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) repeatButton.setImageAlpha(77);
            else { //noinspection deprecation
                repeatButton.setAlpha(77);
            }

            getRootView().setKeepScreenOn(true);
        }

        @Override
        public void initListeners() {
            super.initListeners();

            MySimpleOnGestureListener listener = new MySimpleOnGestureListener();
            gestureDetector = new GestureDetector(context, listener);
            gestureDetector.setIsLongpressEnabled(false);
            getRootView().setOnTouchListener(listener);

            repeatButton.setOnClickListener(this);
            playPauseButton.setOnClickListener(this);
            screenRotationButton.setOnClickListener(this);
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Playback Listener
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        public void shutdown() {
            super.shutdown();
            finish();
        }

        @Override
        public void sync(final StreamInfo info, final int sortedStreamsIndex) {
            super.sync(info, sortedStreamsIndex);
            titleTextView.setText(getVideoTitle());
            channelTextView.setText(getUploaderName());

            playPauseButton.setImageResource(R.drawable.ic_pause_white);
        }

        @Override
        public void onFullScreenButtonClicked() {
            super.onFullScreenButtonClicked();

            if (DEBUG) Log.d(TAG, "onFullScreenButtonClicked() called");
            if (simpleExoPlayer == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && !PermissionHelper.checkSystemAlertWindowPermission(MainVideoPlayer.this)) {
                Toast.makeText(MainVideoPlayer.this, R.string.msg_popup_permission, Toast.LENGTH_LONG).show();
                return;
            }

            final Intent intent = NavigationHelper.getOpenVideoPlayerIntent(context, PopupVideoPlayer.class, this);
            context.startService(intent);
            destroyPlayer();

            ((View) getControlAnimationView().getParent()).setVisibility(View.GONE);
            finish();
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onRepeatClicked() {
            super.onRepeatClicked();
            if (DEBUG) Log.d(TAG, "onRepeatClicked() called");
            switch (simpleExoPlayer.getRepeatMode()) {
                case Player.REPEAT_MODE_OFF:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) repeatButton.setImageAlpha(77);
                    else repeatButton.setAlpha(77);

                    break;
                case Player.REPEAT_MODE_ONE:
                    // todo change image
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) repeatButton.setImageAlpha(168);
                    else repeatButton.setAlpha(168);

                    break;
                case Player.REPEAT_MODE_ALL:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) repeatButton.setImageAlpha(255);
                    else repeatButton.setAlpha(255);

                    break;
            }
        }

        @Override
        public void onClick(View v) {
            super.onClick(v);
            if (v.getId() == repeatButton.getId()) onRepeatClicked();
            else if (v.getId() == playPauseButton.getId()) onVideoPlayPause();
            else if (v.getId() == screenRotationButton.getId()) onScreenRotationClicked();

            if (getCurrentState() != STATE_COMPLETED) {
                getControlsVisibilityHandler().removeCallbacksAndMessages(null);
                animateView(getControlsRoot(), true, 300, 0, new Runnable() {
                    @Override
                    public void run() {
                        if (getCurrentState() == STATE_PLAYING && !isSomePopupMenuVisible()) {
                            hideControls(300, DEFAULT_CONTROLS_HIDE_TIME);
                        }
                    }
                });
            }
        }

        private void onScreenRotationClicked() {
            if (DEBUG) Log.d(TAG, "onScreenRotationClicked() called");
            toggleOrientation();
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            super.onStopTrackingTouch(seekBar);
            if (wasPlaying()) {
                hideControls(100, 0);
            }
        }

        @Override
        public void onDismiss(PopupMenu menu) {
            super.onDismiss(menu);
            if (isPlaying()) hideControls(300, 0);
        }

        @Override
        public void onError(Exception exception) {
            exception.printStackTrace();
            Toast.makeText(context, "Failed to play this video", Toast.LENGTH_SHORT).show();
        }

        /*//////////////////////////////////////////////////////////////////////////
        // States
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        public void onLoading() {
            super.onLoading();
            playPauseButton.setImageResource(R.drawable.ic_pause_white);
            animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, false, 100);
            getRootView().setKeepScreenOn(true);
        }

        @Override
        public void onBuffering() {
            super.onBuffering();
            animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, false, 100);
            getRootView().setKeepScreenOn(true);
        }

        @Override
        public void onPlaying() {
            super.onPlaying();
            animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, false, 80, 0, new Runnable() {
                @Override
                public void run() {
                    playPauseButton.setImageResource(R.drawable.ic_pause_white);
                    animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, true, 200);
                }
            });
            showSystemUi();
            getRootView().setKeepScreenOn(true);
        }

        @Override
        public void onPaused() {
            super.onPaused();
            animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, false, 80, 0, new Runnable() {
                @Override
                public void run() {
                    playPauseButton.setImageResource(R.drawable.ic_play_arrow_white);
                    animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, true, 200);
                }
            });

            showSystemUi();
            getRootView().setKeepScreenOn(false);
        }

        @Override
        public void onPausedSeek() {
            super.onPausedSeek();
            animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, false, 100);
            getRootView().setKeepScreenOn(true);
        }


        @Override
        public void onCompleted() {
            showSystemUi();
            animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, false, 0, 0, new Runnable() {
                @Override
                public void run() {
                    playPauseButton.setImageResource(R.drawable.ic_replay_white);
                    animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, true, 300);
                }
            });

            getRootView().setKeepScreenOn(false);
            super.onCompleted();
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Utils
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        public void hideControls(final long duration, long delay) {
            if (DEBUG) Log.d(TAG, "hideControls() called with: delay = [" + delay + "]");
            getControlsVisibilityHandler().removeCallbacksAndMessages(null);
            getControlsVisibilityHandler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    animateView(getControlsRoot(), false, duration, 0, new Runnable() {
                        @Override
                        public void run() {
                            hideSystemUi();
                        }
                    });
                }
            }, delay);
        }

        ///////////////////////////////////////////////////////////////////////////
        // Getters
        ///////////////////////////////////////////////////////////////////////////

        public TextView getTitleTextView() {
            return titleTextView;
        }

        public TextView getChannelTextView() {
            return channelTextView;
        }

        public TextView getVolumeTextView() {
            return volumeTextView;
        }

        public TextView getBrightnessTextView() {
            return brightnessTextView;
        }

        public ImageButton getRepeatButton() {
            return repeatButton;
        }

        public ImageButton getPlayPauseButton() {
            return playPauseButton;
        }
    }

    private class MySimpleOnGestureListener extends GestureDetector.SimpleOnGestureListener implements View.OnTouchListener {
        private boolean isMoving;

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (DEBUG) Log.d(TAG, "onDoubleTap() called with: e = [" + e + "]" + "rawXy = " + e.getRawX() + ", " + e.getRawY() + ", xy = " + e.getX() + ", " + e.getY());
            //if (!playerImpl.isPlaying()) return false;
            if (!playerImpl.isPlayerReady()) return false;

            if (e.getX() > playerImpl.getRootView().getWidth() / 2)
                playerImpl.playQueue.offsetIndex(+1);
                //playerImpl.onFastForward();
            else
                playerImpl.playQueue.offsetIndex(-1);
                //playerImpl.onFastRewind();

            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (DEBUG) Log.d(TAG, "onSingleTapConfirmed() called with: e = [" + e + "]");
            if (playerImpl.getCurrentState() != BasePlayer.STATE_PLAYING) return true;

            if (playerImpl.isControlsVisible()) playerImpl.hideControls(150, 0);
            else {
                playerImpl.showControlsThenHide();
                showSystemUi();
            }
            return true;
        }

        private final boolean isGestureControlsEnabled = playerImpl.getSharedPreferences().getBoolean(getString(R.string.player_gesture_controls_key), true);

        private final float stepsBrightness = 15, stepBrightness = (1f / stepsBrightness), minBrightness = .01f;
        private float currentBrightness = .5f;

        private int currentVolume, maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        private final float stepsVolume = 15, stepVolume = (float) Math.ceil(maxVolume / stepsVolume), minVolume = 0;

        private final String brightnessUnicode = new String(Character.toChars(0x2600));
        private final String volumeUnicode = new String(Character.toChars(0x1F508));

        private final int MOVEMENT_THRESHOLD = 40;
        private final int eventsThreshold = 8;
        private boolean triggered = false;
        private int eventsNum;

        // TODO: Improve video gesture controls
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (!isGestureControlsEnabled) return false;

            //noinspection PointlessBooleanExpression
            if (DEBUG && false) Log.d(TAG, "MainVideoPlayer.onScroll = " +
                    ", e1.getRaw = [" + e1.getRawX() + ", " + e1.getRawY() + "]" +
                    ", e2.getRaw = [" + e2.getRawX() + ", " + e2.getRawY() + "]" +
                    ", distanceXy = [" + distanceX + ", " + distanceY + "]");
            float abs = Math.abs(e2.getY() - e1.getY());
            if (!triggered) {
                triggered = abs > MOVEMENT_THRESHOLD;
                return false;
            }

            if (eventsNum++ % eventsThreshold != 0 || playerImpl.getCurrentState() == BasePlayer.STATE_COMPLETED) return false;
            isMoving = true;
//            boolean up = !((e2.getY() - e1.getY()) > 0) && distanceY > 0; // Android's origin point is on top
            boolean up = distanceY > 0;


            if (e1.getX() > playerImpl.getRootView().getWidth() / 2) {
                double floor = Math.floor(up ? stepVolume : -stepVolume);
                currentVolume = (int) (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) + floor);
                if (currentVolume >= maxVolume) currentVolume = maxVolume;
                if (currentVolume <= minVolume) currentVolume = (int) minVolume;
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0);

                if (DEBUG) Log.d(TAG, "onScroll().volumeControl, currentVolume = " + currentVolume);
                playerImpl.getVolumeTextView().setText(volumeUnicode + " " + Math.round((((float) currentVolume) / maxVolume) * 100) + "%");

                if (playerImpl.getVolumeTextView().getVisibility() != View.VISIBLE) animateView(playerImpl.getVolumeTextView(), true, 200);
                if (playerImpl.getBrightnessTextView().getVisibility() == View.VISIBLE) playerImpl.getBrightnessTextView().setVisibility(View.GONE);
            } else {
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                currentBrightness += up ? stepBrightness : -stepBrightness;
                if (currentBrightness >= 1f) currentBrightness = 1f;
                if (currentBrightness <= minBrightness) currentBrightness = minBrightness;

                lp.screenBrightness = currentBrightness;
                getWindow().setAttributes(lp);
                if (DEBUG) Log.d(TAG, "onScroll().brightnessControl, currentBrightness = " + currentBrightness);
                int brightnessNormalized = Math.round(currentBrightness * 100);

                playerImpl.getBrightnessTextView().setText(brightnessUnicode + " " + (brightnessNormalized == 1 ? 0 : brightnessNormalized) + "%");

                if (playerImpl.getBrightnessTextView().getVisibility() != View.VISIBLE) animateView(playerImpl.getBrightnessTextView(), true, 200);
                if (playerImpl.getVolumeTextView().getVisibility() == View.VISIBLE) playerImpl.getVolumeTextView().setVisibility(View.GONE);
            }
            return true;
        }

        private void onScrollEnd() {
            if (DEBUG) Log.d(TAG, "onScrollEnd() called");
            triggered = false;
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
            //noinspection PointlessBooleanExpression
            if (DEBUG && false) Log.d(TAG, "onTouch() called with: v = [" + v + "], event = [" + event + "]");
            gestureDetector.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_UP && isMoving) {
                isMoving = false;
                onScrollEnd();
            }
            return true;
        }

    }
}