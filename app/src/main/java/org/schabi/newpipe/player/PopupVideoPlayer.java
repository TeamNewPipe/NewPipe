/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * PopupVideoPlayer.java is part of NewPipe
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

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.RemoteViews;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.ReCaptchaActivity;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.services.youtube.YoutubeStreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.player.event.PlayerEventListener;
import org.schabi.newpipe.player.helper.LockManager;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.old.PlayVideoActivity;
import org.schabi.newpipe.playlist.PlayQueueItem;
import org.schabi.newpipe.playlist.SinglePlayQueue;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ThemeHelper;

import java.io.IOException;
import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import static org.schabi.newpipe.player.helper.PlayerHelper.isUsingOldPlayer;
import static org.schabi.newpipe.util.AnimationUtils.animateView;

/**
 * Service Popup Player implementing VideoPlayer
 *
 * @author mauriciocolli
 */
public final class PopupVideoPlayer extends Service {
    private static final String TAG = ".PopupVideoPlayer";
    private static final boolean DEBUG = BasePlayer.DEBUG;

    private static final int NOTIFICATION_ID = 40028922;
    public static final String ACTION_CLOSE = "org.schabi.newpipe.player.PopupVideoPlayer.CLOSE";
    public static final String ACTION_PLAY_PAUSE = "org.schabi.newpipe.player.PopupVideoPlayer.PLAY_PAUSE";
    public static final String ACTION_REPEAT = "org.schabi.newpipe.player.PopupVideoPlayer.REPEAT";

    private static final String POPUP_SAVED_WIDTH = "popup_saved_width";
    private static final String POPUP_SAVED_X = "popup_saved_x";
    private static final String POPUP_SAVED_Y = "popup_saved_y";

    private WindowManager windowManager;
    private WindowManager.LayoutParams windowLayoutParams;
    private GestureDetector gestureDetector;

    private int shutdownFlingVelocity;
    private int tossFlingVelocity;

    private float screenWidth, screenHeight;
    private float popupWidth, popupHeight;

    private float minimumWidth, minimumHeight;
    private float maximumWidth, maximumHeight;

    private NotificationManager notificationManager;
    private NotificationCompat.Builder notBuilder;
    private RemoteViews notRemoteView;

    private VideoPlayerImpl playerImpl;
    private Disposable currentWorker;
    private LockManager lockManager;
    /*//////////////////////////////////////////////////////////////////////////
    // Service-Activity Binder
    //////////////////////////////////////////////////////////////////////////*/

    private PlayerEventListener activityListener;
    private IBinder mBinder;

