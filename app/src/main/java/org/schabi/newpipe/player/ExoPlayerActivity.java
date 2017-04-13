package org.schabi.newpipe.player;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
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

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.PermissionHelper;
import org.schabi.newpipe.util.ThemeHelper;

/**
 * Activity Player implementing AbstractPlayer
 *
 * @author mauriciocolli
 */
public class ExoPlayerActivity extends Activity {
    private static final String TAG = ".ExoPlayerActivity";
    private static final boolean DEBUG = AbstractPlayer.DEBUG;

    private AudioManager audioManager;
    private BroadcastReceiver broadcastReceiver;
    private GestureDetector gestureDetector;

    private final Runnable hideUiRunnable = new Runnable() {
        @Override
        public void run() {
            hideSystemUi();
        }
    };
    private boolean activityPaused;

    private AbstractPlayerImpl playerImpl;

    /*//////////////////////////////////////////////////////////////////////////
    // Activity LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreate() called with: savedInstanceState = [" + savedInstanceState + "]");
        ThemeHelper.setTheme(this, false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) getWindow().setStatusBarColor(Color.BLACK);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        if (getIntent() == null) {
            Toast.makeText(this, R.string.general_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        showSystemUi();
        setContentView(R.layout.activity_exo_player);
        playerImpl = new AbstractPlayerImpl();
        playerImpl.setup(findViewById(android.R.id.content));
        initReceiver();
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
        playerImpl.destroy();
        playerImpl.setVideoStartPos((int) playerImpl.getPlayer().getCurrentPosition());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (DEBUG) Log.d(TAG, "onResume() called");
        if (activityPaused) {
            //playerImpl.getPlayer().setPlayWhenReady(true);
            playerImpl.getPlayPauseButton().setImageResource(R.drawable.ic_play_arrow_white);
            playerImpl.initPlayer();
            playerImpl.playVideo(playerImpl.getSelectedStreamUri(), false);
            activityPaused = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (DEBUG) Log.d(TAG, "onDestroy() called");
        if (playerImpl != null) playerImpl.destroy();
        if (broadcastReceiver != null) unregisterReceiver(broadcastReceiver);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    private void initReceiver() {
        if (DEBUG) Log.d(TAG, "initReceiver() called");
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (DEBUG) Log.d(TAG, "onReceive() called with: context = [" + context + "], intent = [" + intent + "]");
                switch (intent.getAction()) {
                    case AbstractPlayer.ACTION_UPDATE_THUMB:
                        playerImpl.onUpdateThumbnail(intent);
                        break;
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(AbstractPlayer.ACTION_UPDATE_THUMB);
        registerReceiver(broadcastReceiver, intentFilter);
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
    private class AbstractPlayerImpl extends AbstractPlayer {
        private TextView titleTextView;
        private TextView channelTextView;
        private TextView volumeTextView;
        private TextView brightnessTextView;
        private ImageButton repeatButton;

        private ImageButton screenRotationButton;
        private ImageButton playPauseButton;

        AbstractPlayerImpl() {
            super("AbstractPlayerImpl" + ExoPlayerActivity.TAG, ExoPlayerActivity.this);
        }

        @Override
        public void initViews(View rootView) {
            super.initViews(rootView);
            this.titleTextView = (TextView) rootView.findViewById(R.id.titleTextView);
            this.channelTextView = (TextView) rootView.findViewById(R.id.channelTextView);
            this.volumeTextView = (TextView) rootView.findViewById(R.id.volumeTextView);
            this.brightnessTextView = (TextView) rootView.findViewById(R.id.brightnessTextView);
            this.repeatButton = (ImageButton) rootView.findViewById(R.id.repeatButton);

            this.screenRotationButton = (ImageButton) rootView.findViewById(R.id.screenRotationButton);
            this.playPauseButton = (ImageButton) rootView.findViewById(R.id.playPauseButton);

            // Due to a bug on lower API, lets set the alpha instead of using a drawable
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) repeatButton.setImageAlpha(77);
            else { //noinspection deprecation
                repeatButton.setAlpha(77);
            }

        }

        @Override
        public void initListeners() {
            super.initListeners();

            MySimpleOnGestureListener listener = new MySimpleOnGestureListener();
            gestureDetector = new GestureDetector(context, listener);
            gestureDetector.setIsLongpressEnabled(false);
            playerImpl.getRootView().setOnTouchListener(listener);

            repeatButton.setOnClickListener(this);
            playPauseButton.setOnClickListener(this);
            screenRotationButton.setOnClickListener(this);
        }

        @Override
        public void handleIntent(Intent intent) {
            super.handleIntent(intent);
            titleTextView.setText(getVideoTitle());
            channelTextView.setText(getChannelName());
        }

        @Override
        public void playVideo(Uri videoURI, boolean autoPlay) {
            super.playVideo(videoURI, autoPlay);
            playPauseButton.setImageResource(autoPlay ? R.drawable.ic_pause_white : R.drawable.ic_play_arrow_white);
        }

        @Override
        public void onFullScreenButtonClicked() {
            if (DEBUG) Log.d(TAG, "onFullScreenButtonClicked() called");
            if (playerImpl.getPlayer() == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && !PermissionHelper.checkSystemAlertWindowPermission(ExoPlayerActivity.this)) {
                Toast.makeText(ExoPlayerActivity.this, R.string.msg_popup_permission, Toast.LENGTH_LONG).show();
                return;
            }

            Intent i = new Intent(ExoPlayerActivity.this, PopupVideoPlayer.class);
            i.putExtra(AbstractPlayer.VIDEO_TITLE, getVideoTitle())
                    .putExtra(AbstractPlayer.CHANNEL_NAME, getChannelName())
                    .putExtra(AbstractPlayer.VIDEO_URL, getVideoUrl())
                    .putExtra(AbstractPlayer.INDEX_SEL_VIDEO_STREAM, getSelectedIndexStream())
                    .putExtra(AbstractPlayer.VIDEO_STREAMS_LIST, getVideoStreamsList())
                    .putExtra(AbstractPlayer.START_POSITION, ((int) getPlayer().getCurrentPosition()));
            context.startService(i);
            ((View) getControlAnimationView().getParent()).setVisibility(View.GONE);
            if (playerImpl.isPlaying()) playerImpl.getPlayer().setPlayWhenReady(false);
            ExoPlayerActivity.this.finish();
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onRepeatClicked() {
            super.onRepeatClicked();
            if (DEBUG) Log.d(TAG, "onRepeatClicked() called");
            switch (getCurrentRepeatMode()) {
                case REPEAT_DISABLED:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) repeatButton.setImageAlpha(77);
                    else repeatButton.setAlpha(77);

                    break;
                case REPEAT_ONE:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) repeatButton.setImageAlpha(255);
                    else repeatButton.setAlpha(255);

                    break;
                case REPEAT_ALL:
                    // Waiting :)
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
                animateView(playerImpl.getControlsRoot(), true, 300, 0, new Runnable() {
                    @Override
                    public void run() {
                        if (getCurrentState() == STATE_PLAYING && !playerImpl.isQualityMenuVisible()) {
                            animateView(playerImpl.getControlsRoot(), false, 300, DEFAULT_CONTROLS_HIDE_TIME, true);
                        }
                    }
                }, false);
            }
        }

        private void onScreenRotationClicked() {
            if (DEBUG) Log.d(TAG, "onScreenRotationClicked() called");
            toggleOrientation();
        }

        @Override
        public void onVideoPlayPause() {
            super.onVideoPlayPause();
            if (getPlayer().getPlayWhenReady()) {
                animateView(playPauseButton, false, 80, 0, new Runnable() {
                    @Override
                    public void run() {
                        playPauseButton.setImageResource(R.drawable.ic_pause_white);
                        animateView(playPauseButton, true, 200, 0);
                    }
                });
            } else {
                animateView(playPauseButton, false, 80, 0, new Runnable() {
                    @Override
                    public void run() {
                        playPauseButton.setImageResource(R.drawable.ic_play_arrow_white);
                        animateView(playPauseButton, true, 200, 0);
                    }
                });
            }
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            super.onStopTrackingTouch(seekBar);
            if (playerImpl.wasPlaying()) {
                animateView(playerImpl.getControlsRoot(), false, 100, 0);
            }
        }

        @Override
        public void onDismiss(PopupMenu menu) {
            super.onDismiss(menu);
            if (isPlaying()) animateView(getControlsRoot(), false, 500, 0, true);
        }

        @Override
        public void onError() {
            Toast.makeText(context, "Failed to play this video", Toast.LENGTH_SHORT).show();
            finish();
        }

        /*//////////////////////////////////////////////////////////////////////////
        // States
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        public void onLoading() {
            super.onLoading();
            playPauseButton.setImageResource(R.drawable.ic_pause_white);
            animateView(playPauseButton, false, 100, 0);
        }

