package org.schabi.newpipe.player;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.RemoteViews;
import android.widget.SeekBar;
import android.widget.Toast;

import com.devbrackets.android.exomedia.listener.OnCompletionListener;
import com.devbrackets.android.exomedia.listener.OnErrorListener;
import com.devbrackets.android.exomedia.listener.OnPreparedListener;
import com.devbrackets.android.exomedia.listener.OnSeekCompletionListener;
import com.devbrackets.android.exomedia.ui.widget.EMVideoView;
import com.devbrackets.android.exomedia.util.Repeater;
import com.devbrackets.android.exomedia.util.TimeFormatUtil;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;

import org.schabi.newpipe.ActivityCommunicator;
import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.R;
import org.schabi.newpipe.detail.VideoItemDetailActivity;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream_info.StreamExtractor;
import org.schabi.newpipe.extractor.stream_info.StreamInfo;
import org.schabi.newpipe.extractor.stream_info.VideoStream;
import org.schabi.newpipe.player.popup.PopupViewHolder;
import org.schabi.newpipe.player.popup.StateInterface;
import org.schabi.newpipe.util.NavStack;

public class PopupVideoPlayer extends Service implements StateInterface {
    private static final String TAG = ".PopupVideoPlayer";
    private static final boolean DEBUG = false;
    private static int CURRENT_STATE = -1;

    private static final int NOTIFICATION_ID = 40028922;
    protected static final int FAST_FORWARD_REWIND_AMOUNT = 10000; // 10 Seconds
    protected static final int DEFAULT_CONTROLS_HIDE_TIME = 2000;  // 2 Seconds

    private BroadcastReceiver broadcastReceiver;
    private InternalListener internalListener;

    private WindowManager windowManager;
    private WindowManager.LayoutParams windowLayoutParams;
    private GestureDetector gestureDetector;
    private ValueAnimator controlViewAnimator;
    private PopupViewHolder viewHolder;
    private EMVideoView emVideoView;

    private float screenWidth, screenHeight;
    private float popupWidth, popupHeight;
    private float currentPopupHeight = 200;
    //private float minimumHeight = 100; // TODO: Use it when implementing the resize of the popup

    public static final String VIDEO_URL = "video_url";
    public static final String STREAM_URL = "stream_url";
    public static final String VIDEO_TITLE = "video_title";
    public static final String CHANNEL_NAME = "channel_name";

    private NotificationManager notificationManager;
    private NotificationCompat.Builder notBuilder;
    private RemoteViews notRemoteView;

    private Uri streamUri;
    private String videoUrl = "";
    private String videoTitle = "";
    private volatile String channelName = "";

    private ImageLoader imageLoader = ImageLoader.getInstance();
    private DisplayImageOptions displayImageOptions =
            new DisplayImageOptions.Builder().cacheInMemory(true).build();
    private volatile Bitmap videoThumbnail;

    private Repeater progressPollRepeater = new Repeater();
    private SharedPreferences sharedPreferences;

