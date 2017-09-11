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
import android.support.v4.app.NotificationCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.PopupMenu;
import android.widget.RemoteViews;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.Player;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.ReCaptchaActivity;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.services.youtube.YoutubeStreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.old.PlayVideoActivity;
import org.schabi.newpipe.player.playback.PlaybackManager;
import org.schabi.newpipe.playlist.PlayQueueItem;
import org.schabi.newpipe.playlist.SinglePlayQueue;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ThemeHelper;

import java.io.IOException;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

import static org.schabi.newpipe.util.AnimationUtils.animateView;

/**
 * Service Popup Player implementing VideoPlayer
 *
 * @author mauriciocolli
 */
public class PopupVideoPlayer extends Service {
    private static final String TAG = ".PopupVideoPlayer";
    private static final boolean DEBUG = BasePlayer.DEBUG;
    private static final int SHUTDOWN_FLING_VELOCITY = 10000;

    private static final int NOTIFICATION_ID = 40028922;
    public static final String ACTION_CLOSE = "org.schabi.newpipe.player.PopupVideoPlayer.CLOSE";
    public static final String ACTION_PLAY_PAUSE = "org.schabi.newpipe.player.PopupVideoPlayer.PLAY_PAUSE";
    public static final String ACTION_OPEN_DETAIL = "org.schabi.newpipe.player.PopupVideoPlayer.OPEN_DETAIL";
    public static final String ACTION_REPEAT = "org.schabi.newpipe.player.PopupVideoPlayer.REPEAT";

    private static final String POPUP_SAVED_WIDTH = "popup_saved_width";
    private static final String POPUP_SAVED_X = "popup_saved_x";
    private static final String POPUP_SAVED_Y = "popup_saved_y";

    private WindowManager windowManager;
    private WindowManager.LayoutParams windowLayoutParams;
    private GestureDetector gestureDetector;

    private float screenWidth, screenHeight;
    private float popupWidth, popupHeight;

    private float minimumWidth, minimumHeight;
    private float maximumWidth, maximumHeight;

