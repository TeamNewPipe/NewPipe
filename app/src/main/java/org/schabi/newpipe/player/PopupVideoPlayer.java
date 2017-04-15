package org.schabi.newpipe.player;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
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
import android.widget.Toast;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;

import org.schabi.newpipe.ActivityCommunicator;
import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.ReCaptchaActivity;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream_info.StreamInfo;
import org.schabi.newpipe.extractor.stream_info.VideoStream;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ThemeHelper;
import org.schabi.newpipe.util.Utils;
import org.schabi.newpipe.workers.StreamExtractorWorker;

/**
 * Service Popup Player implementing AbstractPlayer
 *
 * @author mauriciocolli
 */
public class PopupVideoPlayer extends Service {
    private static final String TAG = ".PopupVideoPlayer";
    private static final boolean DEBUG = AbstractPlayer.DEBUG;

    private static final int NOTIFICATION_ID = 40028922;
    public static final String ACTION_CLOSE = "org.schabi.newpipe.player.PopupVideoPlayer.CLOSE";
    public static final String ACTION_PLAY_PAUSE = "org.schabi.newpipe.player.PopupVideoPlayer.PLAY_PAUSE";
    public static final String ACTION_OPEN_DETAIL = "org.schabi.newpipe.player.PopupVideoPlayer.OPEN_DETAIL";
    public static final String ACTION_REPEAT = "org.schabi.newpipe.player.PopupVideoPlayer.REPEAT";

    private BroadcastReceiver broadcastReceiver;

    private WindowManager windowManager;
    private WindowManager.LayoutParams windowLayoutParams;
    private GestureDetector gestureDetector;

    private float screenWidth, screenHeight;
    private float popupWidth, popupHeight;
    private float currentPopupHeight = 110.0f * Resources.getSystem().getDisplayMetrics().density;
    //private float minimumHeight = 100; // TODO: Use it when implementing the resize of the popup