        @Override
        public void onBuffering() {
            super.onBuffering();
            animateView(playPauseButton, false, 100, 0);
        }

        @Override
        public void onPaused() {
            super.onPaused();
            animateView(playPauseButton, true, 100, 0);
            showSystemUi();
        }

        @Override
        public void onPausedSeek() {
            super.onPausedSeek();
            animateView(playPauseButton, false, 100, 0);
        }

        @Override
        public void onPlaying() {
            super.onPlaying();
            animateView(playPauseButton, true, 500, 0);
            showSystemUi();
        }

        @Override
        public void onCompleted() {
            if (getCurrentRepeatMode() == RepeatMode.REPEAT_ONE) {
                playPauseButton.setImageResource(R.drawable.ic_pause_white);
            } else {
                showSystemUi();
                animateView(playPauseButton, false, 0, 0, new Runnable() {
                    @Override
                    public void run() {
                        playPauseButton.setImageResource(R.drawable.ic_replay_white);
                        animateView(playPauseButton, true, 300, 0);
                    }
                });
            }
            super.onCompleted();
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Utils
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        public void animateView(View view, boolean enterOrExit, long duration, long delay, final Runnable execOnEnd, boolean hideUi) {
            //if (execOnEnd == null) playerImpl.setDefaultAnimationEnd(hideUiRunnable);

            if (hideUi && execOnEnd != null) {
                Runnable combinedRunnable = new Runnable() {
                    @Override
                    public void run() {
                        execOnEnd.run();
                        hideUiRunnable.run();
                    }
                };
                super.animateView(view, enterOrExit, duration, delay, combinedRunnable, true);
            } else super.animateView(view, enterOrExit, duration, delay, hideUi ? hideUiRunnable : execOnEnd, hideUi);
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
            if (!playerImpl.isPlaying()) return false;
            if (e.getX() > playerImpl.getRootView().getWidth() / 2) playerImpl.onFastForward();
            else playerImpl.onFastRewind();
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (DEBUG) Log.d(TAG, "onSingleTapConfirmed() called with: e = [" + e + "]");
            if (playerImpl.getCurrentState() != StateInterface.STATE_PLAYING) return true;

            if (playerImpl.isControlsVisible()) playerImpl.animateView(playerImpl.getControlsRoot(), false, 150, 0, true);
            else {
                playerImpl.animateView(playerImpl.getControlsRoot(), true, 500, 0, new Runnable() {
                    @Override
                    public void run() {
                        playerImpl.animateView(playerImpl.getControlsRoot(), false, 300, AbstractPlayer.DEFAULT_CONTROLS_HIDE_TIME, true);
                    }
                });
                showSystemUi();
            }
            return true;
        }

        private final float stepsBrightness = 21, stepBrightness = (1f / stepsBrightness), minBrightness = .01f;
        private float currentBrightness = .5f;

        private int currentVolume, maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        private final String brightnessUnicode = new String(Character.toChars(0x2600));
        //        private final String volumeUnicode = new String(Character.toChars(0x1F50A));
        private final String volumeUnicode = new String(Character.toChars(0x1F508));


        private final int MOVEMENT_THRESHOLD = 40;
        private final int eventsThreshold = 3;
        private boolean triggered = false;
        private int eventsNum;

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            //noinspection PointlessBooleanExpression
            if (DEBUG && false) Log.d(TAG, "ExoPlayerActivity.onScroll = " +
                    ", e1.getRaw = [" + e1.getRawX() + ", " + e1.getRawY() + "]" +
                    ", e2.getRaw = [" + e2.getRawX() + ", " + e2.getRawY() + "]" +
                    ", distanceXy = [" + distanceX + ", " + distanceY + "]");
            float abs = Math.abs(e2.getY() - e1.getY());
            if (!triggered) {
                triggered = abs > MOVEMENT_THRESHOLD;
                return false;
            }

            if (eventsNum++ % eventsThreshold != 0 || playerImpl.getCurrentState() == StateInterface.STATE_COMPLETED) return false;
            isMoving = true;
//            boolean up = !((e2.getY() - e1.getY()) > 0) && distanceY > 0; // Android's origin point is on top
            boolean up = distanceY > 0;


            if (e1.getX() > playerImpl.getRootView().getWidth() / 2) {
                currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) + (up ? 1 : -1);
                if (currentVolume >= maxVolume) currentVolume = maxVolume;
                if (currentVolume <= 0) currentVolume = 0;
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0);