    private final String setAlphaMethodName = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) ? "setImageAlpha" : "setAlpha";
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notBuilder;
    private RemoteViews notRemoteView;

    private VideoPlayerImpl playerImpl;
    private Disposable currentWorker;

    /*//////////////////////////////////////////////////////////////////////////
    // Service LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        notificationManager = ((NotificationManager) getSystemService(NOTIFICATION_SERVICE));

        playerImpl = new VideoPlayerImpl();
        ThemeHelper.setTheme(this);
    }

    @Override
    @SuppressWarnings("unchecked")
    public int onStartCommand(final Intent intent, int flags, int startId) {
        if (DEBUG)
            Log.d(TAG, "onStartCommand() called with: intent = [" + intent + "], flags = [" + flags + "], startId = [" + startId + "]");
        if (playerImpl.getPlayer() == null) initPopup();
        if (!playerImpl.isPlaying()) playerImpl.getPlayer().setPlayWhenReady(true);

        if (intent.getStringExtra(Constants.KEY_URL) != null) {
            final int serviceId = intent.getIntExtra(Constants.KEY_SERVICE_ID, 0);
            final String url = intent.getStringExtra(Constants.KEY_URL);

            playerImpl.setStartedFromNewPipe(false);

            final FetcherHandler fetcherRunnable = new FetcherHandler(this, serviceId, url);
            currentWorker = ExtractorHelper.getStreamInfo(serviceId,url,false)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Consumer<StreamInfo>() {
                        @Override
                        public void accept(@NonNull StreamInfo info) throws Exception {
                            fetcherRunnable.onReceive(info);
                        }
                    }, new Consumer<Throwable>() {
                        @Override
                        public void accept(@NonNull Throwable throwable) throws Exception {
                            fetcherRunnable.onError(throwable);
                        }
                    });
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
        stopForeground(true);
        if (playerImpl != null) {
            playerImpl.destroy();
            if (playerImpl.getRootView() != null) windowManager.removeView(playerImpl.getRootView());
        }
        if (notificationManager != null) notificationManager.cancel(NOTIFICATION_ID);
        if (currentWorker != null) currentWorker.dispose();
        savePositionAndSize();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    @SuppressLint("RtlHardcoded")
    private void initPopup() {
        if (DEBUG) Log.d(TAG, "initPopup() called");
        View rootView = View.inflate(this, R.layout.player_popup, null);
        playerImpl.setup(rootView);

        updateScreenSize();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean popupRememberSizeAndPos = sharedPreferences.getBoolean(getString(R.string.popup_remember_size_pos_key), true);

        float defaultSize = getResources().getDimension(R.dimen.popup_default_width);
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
        //gestureDetector.setIsLongpressEnabled(false);
        rootView.setOnTouchListener(listener);
        playerImpl.getLoadingPanel().setMinimumWidth(windowLayoutParams.width);
        playerImpl.getLoadingPanel().setMinimumHeight(windowLayoutParams.height);
        windowManager.addView(rootView, windowLayoutParams);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Notification
    //////////////////////////////////////////////////////////////////////////*/

    private NotificationCompat.Builder createNotification() {
        notRemoteView = new RemoteViews(BuildConfig.APPLICATION_ID, R.layout.player_popup_notification);

        notRemoteView.setTextViewText(R.id.notificationSongName, playerImpl.getVideoTitle());
        notRemoteView.setTextViewText(R.id.notificationArtist, playerImpl.getUploaderName());

        notRemoteView.setOnClickPendingIntent(R.id.notificationPlayPause,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_PLAY_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT));
        notRemoteView.setOnClickPendingIntent(R.id.notificationStop,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_CLOSE), PendingIntent.FLAG_UPDATE_CURRENT));
        notRemoteView.setOnClickPendingIntent(R.id.notificationContent,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_OPEN_DETAIL), PendingIntent.FLAG_UPDATE_CURRENT));
        notRemoteView.setOnClickPendingIntent(R.id.notificationRepeat,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_REPEAT), PendingIntent.FLAG_UPDATE_CURRENT));

        switch (playerImpl.simpleExoPlayer.getRepeatMode()) {
            case Player.REPEAT_MODE_OFF:
                notRemoteView.setInt(R.id.notificationRepeat, setAlphaMethodName, 77);
                break;
            case Player.REPEAT_MODE_ONE:
                //todo change image
                notRemoteView.setInt(R.id.notificationRepeat, setAlphaMethodName, 168);
                break;
            case Player.REPEAT_MODE_ALL:
                notRemoteView.setInt(R.id.notificationRepeat, setAlphaMethodName, 255);
                break;
        }

        return new NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_play_arrow_white)
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

    public void onVideoClose() {
        if (DEBUG) Log.d(TAG, "onVideoClose() called");
        stopSelf();
    }

    public void onOpenDetail(Context context, String videoUrl, String videoTitle) {
        if (DEBUG) Log.d(TAG, "onOpenDetail() called with: context = [" + context + "], videoUrl = [" + videoUrl + "]");
        Intent i = new Intent(context, MainActivity.class);
        i.putExtra(Constants.KEY_SERVICE_ID, 0);
        i.putExtra(Constants.KEY_URL, videoUrl);
        i.putExtra(Constants.KEY_TITLE, videoTitle);
        i.putExtra(Constants.KEY_LINK_TYPE, StreamingService.LinkType.STREAM);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(i);
        context.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
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
        //if (DEBUG) Log.d(TAG, "updatePopupSize() called with: width = [" + width + "], height = [" + height + "]");

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

    ///////////////////////////////////////////////////////////////////////////

    private class VideoPlayerImpl extends VideoPlayer {
        private TextView resizingIndicator;

        VideoPlayerImpl() {
            super("VideoPlayerImpl" + PopupVideoPlayer.TAG, PopupVideoPlayer.this);
        }

        @Override
        public void initViews(View rootView) {
            super.initViews(rootView);
            resizingIndicator = rootView.findViewById(R.id.resizing_indicator);
        }

        @Override
        public void destroy() {
            super.destroy();
            if (notRemoteView != null) notRemoteView.setImageViewBitmap(R.id.notificationCover, null);
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
            Intent intent;
            if (!getSharedPreferences().getBoolean(getResources().getString(R.string.use_old_player_key), false)) {
                intent = NavigationHelper.getOpenVideoPlayerIntent(context, MainVideoPlayer.class, this);
                if (!isStartedFromNewPipe()) intent.putExtra(VideoPlayer.STARTED_FROM_NEWPIPE, false);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            } else {
                intent = new Intent(PopupVideoPlayer.this, PlayVideoActivity.class)
                        .putExtra(PlayVideoActivity.VIDEO_TITLE, getVideoTitle())
                        .putExtra(PlayVideoActivity.STREAM_URL, getSelectedStreamUri().toString())
                        .putExtra(PlayVideoActivity.VIDEO_URL, getVideoUrl())
                        .putExtra(PlayVideoActivity.START_POSITION, Math.round(getPlayer().getCurrentPosition() / 1000f));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
            destroyPlayer();
            stopSelf();
        }

        @Override
        public void onRepeatClicked() {
            super.onRepeatClicked();
            switch (simpleExoPlayer.getRepeatMode()) {
                case Player.REPEAT_MODE_OFF:
                    // Drawable didn't work on low API :/
                    //notRemoteView.setImageViewResource(R.id.notificationRepeat, R.drawable.ic_repeat_disabled_white);
                    // Set the icon to 30% opacity - 255 (max) * .3
                    notRemoteView.setInt(R.id.notificationRepeat, setAlphaMethodName, 77);
                    break;
                case Player.REPEAT_MODE_ONE:
                    // todo change image
                    notRemoteView.setInt(R.id.notificationRepeat, setAlphaMethodName, 168);
                    break;
                case Player.REPEAT_MODE_ALL:
                    notRemoteView.setInt(R.id.notificationRepeat, setAlphaMethodName, 255);
                    break;
            }
            updateNotification(-1);
        }

        @Override
        public void onDismiss(PopupMenu menu) {
            super.onDismiss(menu);
            if (isPlaying()) hideControls(500, 0);
        }

        @Override
        public void onError(Exception exception) {
            exception.printStackTrace();
            Toast.makeText(context, "Failed to play this video", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            super.onStopTrackingTouch(seekBar);
            if (wasPlaying()) {
                hideControls(100, 0);
            }
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Playback Listener
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        public void shutdown() {
            super.shutdown();
            stopSelf();
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
            intentFilter.addAction(ACTION_OPEN_DETAIL);
            intentFilter.addAction(ACTION_REPEAT);
        }

        @Override
        public void onBroadcastReceived(Intent intent) {
            super.onBroadcastReceived(intent);
            if (DEBUG) Log.d(TAG, "onBroadcastReceived() called with: intent = [" + intent + "]");
            switch (intent.getAction()) {
                case ACTION_CLOSE:
                    onVideoClose();
                    break;
                case ACTION_PLAY_PAUSE:
                    onVideoPlayPause();
                    break;
                case ACTION_OPEN_DETAIL:
                    onOpenDetail(PopupVideoPlayer.this, getVideoUrl(), getVideoTitle());
                    break;
                case ACTION_REPEAT:
                    onRepeatClicked();
                    break;
            }
        }
        /*//////////////////////////////////////////////////////////////////////////
        // States
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        public void onLoading() {
            super.onLoading();
            updateNotification(R.drawable.ic_play_arrow_white);
        }

        @Override
        public void onPlaying() {
            super.onPlaying();
            updateNotification(R.drawable.ic_pause_white);
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
        }


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
            if (!playerImpl.isPlaying() || !playerImpl.isPlayerReady()) return false;

            if (e.getX() > popupWidth / 2) {
                //playerImpl.onFastForward();
                playerImpl.playQueue.offsetIndex(+1);
            } else {
                //playerImpl.onFastRewind();
                playerImpl.playQueue.offsetIndex(-1);
            }

            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (DEBUG) Log.d(TAG, "onSingleTapConfirmed() called with: e = [" + e + "]");
            if (playerImpl.getPlayer() == null) return false;
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
            if (isResizing) return super.onScroll(e1, e2, distanceX, distanceY);

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
            if (playerImpl.isControlsVisible() && playerImpl.getCurrentState() == BasePlayer.STATE_PLAYING) {
                playerImpl.hideControls(300, VideoPlayer.DEFAULT_CONTROLS_HIDE_TIME);
            }
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (Math.abs(velocityX) > SHUTDOWN_FLING_VELOCITY) {
                if (DEBUG) Log.d(TAG, "Popup close fling velocity= " + velocityX);
                onVideoClose();
                return true;
            }
            return false;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            gestureDetector.onTouchEvent(event);
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

        FetcherHandler(Context context, int serviceId, String url) {
            this.mainHandler = new Handler(PopupVideoPlayer.this.getMainLooper());
            this.context = context;
            this.url = url;
            this.serviceId = serviceId;
        }

        public void onReceive(final StreamInfo info) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    playerImpl.playQueue = new SinglePlayQueue(info, PlayQueueItem.DEFAULT_QUALITY);
                    playerImpl.playQueue.init();
                    playerImpl.playbackManager = new PlaybackManager(playerImpl, playerImpl.playQueue);
                }
            });
        }

        protected void onError(final Throwable exception) {
            if (DEBUG) Log.d(TAG, "onError() called with: exception = [" + exception + "]");
            exception.printStackTrace();
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
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
                }
            });
            stopSelf();
        }

        public void onReCaptchaException() {
            Toast.makeText(context, R.string.recaptcha_request_toast, Toast.LENGTH_LONG).show();
            // Starting ReCaptcha Challenge Activity
            Intent intent = new Intent(context, ReCaptchaActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            stopSelf();
        }
    }

}