    @Override
    public void onCreate() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        notificationManager = ((NotificationManager) getSystemService(NOTIFICATION_SERVICE));
        internalListener = new InternalListener();
        viewHolder = new PopupViewHolder(null);
        progressPollRepeater.setRepeatListener(internalListener);
        progressPollRepeater.setRepeaterDelay(500);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(PopupVideoPlayer.this);
        initReceiver();
    }

    private void initReceiver() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (DEBUG)
                    Log.d(TAG, "onReceive() called with: context = [" + context + "], intent = [" + intent + "]");
                switch (intent.getAction()) {
                    case InternalListener.ACTION_CLOSE:
                        internalListener.onVideoClose();
                        break;
                    case InternalListener.ACTION_PLAY_PAUSE:
                        internalListener.onVideoPlayPause();
                        break;
                    case InternalListener.ACTION_OPEN_DETAIL:
                        internalListener.onOpenDetail(PopupVideoPlayer.this, videoUrl);
                        break;
                    case InternalListener.ACTION_UPDATE_THUMB:
                        internalListener.onUpdateThumbnail(intent);
                        break;
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(InternalListener.ACTION_CLOSE);
        intentFilter.addAction(InternalListener.ACTION_PLAY_PAUSE);
        intentFilter.addAction(InternalListener.ACTION_OPEN_DETAIL);
        intentFilter.addAction(InternalListener.ACTION_UPDATE_THUMB);
        registerReceiver(broadcastReceiver, intentFilter);
    }

    @SuppressLint({"RtlHardcoded"})
    private void initPopup() {
        if (DEBUG) Log.d(TAG, "initPopup() called");
        View rootView = View.inflate(this, R.layout.player_popup, null);
        viewHolder = new PopupViewHolder(rootView);
        viewHolder.getPlaybackSeekBar().setOnSeekBarChangeListener(internalListener);
        emVideoView = viewHolder.getVideoView();
        emVideoView.setOnPreparedListener(internalListener);
        emVideoView.setOnCompletionListener(internalListener);
        emVideoView.setOnErrorListener(internalListener);
        emVideoView.setOnSeekCompletionListener(internalListener);

        windowLayoutParams = new WindowManager.LayoutParams(
                (int) getMinimumVideoWidth(currentPopupHeight), (int) currentPopupHeight,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        windowLayoutParams.gravity = Gravity.LEFT | Gravity.TOP;

        MySimpleOnGestureListener listener = new MySimpleOnGestureListener();
        gestureDetector = new GestureDetector(this, listener);
        gestureDetector.setIsLongpressEnabled(false);
        rootView.setOnTouchListener(listener);
        updateScreenSize();

        windowManager.addView(rootView, windowLayoutParams);
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "onStartCommand() called with: intent = [" + intent + "], flags = [" + flags + "], startId = [" + startId + "]");
        if (emVideoView == null) initPopup();

        if (intent.getStringExtra(NavStack.URL) != null) {
            Thread fetcher = new Thread(new FetcherRunnable(intent));
            fetcher.start();
        } else {
            if (imageLoader != null) imageLoader.clearMemoryCache();
            streamUri = Uri.parse(intent.getStringExtra(STREAM_URL));
            videoUrl = intent.getStringExtra(VIDEO_URL);
            videoTitle = intent.getStringExtra(VIDEO_TITLE);
            channelName = intent.getStringExtra(CHANNEL_NAME);
            try {
                videoThumbnail = ActivityCommunicator.getCommunicator().backgroundPlayerThumbnail;
            } catch (Exception e) {
                e.printStackTrace();
            }
            playVideo(streamUri);
        }
        return START_NOT_STICKY;
    }

    private float getMinimumVideoWidth(float height) {
        float width = height * (16.0f / 9.0f); // Respect the 16:9 ratio that most videos have
        if (DEBUG) Log.d(TAG, "getMinimumVideoWidth() called with: height = [" + height + "], returned: " + width);
        return width;
    }

    private void updateScreenSize() {
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);

        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        if (DEBUG) Log.d(TAG, "updateScreenSize() called > screenWidth = " + screenWidth + ", screenHeight = " + screenHeight);
    }

    private void seekBy(int milliSeconds) {
        if (emVideoView == null) return;
        int progress = emVideoView.getCurrentPosition() + milliSeconds;
        emVideoView.seekTo(progress);
    }

    private void playVideo(Uri videoURI) {
        if (DEBUG) Log.d(TAG, "playVideo() called with: streamUri = [" + streamUri + "]");

        changeState(STATE_LOADING);

        windowLayoutParams.width = (int) getMinimumVideoWidth(currentPopupHeight);
        windowManager.updateViewLayout(viewHolder.getRootView(), windowLayoutParams);

        if (videoURI == null || emVideoView == null || viewHolder.getRootView() == null) {
            Toast.makeText(this, "Failed to play this video", Toast.LENGTH_SHORT).show();
            stopSelf();
            return;
        }
        if (emVideoView.isPlaying()) emVideoView.stopPlayback();
        emVideoView.setVideoURI(videoURI);

        notBuilder = createNotification();
        startForeground(NOTIFICATION_ID, notBuilder.build());
        notificationManager.notify(NOTIFICATION_ID, this.notBuilder.build());
    }

    private NotificationCompat.Builder createNotification() {
        notRemoteView = new RemoteViews(BuildConfig.APPLICATION_ID, R.layout.player_popup_notification);
        if (videoThumbnail != null) notRemoteView.setImageViewBitmap(R.id.notificationCover, videoThumbnail);
        else notRemoteView.setImageViewResource(R.id.notificationCover, R.drawable.dummy_thumbnail);
        notRemoteView.setOnClickPendingIntent(R.id.notificationPlayPause,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(InternalListener.ACTION_PLAY_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT));
        notRemoteView.setOnClickPendingIntent(R.id.notificationStop,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(InternalListener.ACTION_CLOSE), PendingIntent.FLAG_UPDATE_CURRENT));
        notRemoteView.setTextViewText(R.id.notificationSongName, videoTitle);
        notRemoteView.setTextViewText(R.id.notificationArtist, channelName);
        notRemoteView.setOnClickPendingIntent(R.id.notificationContent,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(InternalListener.ACTION_OPEN_DETAIL), PendingIntent.FLAG_UPDATE_CURRENT));

        return new NotificationCompat.Builder(this)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_play_arrow_white_48dp)
                .setContent(notRemoteView);
    }

    /**
     * Updates the notification, and the play/pause button in it.
     * Used for changes on the remoteView
     *
     * @param drawableId if != -1, sets the drawable with that id on the play/pause button
     */
    private void updateNotification(int drawableId) {
        if (DEBUG) Log.d(TAG, "updateNotification() called with: drawableId = [" + drawableId + "]");
        if (notBuilder == null || notRemoteView == null) return;
        if (drawableId != -1) notRemoteView.setImageViewResource(R.id.notificationPlayPause, drawableId);
        notificationManager.notify(NOTIFICATION_ID, notBuilder.build());
    }

    /**
     * Show a animation, and depending on goneOnEnd, will stay on the screen or be gone
     *
     * @param drawableId the drawable that will be used to animate, pass -1 to clear any animation that is visible
     * @param goneOnEnd  will set the animation view to GONE on the end of the animation
     */
    private void showAndAnimateControl(final int drawableId, final boolean goneOnEnd) {
        if (DEBUG) Log.d(TAG, "showAndAnimateControl() called with: drawableId = [" + drawableId + "], goneOnEnd = [" + goneOnEnd + "]");
        if (controlViewAnimator != null && controlViewAnimator.isRunning()) {
            if (DEBUG) Log.d(TAG, "showAndAnimateControl: controlViewAnimator.isRunning");
            controlViewAnimator.end();
        }

        if (drawableId == -1) {
            if (viewHolder.getControlAnimationView().getVisibility() == View.VISIBLE) {
                controlViewAnimator = ObjectAnimator.ofPropertyValuesHolder(viewHolder.getControlAnimationView(),
                        PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0f),
                        PropertyValuesHolder.ofFloat(View.SCALE_X, 1.4f, 1f),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.4f, 1f)
                ).setDuration(300);
                controlViewAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        viewHolder.getControlAnimationView().setVisibility(View.GONE);
                    }
                });
                controlViewAnimator.start();
            }
            return;
        }

        float scaleFrom = goneOnEnd ? 1f : 1f, scaleTo = goneOnEnd ? 1.8f : 1.4f;
        float alphaFrom = goneOnEnd ? 1f : 0f, alphaTo = goneOnEnd ? 0f : 1f;


        controlViewAnimator = ObjectAnimator.ofPropertyValuesHolder(viewHolder.getControlAnimationView(),
                PropertyValuesHolder.ofFloat(View.ALPHA, alphaFrom, alphaTo),
                PropertyValuesHolder.ofFloat(View.SCALE_X, scaleFrom, scaleTo),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, scaleFrom, scaleTo)
        );
        controlViewAnimator.setDuration(goneOnEnd ? 1000 : 500);
        controlViewAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (goneOnEnd) viewHolder.getControlAnimationView().setVisibility(View.GONE);
                else viewHolder.getControlAnimationView().setVisibility(View.VISIBLE);
            }
        });


        viewHolder.getControlAnimationView().setVisibility(View.VISIBLE);
        viewHolder.getControlAnimationView().setImageDrawable(ContextCompat.getDrawable(PopupVideoPlayer.this, drawableId));
        controlViewAnimator.start();
    }

    /**
     * Animate the view
     *
     * @param enterOrExit true to enter, false to exit
     * @param duration    how long the animation will take, in milliseconds
     * @param delay       how long the animation will wait to start, in milliseconds
     */
    private void animateView(final View view, final boolean enterOrExit, long duration, long delay) {
        if (DEBUG) Log.d(TAG, "animateView() called with: view = [" + view + "], enterOrExit = [" + enterOrExit + "], duration = [" + duration + "], delay = [" + delay + "]");
        if (view.getVisibility() == View.VISIBLE && enterOrExit) {
            if (DEBUG) Log.d(TAG, "animateLoadingPanel() > view.getVisibility() == View.VISIBLE && enterOrExit");
            view.animate().setListener(null).cancel();
            view.setVisibility(View.VISIBLE);
            return;
        }

        view.animate().setListener(null).cancel();
        view.setVisibility(View.VISIBLE);

        if (view == viewHolder.getControlsRoot()) {
            if (enterOrExit) {
                view.setAlpha(0f);
                view.animate().alpha(1f).setDuration(duration).setStartDelay(delay).setListener(null).start();
            } else {
                view.setAlpha(1f);
                view.animate().alpha(0f)
                        .setDuration(duration).setStartDelay(delay)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                view.setVisibility(View.GONE);
                            }
                        })
                        .start();
            }
            return;
        }

        if (enterOrExit) {
            view.setAlpha(0f);
            view.setScaleX(.8f);
            view.setScaleY(.8f);
            view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(duration).setStartDelay(delay).setListener(null).start();
        } else {
            view.setAlpha(1f);
            view.setScaleX(1f);
            view.setScaleY(1f);
            view.animate().alpha(0f).scaleX(.8f).scaleY(.8f).setDuration(duration).setStartDelay(delay)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            view.setVisibility(View.GONE);
                        }
                    })
                    .start();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        updateScreenSize();
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy() called");
        stopForeground(true);
        if (emVideoView != null) emVideoView.stopPlayback();
        if (imageLoader != null) imageLoader.clearMemoryCache();
        if (viewHolder.getRootView() != null) windowManager.removeView(viewHolder.getRootView());
        if (notificationManager != null) notificationManager.cancel(NOTIFICATION_ID);
        if (progressPollRepeater != null) {
            progressPollRepeater.stop();
            progressPollRepeater.setRepeatListener(null);
        }
        if (broadcastReceiver != null) unregisterReceiver(broadcastReceiver);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    ///////////////////////////////////////////////////////////////////////////
    // States Implementation
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void changeState(int state) {
        if (DEBUG) Log.d(TAG, "changeState() called with: state = [" + state + "]");
        CURRENT_STATE = state;
        switch (state) {
            case STATE_LOADING:
                onLoading();
                break;
            case STATE_PLAYING:
                onPlaying();
                break;
            case STATE_PAUSED:
                onPaused();
                break;
            case STATE_PAUSED_SEEK:
                onPausedSeek();
                break;
            case STATE_COMPLETED:
                onCompleted();
                break;
        }
    }

    @Override
    public void onLoading() {
        if (DEBUG) Log.d(TAG, "onLoading() called");
        updateNotification(R.drawable.ic_play_arrow_white_48dp);

        showAndAnimateControl(-1, true);
        viewHolder.getPlaybackSeekBar().setEnabled(true);
        viewHolder.getPlaybackSeekBar().setProgress(0);
        viewHolder.getLoadingPanel().setBackgroundColor(Color.BLACK);
        animateView(viewHolder.getLoadingPanel(), true, 500, 0);
        viewHolder.getEndScreen().setVisibility(View.GONE);
        viewHolder.getControlsRoot().setVisibility(View.GONE);
    }

    @Override
    public void onPlaying() {
        if (DEBUG) Log.d(TAG, "onPlaying() called");
        updateNotification(R.drawable.ic_pause_white_24dp);

        showAndAnimateControl(-1, true);
        viewHolder.getLoadingPanel().setVisibility(View.GONE);
        animateView(viewHolder.getControlsRoot(), false, 500, DEFAULT_CONTROLS_HIDE_TIME);
    }

    @Override
    public void onPaused() {
        if (DEBUG) Log.d(TAG, "onPaused() called");
        updateNotification(R.drawable.ic_play_arrow_white_48dp);

        showAndAnimateControl(R.drawable.ic_play_arrow_white_48dp, false);
        animateView(viewHolder.getControlsRoot(), true, 500, 100);
        viewHolder.getLoadingPanel().setVisibility(View.GONE);
    }

    @Override
    public void onPausedSeek() {
        if (DEBUG) Log.d(TAG, "onPausedSeek() called");
        updateNotification(R.drawable.ic_play_arrow_white_48dp);

        showAndAnimateControl(-1, true);
        viewHolder.getLoadingPanel().setBackgroundColor(Color.TRANSPARENT);
        animateView(viewHolder.getLoadingPanel(), true, 300, 0);
    }

    @Override
    public void onCompleted() {
        if (DEBUG) Log.d(TAG, "onCompleted() called");
        updateNotification(R.drawable.ic_replay_white);
        showAndAnimateControl(R.drawable.ic_replay_white, false);
        animateView(viewHolder.getControlsRoot(), true, 500, 0);
        animateView(viewHolder.getEndScreen(), true, 200, 0);
        viewHolder.getLoadingPanel().setVisibility(View.GONE);
        viewHolder.getPlaybackSeekBar().setEnabled(false);
        viewHolder.getPlaybackCurrentTime().setText(viewHolder.getPlaybackEndTime().getText());
        if (videoThumbnail != null) viewHolder.getEndScreen().setImageBitmap(videoThumbnail);
    }

    /**
     * This class joins all the necessary listeners
     */
    @SuppressWarnings({"WeakerAccess"})
    public class InternalListener implements SeekBar.OnSeekBarChangeListener, OnPreparedListener, OnSeekCompletionListener, OnCompletionListener, OnErrorListener, Repeater.RepeatListener {
        public static final String ACTION_CLOSE = "org.schabi.newpipe.player.PopupVideoPlayer.CLOSE";
        public static final String ACTION_PLAY_PAUSE = "org.schabi.newpipe.player.PopupVideoPlayer.PLAY_PAUSE";
        public static final String ACTION_OPEN_DETAIL = "org.schabi.newpipe.player.PopupVideoPlayer.OPEN_DETAIL";
        public static final String ACTION_UPDATE_THUMB = "org.schabi.newpipe.player.PopupVideoPlayer.UPDATE_THUMBNAIL";

        @Override
        public void onPrepared() {
            if (DEBUG) Log.d(TAG, "onPrepared() called");
            viewHolder.getPlaybackSeekBar().setMax(emVideoView.getDuration());
            viewHolder.getPlaybackEndTime().setText(TimeFormatUtil.formatMs(emVideoView.getDuration()));

            changeState(STATE_PLAYING);
            progressPollRepeater.start();
            emVideoView.start();

        }

        public void onUpdateProgress(int currentProgress, int duration, int bufferPercent) {
            if (viewHolder.isControlsVisible() && CURRENT_STATE != STATE_PAUSED_SEEK) {
                viewHolder.getPlaybackSeekBar().setProgress(currentProgress);
                viewHolder.getPlaybackCurrentTime().setText(TimeFormatUtil.formatMs(currentProgress));
                viewHolder.getPlaybackSeekBar().setSecondaryProgress((int) (viewHolder.getPlaybackSeekBar().getMax() * ((float) bufferPercent / 100)));
            }
            if (DEBUG && bufferPercent % 10 == 0) { //Limit log
                Log.d(TAG, "updateProgress() called with: isVisible = " + viewHolder.isControlsVisible() + ", currentProgress = [" + currentProgress + "], duration = [" + duration + "], bufferPercent = [" + bufferPercent + "]");
            }
        }

        public void onOpenDetail(Context context, String videoUrl) {
            if (DEBUG) Log.d(TAG, "onOpenDetail() called with: context = [" + context + "], videoUrl = [" + videoUrl + "]");
            Intent i = new Intent(context, VideoItemDetailActivity.class);
            i.putExtra(NavStack.SERVICE_ID, 0);
            i.putExtra(NavStack.URL, videoUrl);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
            //NavStack.getInstance().openDetailActivity(context, videoUrl, 0);
        }

        public void onUpdateThumbnail(Intent intent) {
            if (DEBUG) Log.d(TAG, "onUpdateThumbnail() called");
            if (!intent.getStringExtra(VIDEO_URL).equals(videoUrl)) return;
            videoThumbnail = ActivityCommunicator.getCommunicator().backgroundPlayerThumbnail;
            if (videoThumbnail != null) notRemoteView.setImageViewBitmap(R.id.notificationCover, videoThumbnail);
            updateNotification(-1);
        }

        public void onVideoClose() {
            if (DEBUG) Log.d(TAG, "onVideoClose() called");
            stopSelf();
        }

        public void onVideoPlayPause() {
            if (DEBUG) Log.d(TAG, "onVideoPlayPause() called");
            if (CURRENT_STATE == STATE_COMPLETED) {
                changeState(STATE_LOADING);
                emVideoView.restart();
                return;
            }
            if (emVideoView.isPlaying()) {
                emVideoView.pause();
                progressPollRepeater.stop();
                internalListener.onRepeat();
                changeState(STATE_PAUSED);
            } else {
                emVideoView.start();
                progressPollRepeater.start();
                changeState(STATE_PLAYING);
            }
        }

        public void onFastRewind() {
            if (DEBUG) Log.d(TAG, "onFastRewind() called");
            seekBy(-FAST_FORWARD_REWIND_AMOUNT);
            internalListener.onRepeat();
            changeState(STATE_PAUSED_SEEK);

            showAndAnimateControl(R.drawable.ic_action_av_fast_rewind, true);
        }

        public void onFastForward() {
            if (DEBUG) Log.d(TAG, "onFastForward() called");
            seekBy(FAST_FORWARD_REWIND_AMOUNT);
            internalListener.onRepeat();
            changeState(STATE_PAUSED_SEEK);

            showAndAnimateControl(R.drawable.ic_action_av_fast_forward, true);
        }

        @Override
        public void onSeekComplete() {
            if (DEBUG) Log.d(TAG, "onSeekComplete() called");

            if (!emVideoView.isPlaying()) emVideoView.start();
            changeState(STATE_PLAYING);
            /*if (emVideoView.isPlaying()) changeState(STATE_PLAYING);
            else changeState(STATE_PAUSED);*/
        }

        @Override
        public void onCompletion() {
            if (DEBUG) Log.d(TAG, "onCompletion() called");
            changeState(STATE_COMPLETED);
            progressPollRepeater.stop();
        }

        @Override
        public boolean onError() {
            if (DEBUG) Log.d(TAG, "onError() called");
            stopSelf();
            return true;
        }

        ///////////////////////////////////////////////////////////////////////////
        // SeekBar Listener
        ///////////////////////////////////////////////////////////////////////////

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (DEBUG) Log.d(TAG, "onProgressChanged() called with: seekBar = [" + seekBar + "], progress = [" + progress + "], fromUser = [" + fromUser + "]");
            viewHolder.getPlaybackCurrentTime().setText(TimeFormatUtil.formatMs(progress));
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            if (DEBUG) Log.d(TAG, "onStartTrackingTouch() called with: seekBar = [" + seekBar + "]");

            changeState(STATE_PAUSED_SEEK);
            if (emVideoView.isPlaying()) emVideoView.pause();
            animateView(viewHolder.getControlsRoot(), true, 300, 0);
            viewHolder.getControlsRoot().setAlpha(1f);
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            if (DEBUG) Log.d(TAG, "onProgressChanged() called with: seekBar = [" + seekBar + "], progress = [" + seekBar.getProgress() + "]");
            emVideoView.seekTo(seekBar.getProgress());

        }

        ///////////////////////////////////////////////////////////////////////////
        // Repeater Listener
        ///////////////////////////////////////////////////////////////////////////

        /**
         * Don't mistake this with anything related to the player itself, it's the {@link Repeater.RepeatListener#onRepeat}
         * It's used for pool the progress of the video
         */
        @Override
        public void onRepeat() {
            onUpdateProgress(emVideoView.getCurrentPosition(), emVideoView.getDuration(), emVideoView.getBufferPercentage());
        }
    }

    private class MySimpleOnGestureListener extends GestureDetector.SimpleOnGestureListener implements View.OnTouchListener {
        private int initialPopupX, initialPopupY;
        private boolean isMoving;

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (DEBUG) Log.d(TAG, "onDoubleTap() called with: e = [" + e + "]" + "rawXy = " + e.getRawX() + ", " + e.getRawY() + ", xy = " + e.getX() + ", " + e.getY());
            if (!emVideoView.isPlaying()) return false;
            if (e.getX() > popupWidth / 2) internalListener.onFastForward();
            else internalListener.onFastRewind();
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (DEBUG) Log.d(TAG, "onSingleTapConfirmed() called with: e = [" + e + "]");
            if (emVideoView == null) return false;
            internalListener.onVideoPlayPause();
            return true;
        }


        @Override
        public boolean onDown(MotionEvent e) {
            if (DEBUG) Log.d(TAG, "onDown() called with: e = [" + e + "]");
            initialPopupX = windowLayoutParams.x;
            initialPopupY = windowLayoutParams.y;
            popupWidth = viewHolder.getRootView().getWidth();
            popupHeight = viewHolder.getRootView().getHeight();
            return false;
        }

        @Override
        public void onShowPress(MotionEvent e) {
            if (DEBUG) Log.d(TAG, "onShowPress() called with: e = [" + e + "]");
            /*viewHolder.getControlsRoot().animate().setListener(null).cancel();
            viewHolder.getControlsRoot().setAlpha(1f);
            viewHolder.getControlsRoot().setVisibility(View.VISIBLE);*/
            animateView(viewHolder.getControlsRoot(), true, 200, 0);
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            isMoving = true;
            float diffX = (int) (e2.getRawX() - e1.getRawX()), posX = (int) (initialPopupX + diffX);
            float diffY = (int) (e2.getRawY() - e1.getRawY()), posY = (int) (initialPopupY + diffY);

            if (posX > (screenWidth - popupWidth)) posX = (int) (screenWidth - popupWidth);
            else if (posX < 0) posX = 0;

            if (posY > (screenHeight - popupHeight)) posY = (int) (screenHeight - popupHeight);
            else if (posY < 0) posY = 0;

            windowLayoutParams.x = (int) posX;
            windowLayoutParams.y = (int) posY;

            if (DEBUG) Log.d(TAG, "PopupVideoPlayer.onScroll = " +
                    ", e1.getRaw = [" + e1.getRawX() + ", " + e1.getRawY() + "]" +
                    ", e2.getRaw = [" + e2.getRawX() + ", " + e2.getRawY() + "]" +
                    ", distanceXy = [" + distanceX + ", " + distanceY + "]" +
                    ", posXy = [" + posX + ", " + posY + "]" +
                    ", popupWh rootView.get wh = [" + popupWidth + " x " + popupHeight + "]");
            windowManager.updateViewLayout(viewHolder.getRootView(), windowLayoutParams);
            return true;
        }

        private void onScrollEnd() {
            if (DEBUG) Log.d(TAG, "onScrollEnd() called");
            if (viewHolder.isControlsVisible() && CURRENT_STATE == STATE_PLAYING) {
                animateView(viewHolder.getControlsRoot(), false, 300, DEFAULT_CONTROLS_HIDE_TIME);
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

    /**
     * Fetcher used if open by a link out of NewPipe
     */
    private class FetcherRunnable implements Runnable {
        private final Intent intent;
        private final Handler mainHandler;
        private final boolean printStreams = true;


        FetcherRunnable(Intent intent) {
            this.intent = intent;
            this.mainHandler = new Handler(PopupVideoPlayer.this.getMainLooper());
        }

        @Override
        public void run() {
            StreamExtractor streamExtractor;
            try {
                StreamingService service = NewPipe.getService(0);
                if (service == null) return;
                streamExtractor = service.getExtractorInstance(intent.getStringExtra(NavStack.URL));
                StreamInfo info = StreamInfo.getVideoInfo(streamExtractor);
                String defaultResolution = sharedPreferences.getString(
                        getResources().getString(R.string.default_resolution_key),
                        getResources().getString(R.string.default_resolution_value));

                String chosen = "", secondary = "", fallback = "";
                for (VideoStream item : info.video_streams) {
                    if (DEBUG && printStreams) {
                        Log.d(TAG, "StreamExtractor: current Item"
                                + ", item.resolution = " + item.resolution
                                + ", item.format = " + item.format
                                + ", item.url = " + item.url);
                    }
                    if (defaultResolution.equals(item.resolution)) {
                        if (item.format == MediaFormat.MPEG_4.id) {
                            chosen = item.url;
                            if (DEBUG)
                                Log.d(TAG, "StreamExtractor: CHOSEN item"
                                        + ", item.resolution = " + item.resolution
                                        + ", item.format = " + item.format
                                        + ", item.url = " + item.url);
                        } else if (item.format == 2) secondary = item.url;
                        else fallback = item.url;

                    }
                }

                if (!chosen.trim().isEmpty()) streamUri = Uri.parse(chosen);
                else if (!secondary.trim().isEmpty()) streamUri = Uri.parse(secondary);
                else if (!fallback.trim().isEmpty()) streamUri = Uri.parse(fallback);
                else streamUri = Uri.parse(info.video_streams.get(0).url);
                if (DEBUG && printStreams) Log.d(TAG, "StreamExtractor: chosen = " + chosen
                        + "\n, secondary = " + secondary
                        + "\n, fallback = " + fallback
                        + "\n, info.video_streams.get(0).url = " + info.video_streams.get(0).url);

                videoUrl = info.webpage_url;
                videoTitle = info.title;
                channelName = info.uploader;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        playVideo(streamUri);
                    }
                });
                imageLoader.loadImage(info.thumbnail_url, displayImageOptions, new SimpleImageLoadingListener() {
                    @Override
                    public void onLoadingComplete(String imageUri, View view, final Bitmap loadedImage) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                videoThumbnail = loadedImage;
                                if (videoThumbnail != null) notRemoteView.setImageViewBitmap(R.id.notificationCover, videoThumbnail);
                                updateNotification(-1);
                            }
                        });
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}