                if (DEBUG) Log.d(TAG, "onScroll().volumeControl, currentVolume = " + currentVolume);
                playerImpl.getVolumeTextView().setText(volumeUnicode + " " + Math.round((((float) currentVolume) / maxVolume) * 100) + "%");

                if (playerImpl.getVolumeTextView().getVisibility() != View.VISIBLE) playerImpl.animateView(playerImpl.getVolumeTextView(), true, 200, 0);
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

                if (playerImpl.getBrightnessTextView().getVisibility() != View.VISIBLE) playerImpl.animateView(playerImpl.getBrightnessTextView(), true, 200, 0);
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
            if (playerImpl.getVolumeTextView().getVisibility() == View.VISIBLE) playerImpl.animateView(playerImpl.getVolumeTextView(), false, 200, 200);
            if (playerImpl.getBrightnessTextView().getVisibility() == View.VISIBLE) playerImpl.animateView(playerImpl.getBrightnessTextView(), false, 200, 200);

            if (playerImpl.isControlsVisible() && playerImpl.getCurrentState() == StateInterface.STATE_PLAYING) {
                playerImpl.animateView(playerImpl.getControlsRoot(), false, 300, AbstractPlayer.DEFAULT_CONTROLS_HIDE_TIME);
            }
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            gestureDetector.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_UP && isMoving) {
                isMoving = false;
                onScrollEnd();
            }
            return true;
        }

    }
}