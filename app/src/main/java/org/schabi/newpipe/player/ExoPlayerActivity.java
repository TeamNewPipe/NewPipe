package org.schabi.newpipe.player;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.SeekBar;

import com.devbrackets.android.exomedia.listener.OnCompletionListener;
import com.devbrackets.android.exomedia.listener.OnPreparedListener;
import com.devbrackets.android.exomedia.listener.VideoControlsVisibilityListener;
import com.devbrackets.android.exomedia.ui.widget.EMVideoView;
import com.devbrackets.android.exomedia.ui.widget.VideoControlsMobile;

import org.schabi.newpipe.R;

public class ExoPlayerActivity extends Activity implements OnPreparedListener, OnCompletionListener {
    private static final String TAG = "ExoPlayerActivity";
    private static final boolean DEBUG = false;
    private EMVideoView videoView;
    private CustomVideoControls videoControls;

    public static final String VIDEO_TITLE = "video_title";
    public static final String CHANNEL_NAME = "channel_name";
    private String videoTitle = "";
    private volatile String channelName = "";
    private int lastPosition;
    private boolean isFinished;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exo_player);
        videoView = (EMVideoView) findViewById(R.id.emVideoView);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = getIntent();
        videoTitle = intent.getStringExtra(VIDEO_TITLE);
        channelName = intent.getStringExtra(CHANNEL_NAME);
        videoView.setOnPreparedListener(this);
        videoView.setOnCompletionListener(this);
        videoView.setVideoURI(intent.getData());

        videoControls = new CustomVideoControls(this);
        videoControls.setTitle(videoTitle);
        videoControls.setSubTitle(channelName);

        //We don't need these button until the playlist or queue is implemented
        videoControls.setNextButtonRemoved(true);
        videoControls.setPreviousButtonRemoved(true);

        videoControls.setVisibilityListener(new VideoControlsVisibilityListener() {
            @Override
            public void onControlsShown() {
                if (DEBUG) Log.d(TAG, "------------ onControlsShown() called");
                showSystemUi();
            }

            @Override
            public void onControlsHidden() {
                if (DEBUG) Log.d(TAG, "------------ onControlsHidden() called");
                hideSystemUi();
            }
        });
        videoView.setControls(videoControls);
    }

    @Override
    public void onPrepared() {
        if (DEBUG) Log.d(TAG, "onPrepared() called");
        videoView.start();
    }

    @Override
    public void onCompletion() {
        if (DEBUG) Log.d(TAG, "onCompletion() called");
//        videoView.getVideoControls().setButtonListener();
        //videoView.restart();
        videoControls.setRewindButtonRemoved(true);
        videoControls.setFastForwardButtonRemoved(true);
        isFinished = true;
        videoControls.getSeekBar().setEnabled(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        videoView.stopPlayback();
        lastPosition = videoView.getCurrentPosition();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (lastPosition > 0) videoView.seekTo(lastPosition);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        videoView.stopPlayback();
    }

    private void showSystemUi() {
        if (DEBUG) Log.d(TAG, "showSystemUi() called");
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(0);
    }

    private void hideSystemUi() {
        if (DEBUG) Log.d(TAG, "hideSystemUi() called");
        if (android.os.Build.VERSION.SDK_INT >= 17) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    private class CustomVideoControls extends VideoControlsMobile {
        protected static final int FAST_FORWARD_REWIND_AMOUNT = 8000;

        protected ImageButton fastForwardButton;
        protected ImageButton rewindButton;

        public CustomVideoControls(Context context) {
            super(context);
        }

        @Override
        protected int getLayoutResource() {
            return R.layout.exomedia_custom_controls;
        }

        @Override
        protected void retrieveViews() {
            super.retrieveViews();
            rewindButton = (ImageButton) findViewById(R.id.exomedia_controls_frewind_btn);
            fastForwardButton = (ImageButton) findViewById(R.id.exomedia_controls_fforward_btn);
        }

        @Override
        protected void registerListeners() {
            super.registerListeners();
            rewindButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    onRewindClicked();
                }
            });
            fastForwardButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    onFastForwardClicked();
                }
            });
        }

        public boolean onFastForwardClicked() {
            if (videoView == null) return false;

            int newPosition = videoView.getCurrentPosition() + FAST_FORWARD_REWIND_AMOUNT;
            if (newPosition > seekBar.getMax()) newPosition = seekBar.getMax();

            performSeek(newPosition);
            return true;
        }

        public boolean onRewindClicked() {
            if (videoView == null) return false;

            int newPosition = videoView.getCurrentPosition() - FAST_FORWARD_REWIND_AMOUNT;
            if (newPosition < 0) newPosition = 0;

            performSeek(newPosition);
            return true;
        }

        @Override
        public void setFastForwardButtonRemoved(boolean removed) {
            fastForwardButton.setVisibility(removed ? View.GONE : View.VISIBLE);
        }

        @Override
        public void setRewindButtonRemoved(boolean removed) {
            rewindButton.setVisibility(removed ? View.GONE : View.VISIBLE);
        }

        @Override
        protected void onPlayPauseClick() {
            super.onPlayPauseClick();
            if (videoView == null) return;
            if (DEBUG) Log.d(TAG, "onPlayPauseClick() called" + videoView.getDuration() + " position= " + videoView.getCurrentPosition());
            if (isFinished) {
                videoView.restart();
                setRewindButtonRemoved(false);
                setFastForwardButtonRemoved(false);
                isFinished = false;
                seekBar.setEnabled(true);
            }
        }

        private void performSeek(int newPosition) {
            internalListener.onSeekEnded(newPosition);
        }

        public SeekBar getSeekBar() {
            return seekBar;
        }
    }
}