    /*//////////////////////////////////////////////////////////////////////////
    // Service LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        notificationManager = ((NotificationManager) getSystemService(NOTIFICATION_SERVICE));

        lockManager = new LockManager(this);
        playerImpl = new VideoPlayerImpl(this);
        ThemeHelper.setTheme(this);

        mBinder = new PlayerServiceBinder(playerImpl);
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        if (DEBUG)
            Log.d(TAG, "onStartCommand() called with: intent = [" + intent + "], flags = [" + flags + "], startId = [" + startId + "]");
        if (playerImpl.getPlayer() == null) initPopup();
        if (!playerImpl.isPlaying()) playerImpl.getPlayer().setPlayWhenReady(true);

        if (intent != null && intent.getStringExtra(Constants.KEY_URL) != null) {
            final int serviceId = intent.getIntExtra(Constants.KEY_SERVICE_ID, 0);
            final String url = intent.getStringExtra(Constants.KEY_URL);

            playerImpl.setStartedFromNewPipe(false);

            final FetcherHandler fetcherRunnable = new FetcherHandler(this, serviceId, url);
            currentWorker = ExtractorHelper.getStreamInfo(serviceId,url,false)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(fetcherRunnable::onReceive, fetcherRunnable::onError);
        } else {
            playerImpl.setStartedFromNewPipe(true);
            playerImpl.handleIntent(intent);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        updateScreenSize();
        updatePopupSize(windowLayoutParams.width, -1);
        checkPositionBounds();
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy() called");
        onClose();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    @SuppressLint("RtlHardcoded")
    private void initPopup() {
        if (DEBUG) Log.d(TAG, "initPopup() called");
        View rootView = View.inflate(this, R.layout.player_popup, null);
        playerImpl.setup(rootView);

        shutdownFlingVelocity = PlayerHelper.getShutdownFlingVelocity(this);
        tossFlingVelocity = PlayerHelper.getTossFlingVelocity(this);

        updateScreenSize();

        final boolean popupRememberSizeAndPos = PlayerHelper.isRememberingPopupDimensions(this);
        final float defaultSize = getResources().getDimension(R.dimen.popup_default_width);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        popupWidth = popupRememberSizeAndPos ? sharedPreferences.getFloat(POPUP_SAVED_WIDTH, defaultSize) : defaultSize;

        final int layoutParamType = Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_PHONE : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

        windowLayoutParams = new WindowManager.LayoutParams(
                (int) popupWidth, (int) getMinimumVideoHeight(popupWidth),
                layoutParamType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        windowLayoutParams.gravity = Gravity.LEFT | Gravity.TOP;

        int centerX = (int) (screenWidth / 2f - popupWidth / 2f);
        int centerY = (int) (screenHeight / 2f - popupHeight / 2f);
        windowLayoutParams.x = popupRememberSizeAndPos ? sharedPreferences.getInt(POPUP_SAVED_X, centerX) : centerX;
        windowLayoutParams.y = popupRememberSizeAndPos ? sharedPreferences.getInt(POPUP_SAVED_Y, centerY) : centerY;

        checkPositionBounds();

        MySimpleOnGestureListener listener = new MySimpleOnGestureListener();
        gestureDetector = new GestureDetector(this, listener);
        rootView.setOnTouchListener(listener);
        playerImpl.getLoadingPanel().setMinimumWidth(windowLayoutParams.width);
        playerImpl.getLoadingPanel().setMinimumHeight(windowLayoutParams.height);
        windowManager.addView(rootView, windowLayoutParams);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Notification
    //////////////////////////////////////////////////////////////////////////*/

    private void resetNotification() {
        notBuilder = createNotification();
    }