    private final String setAlphaMethodName = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) ? "setImageAlpha" : "setAlpha";
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notBuilder;
    private RemoteViews notRemoteView;


    private ImageLoader imageLoader = ImageLoader.getInstance();
    private DisplayImageOptions displayImageOptions = new DisplayImageOptions.Builder().cacheInMemory(true).build();

    private AbstractPlayerImpl playerImpl;
    private StreamExtractorWorker currentExtractorWorker;

    /*//////////////////////////////////////////////////////////////////////////
    // Service LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        notificationManager = ((NotificationManager) getSystemService(NOTIFICATION_SERVICE));
        initReceiver();

        playerImpl = new AbstractPlayerImpl();
        ThemeHelper.setTheme(this, false);
    }

    @Override
    @SuppressWarnings("unchecked")
    public int onStartCommand(final Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "onStartCommand() called with: intent = [" + intent + "], flags = [" + flags + "], startId = [" + startId + "]");
        if (playerImpl.getPlayer() == null) initPopup();
        if (!playerImpl.isPlaying()) playerImpl.getPlayer().setPlayWhenReady(true);

        if (imageLoader != null) imageLoader.clearMemoryCache();
        if (intent.getStringExtra(Constants.KEY_URL) != null) {
            playerImpl.setStartedFromNewPipe(false);
            currentExtractorWorker = new StreamExtractorWorker(this, 0, intent.getStringExtra(Constants.KEY_URL), new FetcherRunnable(this));
            currentExtractorWorker.start();
        } else {
            playerImpl.setStartedFromNewPipe(true);
            playerImpl.handleIntent(intent);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        updateScreenSize();
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy() called");
        stopForeground(true);
        if (playerImpl != null) {
            playerImpl.destroy();
            if (playerImpl.getRootView() != null) windowManager.removeView(playerImpl.getRootView());
        }
        if (imageLoader != null) imageLoader.clearMemoryCache();
        if (notificationManager != null) notificationManager.cancel(NOTIFICATION_ID);
        if (broadcastReceiver != null) unregisterReceiver(broadcastReceiver);
        if (currentExtractorWorker != null) {
            currentExtractorWorker.cancel();
            currentExtractorWorker = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    private void initReceiver() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (DEBUG) Log.d(TAG, "onReceive() called with: context = [" + context + "], intent = [" + intent + "]");
                switch (intent.getAction()) {
                    case ACTION_CLOSE:
                        onVideoClose();
                        break;
                    case ACTION_PLAY_PAUSE:
                        playerImpl.onVideoPlayPause();
                        break;
                    case ACTION_OPEN_DETAIL:
                        onOpenDetail(PopupVideoPlayer.this, playerImpl.getVideoUrl(), playerImpl.getVideoTitle());
                        break;
                    case ACTION_REPEAT:
                        playerImpl.onRepeatClicked();
                        break;
                    case AbstractPlayer.ACTION_UPDATE_THUMB:
                        playerImpl.onUpdateThumbnail(intent);
                        break;
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_CLOSE);
        intentFilter.addAction(ACTION_PLAY_PAUSE);
        intentFilter.addAction(ACTION_OPEN_DETAIL);
        intentFilter.addAction(ACTION_REPEAT);
        intentFilter.addAction(AbstractPlayer.ACTION_UPDATE_THUMB);
        registerReceiver(broadcastReceiver, intentFilter);
    }

    @SuppressLint("RtlHardcoded")
    private void initPopup() {
        if (DEBUG) Log.d(TAG, "initPopup() called");
        View rootView = View.inflate(this, R.layout.player_popup, null);

        playerImpl.setup(rootView);

        updateScreenSize();
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
        playerImpl.getLoadingPanel().setMinimumWidth(windowLayoutParams.width);
        playerImpl.getLoadingPanel().setMinimumHeight(windowLayoutParams.height);
        windowManager.addView(rootView, windowLayoutParams);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Notification
    //////////////////////////////////////////////////////////////////////////*/

    private NotificationCompat.Builder createNotification() {
        notRemoteView = new RemoteViews(BuildConfig.APPLICATION_ID, R.layout.player_popup_notification);

        if (playerImpl.getVideoThumbnail() != null) notRemoteView.setImageViewBitmap(R.id.notificationCover, playerImpl.getVideoThumbnail());
        else notRemoteView.setImageViewResource(R.id.notificationCover, R.drawable.dummy_thumbnail);
        notRemoteView.setTextViewText(R.id.notificationSongName, playerImpl.getVideoTitle());
        notRemoteView.setTextViewText(R.id.notificationArtist, playerImpl.getChannelName());

        notRemoteView.setOnClickPendingIntent(R.id.notificationPlayPause,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_PLAY_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT));
        notRemoteView.setOnClickPendingIntent(R.id.notificationStop,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_CLOSE), PendingIntent.FLAG_UPDATE_CURRENT));
        notRemoteView.setOnClickPendingIntent(R.id.notificationContent,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_OPEN_DETAIL), PendingIntent.FLAG_UPDATE_CURRENT));
        notRemoteView.setOnClickPendingIntent(R.id.notificationRepeat,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_REPEAT), PendingIntent.FLAG_UPDATE_CURRENT));

        switch (playerImpl.getCurrentRepeatMode()) {
            case REPEAT_DISABLED:
                notRemoteView.setInt(R.id.notificationRepeat, setAlphaMethodName, 77);
                break;
            case REPEAT_ONE:
                notRemoteView.setInt(R.id.notificationRepeat, setAlphaMethodName, 255);
                break;
            case REPEAT_ALL:
                // Waiting :)
                break;
        }

        return new NotificationCompat.Builder(this)
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
        context.startActivity(i);
        context.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

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

    ///////////////////////////////////////////////////////////////////////////

    private class AbstractPlayerImpl extends AbstractPlayer {
        AbstractPlayerImpl() {
            super("AbstractPlayerImpl" + PopupVideoPlayer.TAG, PopupVideoPlayer.this);
        }

        @Override
        public void playVideo(VideoStream videoStream, boolean autoPlay) {
            super.playVideo(videoStream, autoPlay);

            windowLayoutParams.width = (int) getMinimumVideoWidth(currentPopupHeight);
            windowManager.updateViewLayout(getRootView(), windowLayoutParams);

            notBuilder = createNotification();
            startForeground(NOTIFICATION_ID, notBuilder.build());
            notificationManager.notify(NOTIFICATION_ID, notBuilder.build());
        }

        @Override
        public void onFullScreenButtonClicked() {
            if (DEBUG) Log.d(TAG, "onFullScreenButtonClicked() called");
            Intent intent;
            if (!getSharedPreferences().getBoolean(getResources().getString(R.string.use_old_player_key), false)) {
                intent = NavigationHelper.getOpenPlayerIntent(context, ExoPlayerActivity.class, playerImpl);
                if (!playerImpl.isStartedFromNewPipe()) intent.putExtra(AbstractPlayer.STARTED_FROM_NEWPIPE, false);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            } else {
                intent = new Intent(PopupVideoPlayer.this, PlayVideoActivity.class)
                        .putExtra(PlayVideoActivity.VIDEO_TITLE, getVideoTitle())
                        .putExtra(PlayVideoActivity.STREAM_URL, getSelectedStreamUri().toString())
                        .putExtra(PlayVideoActivity.VIDEO_URL, getVideoUrl())
                        .putExtra(PlayVideoActivity.START_POSITION, Math.round(getPlayer().getCurrentPosition() / 1000f));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            stopSelf();
            if (playerImpl != null) playerImpl.destroy();
            context.startActivity(intent);
        }

        @Override
        public void onRepeatClicked() {
            super.onRepeatClicked();
            switch (getCurrentRepeatMode()) {
                case REPEAT_DISABLED:
                    // Drawable didn't work on low API :/
                    //notRemoteView.setImageViewResource(R.id.notificationRepeat, R.drawable.ic_repeat_disabled_white);
                    // Set the icon to 30% opacity - 255 (max) * .3
                    notRemoteView.setInt(R.id.notificationRepeat, setAlphaMethodName, 77);
                    break;
                case REPEAT_ONE:
                    notRemoteView.setInt(R.id.notificationRepeat, setAlphaMethodName, 255);
                    break;
                case REPEAT_ALL:
                    // Waiting :)
                    break;
            }
            updateNotification(-1);
        }

        @Override
        public void onUpdateThumbnail(Intent intent) {
            super.onUpdateThumbnail(intent);
            if (getVideoThumbnail() != null) notRemoteView.setImageViewBitmap(R.id.notificationCover, getVideoThumbnail());
            updateNotification(-1);
        }

        @Override
        public void onDismiss(PopupMenu menu) {
            super.onDismiss(menu);
            if (isPlaying()) animateView(getControlsRoot(), false, 500, 0);
        }

        @Override
        public void onError() {
            Toast.makeText(context, "Failed to play this video", Toast.LENGTH_SHORT).show();
            stopSelf();
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

    }

    private class MySimpleOnGestureListener extends GestureDetector.SimpleOnGestureListener implements View.OnTouchListener {
        private int initialPopupX, initialPopupY;
        private boolean isMoving;

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (DEBUG) Log.d(TAG, "onDoubleTap() called with: e = [" + e + "]" + "rawXy = " + e.getRawX() + ", " + e.getRawY() + ", xy = " + e.getX() + ", " + e.getY());
            if (!playerImpl.isPlaying()) return false;
            if (e.getX() > popupWidth / 2) playerImpl.onFastForward();
            else playerImpl.onFastRewind();
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
            popupWidth = playerImpl.getRootView().getWidth();
            popupHeight = playerImpl.getRootView().getHeight();
            return false;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (!isMoving || playerImpl.getControlsRoot().getAlpha() != 1f) playerImpl.animateView(playerImpl.getControlsRoot(), true, 30, 0);
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
                    ", popupWh rootView.get wh = [" + popupWidth + " x " + popupHeight + "]");
            windowManager.updateViewLayout(playerImpl.getRootView(), windowLayoutParams);
            return true;
        }

        private void onScrollEnd() {
            if (DEBUG) Log.d(TAG, "onScrollEnd() called");
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

    /**
     * Fetcher used if open by a link out of NewPipe
     */
    private class FetcherRunnable implements StreamExtractorWorker.OnStreamInfoReceivedListener {
        private final Context context;
        private final Handler mainHandler;

        FetcherRunnable(Context context) {
            this.mainHandler = new Handler(PopupVideoPlayer.this.getMainLooper());
            this.context = context;
        }

        @Override
        public void onReceive(StreamInfo info) {
            playerImpl.setVideoTitle(info.title);
            playerImpl.setVideoUrl(info.webpage_url);
            playerImpl.setChannelName(info.uploader);

            playerImpl.setVideoStreamsList(Utils.getSortedStreamVideosList(context, info.video_streams, info.video_only_streams, false));
            playerImpl.setAudioStream(Utils.getHighestQualityAudio(info.audio_streams));

            int defaultResolution = Utils.getPopupDefaultResolution(context, playerImpl.getVideoStreamsList());
            playerImpl.setSelectedIndexStream(defaultResolution);

            if (DEBUG) {
                Log.d(TAG, "FetcherRunnable.StreamExtractor: chosen = "
                        + MediaFormat.getNameById(info.video_streams.get(defaultResolution).format) + " "
                        + info.video_streams.get(defaultResolution).resolution + " > "
                        + info.video_streams.get(defaultResolution).url);
            }

            if (info.start_position > 0) playerImpl.setVideoStartPos(info.start_position * 1000);
            else playerImpl.setVideoStartPos(-1);

            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    playerImpl.playVideo(playerImpl.getSelectedVideoStream(), true);
                }
            });

            imageLoader.resume();
            imageLoader.loadImage(info.thumbnail_url, displayImageOptions, new SimpleImageLoadingListener() {
                @Override
                public void onLoadingComplete(String imageUri, View view, final Bitmap loadedImage) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            playerImpl.setVideoThumbnail(loadedImage);
                            if (loadedImage != null) notRemoteView.setImageViewBitmap(R.id.notificationCover, loadedImage);
                            updateNotification(-1);
                            ActivityCommunicator.getCommunicator().backgroundPlayerThumbnail = loadedImage;
                        }
                    });
                }
            });
        }

        @Override
        public void onError(final int messageId) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, messageId, Toast.LENGTH_LONG).show();
                }
            });
            stopSelf();
        }

        @Override
        public void onReCaptchaException() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, R.string.recaptcha_request_toast, Toast.LENGTH_LONG).show();
                }
            });
            // Starting ReCaptcha Challenge Activity
            Intent intent = new Intent(context, ReCaptchaActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            stopSelf();
        }

        @Override
        public void onBlockedByGemaError() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, R.string.blocked_by_gema, Toast.LENGTH_LONG).show();
                }
            });
            stopSelf();
        }

        @Override
        public void onContentErrorWithMessage(final int messageId) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, messageId, Toast.LENGTH_LONG).show();
                }
            });
            stopSelf();
        }

        @Override
        public void onContentError() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, R.string.content_not_available, Toast.LENGTH_LONG).show();
                }
            });
            stopSelf();
        }

        @Override
        public void onUnrecoverableError(Exception exception) {
            stopSelf();
        }
    }

}