    private NotificationCompat.Builder createNotification() {
        notRemoteView = new RemoteViews(BuildConfig.APPLICATION_ID, R.layout.player_popup_notification);

        notRemoteView.setTextViewText(R.id.notificationSongName, playerImpl.getVideoTitle());
        notRemoteView.setTextViewText(R.id.notificationArtist, playerImpl.getUploaderName());

        notRemoteView.setOnClickPendingIntent(R.id.notificationPlayPause,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_PLAY_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT));
        notRemoteView.setOnClickPendingIntent(R.id.notificationStop,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_CLOSE), PendingIntent.FLAG_UPDATE_CURRENT));
        notRemoteView.setOnClickPendingIntent(R.id.notificationRepeat,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_REPEAT), PendingIntent.FLAG_UPDATE_CURRENT));

        // Starts popup player activity -- attempts to unlock lockscreen
        final Intent intent = NavigationHelper.getPopupPlayerActivityIntent(this);
        notRemoteView.setOnClickPendingIntent(R.id.notificationContent,
                PendingIntent.getActivity(this, NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT));

        setRepeatModeRemote(notRemoteView, playerImpl.getRepeatMode());

        return new NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
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

    /*//////////////////////////////////////////////////////////////////////////
    // Misc
    //////////////////////////////////////////////////////////////////////////*/

    public void onClose() {
        if (DEBUG) Log.d(TAG, "onClose() called");

        if (playerImpl != null) {
            if (playerImpl.getRootView() != null) {
                windowManager.removeView(playerImpl.getRootView());
                playerImpl.setRootView(null);
            }
            playerImpl.stopActivityBinding();
            playerImpl.destroy();
        }
        if (lockManager != null) lockManager.releaseWifiAndCpu();
        if (notificationManager != null) notificationManager.cancel(NOTIFICATION_ID);
        if (currentWorker != null) currentWorker.dispose();
        mBinder = null;
        playerImpl = null;

        stopForeground(true);
        stopSelf();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void checkPositionBounds() {
        if (windowLayoutParams.x > screenWidth - windowLayoutParams.width)
            windowLayoutParams.x = (int) (screenWidth - windowLayoutParams.width);
        if (windowLayoutParams.x < 0) windowLayoutParams.x = 0;
        if (windowLayoutParams.y > screenHeight - windowLayoutParams.height)
            windowLayoutParams.y = (int) (screenHeight - windowLayoutParams.height);
        if (windowLayoutParams.y < 0) windowLayoutParams.y = 0;
    }

    private void savePositionAndSize() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(PopupVideoPlayer.this);
        sharedPreferences.edit().putInt(POPUP_SAVED_X, windowLayoutParams.x).apply();
        sharedPreferences.edit().putInt(POPUP_SAVED_Y, windowLayoutParams.y).apply();
        sharedPreferences.edit().putFloat(POPUP_SAVED_WIDTH, windowLayoutParams.width).apply();
    }

    private float getMinimumVideoHeight(float width) {
        //if (DEBUG) Log.d(TAG, "getMinimumVideoHeight() called with: width = [" + width + "], returned: " + height);
        return width / (16.0f / 9.0f); // Respect the 16:9 ratio that most videos have
    }

    private void updateScreenSize() {
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);

        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        if (DEBUG) Log.d(TAG, "updateScreenSize() called > screenWidth = " + screenWidth + ", screenHeight = " + screenHeight);

        popupWidth = getResources().getDimension(R.dimen.popup_default_width);
        popupHeight = getMinimumVideoHeight(popupWidth);

        minimumWidth = getResources().getDimension(R.dimen.popup_minimum_width);
        minimumHeight = getMinimumVideoHeight(minimumWidth);

        maximumWidth = screenWidth;
        maximumHeight = screenHeight;
    }

    private void updatePopupSize(int width, int height) {
        if (playerImpl == null) return;
        if (DEBUG) Log.d(TAG, "updatePopupSize() called with: width = [" + width + "], height = [" + height + "]");

        width = (int) (width > maximumWidth ? maximumWidth : width < minimumWidth ? minimumWidth : width);

        if (height == -1) height = (int) getMinimumVideoHeight(width);
        else height = (int) (height > maximumHeight ? maximumHeight : height < minimumHeight ? minimumHeight : height);

        windowLayoutParams.width = width;
        windowLayoutParams.height = height;
        popupWidth = width;
        popupHeight = height;

        if (DEBUG) Log.d(TAG, "updatePopupSize() updated values:  width = [" + width + "], height = [" + height + "]");
        windowManager.updateViewLayout(playerImpl.getRootView(), windowLayoutParams);
    }

    protected void setRepeatModeRemote(final RemoteViews remoteViews, final int repeatMode) {
        final String methodName = "setImageResource";

        if (remoteViews == null) return;

        switch (repeatMode) {
            case Player.REPEAT_MODE_OFF:
                remoteViews.setInt(R.id.notificationRepeat, methodName, R.drawable.exo_controls_repeat_off);
                break;
            case Player.REPEAT_MODE_ONE:
                remoteViews.setInt(R.id.notificationRepeat, methodName, R.drawable.exo_controls_repeat_one);
                break;
            case Player.REPEAT_MODE_ALL:
                remoteViews.setInt(R.id.notificationRepeat, methodName, R.drawable.exo_controls_repeat_all);
                break;
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    protected class VideoPlayerImpl extends VideoPlayer {
        private TextView resizingIndicator;
        private ImageButton fullScreenButton;

        @Override
        public void handleIntent(Intent intent) {
            super.handleIntent(intent);

            resetNotification();
            startForeground(NOTIFICATION_ID, notBuilder.build());
        }

        VideoPlayerImpl(final Context context) {
            super("VideoPlayerImpl" + PopupVideoPlayer.TAG, context);
        }

        @Override
        public void initViews(View rootView) {
            super.initViews(rootView);
            resizingIndicator = rootView.findViewById(R.id.resizing_indicator);
            fullScreenButton = rootView.findViewById(R.id.fullScreenButton);
            fullScreenButton.setOnClickListener(v -> onFullScreenButtonClicked());
        }

        @Override
        public void destroy() {
            if (notRemoteView != null) notRemoteView.setImageViewBitmap(R.id.notificationCover, null);
            super.destroy();
        }

        @Override
        public void onThumbnailReceived(Bitmap thumbnail) {
            super.onThumbnailReceived(thumbnail);
            if (thumbnail != null) {
                // rebuild notification here since remote view does not release bitmaps, causing memory leaks
                notBuilder = createNotification();

                if (notRemoteView != null) notRemoteView.setImageViewBitmap(R.id.notificationCover, thumbnail);

                updateNotification(-1);
            }
        }

        @Override
        public void onFullScreenButtonClicked() {
            super.onFullScreenButtonClicked();

            if (DEBUG) Log.d(TAG, "onFullScreenButtonClicked() called");

            setRecovery();
            Intent intent;
            if (!isUsingOldPlayer(getApplicationContext())) {
                intent = NavigationHelper.getPlayerIntent(
                        context,
                        MainVideoPlayer.class,
                        this.getPlayQueue(),
                        this.getRepeatMode(),
                        this.getPlaybackSpeed(),
                        this.getPlaybackPitch(),
                        this.getPlaybackQuality()
                );
                if (!isStartedFromNewPipe()) intent.putExtra(VideoPlayer.STARTED_FROM_NEWPIPE, false);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            } else {
                intent = new Intent(PopupVideoPlayer.this, PlayVideoActivity.class)
                        .putExtra(PlayVideoActivity.VIDEO_TITLE, getVideoTitle())
                        .putExtra(PlayVideoActivity.STREAM_URL, getSelectedVideoStream().getUrl())
                        .putExtra(PlayVideoActivity.VIDEO_URL, getVideoUrl())
                        .putExtra(PlayVideoActivity.START_POSITION, Math.round(getPlayer().getCurrentPosition() / 1000f));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
            onClose();
        }

        @Override
        public void onDismiss(PopupMenu menu) {
            super.onDismiss(menu);
            if (isPlaying()) hideControls(500, 0);
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            super.onStopTrackingTouch(seekBar);
            if (wasPlaying()) {
                hideControls(100, 0);
            }
        }

        @Override
        public void onShuffleClicked() {
            super.onShuffleClicked();
            updatePlayback();
        }

        @Override
        public void onUpdateProgress(int currentProgress, int duration, int bufferPercent) {
            updateProgress(currentProgress, duration, bufferPercent);
            super.onUpdateProgress(currentProgress, duration, bufferPercent);
        }

        @Override
        protected int getDefaultResolutionIndex(final List<VideoStream> sortedVideos) {
            return ListHelper.getPopupDefaultResolutionIndex(context, sortedVideos);
        }

        @Override
        protected int getOverrideResolutionIndex(final List<VideoStream> sortedVideos,
                                                 final String playbackQuality) {
            return ListHelper.getPopupDefaultResolutionIndex(context, sortedVideos, playbackQuality);
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Activity Event Listener
        //////////////////////////////////////////////////////////////////////////*/

        /*package-private*/ void setActivityListener(PlayerEventListener listener) {
            activityListener = listener;
            updateMetadata();
            updatePlayback();
            triggerProgressUpdate();
        }

        /*package-private*/ void removeActivityListener(PlayerEventListener listener) {
            if (activityListener == listener) {
                activityListener = null;
            }
        }

        private void updateMetadata() {
            if (activityListener != null && currentInfo != null) {
                activityListener.onMetadataUpdate(currentInfo);
            }
        }

        private void updatePlayback() {
            if (activityListener != null && simpleExoPlayer != null && playQueue != null) {
                activityListener.onPlaybackUpdate(currentState, getRepeatMode(), playQueue.isShuffled(), simpleExoPlayer.getPlaybackParameters());
            }
        }

        private void updateProgress(int currentProgress, int duration, int bufferPercent) {
            if (activityListener != null) {
                activityListener.onProgressUpdate(currentProgress, duration, bufferPercent);
            }
        }

        private void stopActivityBinding() {
            if (activityListener != null) {
                activityListener.onServiceStopped();
                activityListener = null;
            }
        }

        /*//////////////////////////////////////////////////////////////////////////
        // ExoPlayer Video Listener
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        public void onRepeatModeChanged(int i) {
            super.onRepeatModeChanged(i);
            setRepeatModeRemote(notRemoteView, i);
            updateNotification(-1);
            updatePlayback();
        }

        @Override
        public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
            super.onPlaybackParametersChanged(playbackParameters);
            updatePlayback();
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Playback Listener
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        public void sync(@NonNull PlayQueueItem item, @Nullable StreamInfo info) {
            if (currentItem == item && currentInfo == info) return;
            super.sync(item, info);
            updateMetadata();
        }

        @Override
        public void shutdown() {
            super.shutdown();
            onClose();
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Broadcast Receiver
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        protected void setupBroadcastReceiver(IntentFilter intentFilter) {
            super.setupBroadcastReceiver(intentFilter);
            if (DEBUG) Log.d(TAG, "setupBroadcastReceiver() called with: intentFilter = [" + intentFilter + "]");
            intentFilter.addAction(ACTION_CLOSE);
            intentFilter.addAction(ACTION_PLAY_PAUSE);
            intentFilter.addAction(ACTION_REPEAT);

            intentFilter.addAction(Intent.ACTION_SCREEN_ON);
            intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        }

        @Override
        public void onBroadcastReceived(Intent intent) {
            super.onBroadcastReceived(intent);
            if (intent == null || intent.getAction() == null) return;
            if (DEBUG) Log.d(TAG, "onBroadcastReceived() called with: intent = [" + intent + "]");
            switch (intent.getAction()) {
                case ACTION_CLOSE:
                    onClose();
                    break;
                case ACTION_PLAY_PAUSE:
                    onVideoPlayPause();
                    break;
                case ACTION_REPEAT:
                    onRepeatClicked();
                    break;
                case Intent.ACTION_SCREEN_ON:
                    enableVideoRenderer(true);
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    enableVideoRenderer(false);
                    break;
            }
        }

        /*//////////////////////////////////////////////////////////////////////////
        // States
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        public void changeState(int state) {
            super.changeState(state);
            updatePlayback();
        }

        @Override
        public void onBlocked() {
            super.onBlocked();
            updateNotification(R.drawable.ic_play_arrow_white);
        }

        @Override
        public void onPlaying() {
            super.onPlaying();
            updateNotification(R.drawable.ic_pause_white);
            lockManager.acquireWifiAndCpu();
        }

        @Override
        public void onBuffering() {
            super.onBuffering();
            updateNotification(R.drawable.ic_play_arrow_white);
        }

        @Override
        public void onPaused() {
            super.onPaused();
            updateNotification(R.drawable.ic_play_arrow_white);
            showAndAnimateControl(R.drawable.ic_play_arrow_white, false);
            lockManager.releaseWifiAndCpu();
        }

        @Override
        public void onPausedSeek() {
            super.onPausedSeek();
            updateNotification(R.drawable.ic_play_arrow_white);
        }

        @Override
        public void onCompleted() {
            super.onCompleted();
            updateNotification(R.drawable.ic_replay_white);
            showAndAnimateControl(R.drawable.ic_replay_white, false);
            lockManager.releaseWifiAndCpu();
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Utils
        //////////////////////////////////////////////////////////////////////////*/

        /*package-private*/ void enableVideoRenderer(final boolean enable) {
            final int videoRendererIndex = getVideoRendererIndex();
            if (trackSelector != null && videoRendererIndex != -1) {
                trackSelector.setRendererDisabled(videoRendererIndex, !enable);
            }
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Getters
        //////////////////////////////////////////////////////////////////////////*/

        @SuppressWarnings("WeakerAccess")
        public TextView getResizingIndicator() {
            return resizingIndicator;
        }
    }

    private class MySimpleOnGestureListener extends GestureDetector.SimpleOnGestureListener implements View.OnTouchListener {
        private int initialPopupX, initialPopupY;
        private boolean isMoving;

        private boolean isResizing;

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (DEBUG)
                Log.d(TAG, "onDoubleTap() called with: e = [" + e + "]" + "rawXy = " + e.getRawX() + ", " + e.getRawY() + ", xy = " + e.getX() + ", " + e.getY());
            if (playerImpl == null || !playerImpl.isPlaying() || !playerImpl.isPlayerReady()) return false;

            if (e.getX() > popupWidth / 2) {
                playerImpl.onFastForward();
            } else {
                playerImpl.onFastRewind();
            }

            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (DEBUG) Log.d(TAG, "onSingleTapConfirmed() called with: e = [" + e + "]");
            if (playerImpl == null || playerImpl.getPlayer() == null) return false;
            playerImpl.onVideoPlayPause();
            return true;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            if (DEBUG) Log.d(TAG, "onDown() called with: e = [" + e + "]");
            initialPopupX = windowLayoutParams.x;
            initialPopupY = windowLayoutParams.y;
            popupWidth = windowLayoutParams.width;
            popupHeight = windowLayoutParams.height;
            return super.onDown(e);
        }

        @Override
        public void onLongPress(MotionEvent e) {
            if (DEBUG) Log.d(TAG, "onLongPress() called with: e = [" + e + "]");
            updateScreenSize();
            checkPositionBounds();
            updatePopupSize((int) screenWidth, -1);
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (isResizing || playerImpl == null) return super.onScroll(e1, e2, distanceX, distanceY);

            if (playerImpl.getCurrentState() != BasePlayer.STATE_BUFFERING
                    && (!isMoving || playerImpl.getControlsRoot().getAlpha() != 1f)) playerImpl.showControls(0);
            isMoving = true;

            float diffX = (int) (e2.getRawX() - e1.getRawX()), posX = (int) (initialPopupX + diffX);
            float diffY = (int) (e2.getRawY() - e1.getRawY()), posY = (int) (initialPopupY + diffY);

            if (posX > (screenWidth - popupWidth)) posX = (int) (screenWidth - popupWidth);
            else if (posX < 0) posX = 0;

            if (posY > (screenHeight - popupHeight)) posY = (int) (screenHeight - popupHeight);
            else if (posY < 0) posY = 0;

            windowLayoutParams.x = (int) posX;
            windowLayoutParams.y = (int) posY;

            //noinspection PointlessBooleanExpression
            if (DEBUG && false) Log.d(TAG, "PopupVideoPlayer.onScroll = " +
                    ", e1.getRaw = [" + e1.getRawX() + ", " + e1.getRawY() + "]" +
                    ", e2.getRaw = [" + e2.getRawX() + ", " + e2.getRawY() + "]" +
                    ", distanceXy = [" + distanceX + ", " + distanceY + "]" +
                    ", posXy = [" + posX + ", " + posY + "]" +
                    ", popupWh = [" + popupWidth + " x " + popupHeight + "]");
            windowManager.updateViewLayout(playerImpl.getRootView(), windowLayoutParams);
            return true;
        }

        private void onScrollEnd() {
            if (DEBUG) Log.d(TAG, "onScrollEnd() called");
            if (playerImpl == null) return;
            if (playerImpl.isControlsVisible() && playerImpl.getCurrentState() == BasePlayer.STATE_PLAYING) {
                playerImpl.hideControls(300, VideoPlayer.DEFAULT_CONTROLS_HIDE_TIME);
            }
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (DEBUG) Log.d(TAG, "Fling velocity: dX=[" + velocityX + "], dY=[" + velocityY + "]");
            if (playerImpl == null) return false;

            final float absVelocityX = Math.abs(velocityX);
            final float absVelocityY = Math.abs(velocityY);
            if (absVelocityX > shutdownFlingVelocity) {
                onClose();
                return true;
            } else if (Math.max(absVelocityX, absVelocityY) > tossFlingVelocity) {
                if (absVelocityX > tossFlingVelocity) windowLayoutParams.x = (int) velocityX;
                if (absVelocityY > tossFlingVelocity) windowLayoutParams.y = (int) velocityY;
                checkPositionBounds();
                windowManager.updateViewLayout(playerImpl.getRootView(), windowLayoutParams);
                return true;
            }
            return false;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            gestureDetector.onTouchEvent(event);
            if (playerImpl == null) return false;
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
                savePositionAndSize();
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
                windowLayoutParams.x = (int) (event.getRawX() - diff);
            } else {
                // first pointer is the anchor
                windowLayoutParams.x = (int) event.getRawX();
            }

            checkPositionBounds();
            updateScreenSize();

            final int width = (int) Math.min(screenWidth, diff);
            updatePopupSize(width, -1);

            return true;
        }
    }

    /**
     * Fetcher handler used if open by a link out of NewPipe
     */
    private class FetcherHandler {
        private final int serviceId;
        private final String url;

        private final Context context;
        private final Handler mainHandler;

        private FetcherHandler(Context context, int serviceId, String url) {
            this.mainHandler = new Handler(PopupVideoPlayer.this.getMainLooper());
            this.context = context;
            this.url = url;
            this.serviceId = serviceId;
        }

        private void onReceive(final StreamInfo info) {
            mainHandler.post(() -> {
                final Intent intent = NavigationHelper.getPlayerIntent(getApplicationContext(),
                        PopupVideoPlayer.class, new SinglePlayQueue(info));
                playerImpl.handleIntent(intent);
            });
        }

        private void onError(final Throwable exception) {
            if (DEBUG) Log.d(TAG, "onError() called with: exception = [" + exception + "]");
            exception.printStackTrace();
            mainHandler.post(() -> {
                if (exception instanceof ReCaptchaException) {
                    onReCaptchaException();
                } else if (exception instanceof IOException) {
                    Toast.makeText(context, R.string.network_error, Toast.LENGTH_LONG).show();
                } else if (exception instanceof YoutubeStreamExtractor.GemaException) {
                    Toast.makeText(context, R.string.blocked_by_gema, Toast.LENGTH_LONG).show();
                } else if (exception instanceof YoutubeStreamExtractor.LiveStreamException) {
                    Toast.makeText(context, R.string.live_streams_not_supported, Toast.LENGTH_LONG).show();
                } else if (exception instanceof ContentNotAvailableException) {
                    Toast.makeText(context, R.string.content_not_available, Toast.LENGTH_LONG).show();
                } else {
                    int errorId = exception instanceof YoutubeStreamExtractor.DecryptException ? R.string.youtube_signature_decryption_error :
                            exception instanceof ParsingException ? R.string.parsing_error : R.string.general_error;
                    ErrorActivity.reportError(mainHandler, context, exception, MainActivity.class, null, ErrorActivity.ErrorInfo.make(UserAction.REQUESTED_STREAM, NewPipe.getNameOfService(serviceId), url, errorId));
                }
            });
            stopSelf();
        }

        private void onReCaptchaException() {
            Toast.makeText(context, R.string.recaptcha_request_toast, Toast.LENGTH_LONG).show();
            // Starting ReCaptcha Challenge Activity
            Intent intent = new Intent(context, ReCaptchaActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            stopSelf();
        }
    }

}