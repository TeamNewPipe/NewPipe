/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * MainPlayerService.java is part of NewPipe
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
import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;

import com.google.android.exoplayer2.source.MediaSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.ReCaptchaActivity;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.services.youtube.YoutubeStreamExtractor;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.fragments.OnScrollBelowItemsListener;
import org.schabi.newpipe.fragments.detail.VideoDetailFragment;
import org.schabi.newpipe.player.event.PlayerEventListener;
import org.schabi.newpipe.player.event.PlayerServiceEventListener;
import org.schabi.newpipe.player.helper.LockManager;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.old.PlayVideoActivity;
import org.schabi.newpipe.playlist.PlayQueueItem;
import org.schabi.newpipe.playlist.PlayQueueItemBuilder;
import org.schabi.newpipe.playlist.PlayQueueItemHolder;
import org.schabi.newpipe.playlist.SinglePlayQueue;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.AnimationUtils;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ThemeHelper;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.schabi.newpipe.player.helper.PlayerHelper.getTimeString;
import static org.schabi.newpipe.player.helper.PlayerHelper.isUsingOldPlayer;
import static org.schabi.newpipe.util.AnimationUtils.animateView;

/**
 * Player based on service implementing VideoPlayer
 *
 * @authors mauriciocolli and avently
 */
public class MainPlayerService extends Service {
    private static final String TAG = ".MainPlayerService";
    private static final boolean DEBUG = BasePlayer.DEBUG;

    private GestureDetector gestureDetector;

    private VideoPlayerImpl playerImpl;

    // Popup
    private static final String POPUP_SAVED_WIDTH = "popup_saved_width";
    private static final String POPUP_SAVED_X = "popup_saved_x";
    private static final String POPUP_SAVED_Y = "popup_saved_y";

    private Disposable currentWorker;
    private WindowManager windowManager;
    private WindowManager.LayoutParams windowLayoutParams;

    private int shutdownFlingVelocity;
    private int tossFlingVelocity;

    private float screenWidth, screenHeight;
    private float popupWidth, popupHeight;

    private float minimumWidth, minimumHeight;
    private float maximumWidth, maximumHeight;




    private PlayerServiceEventListener fragmentListener;
    private final IBinder mBinder = new MainPlayerService.LocalBinder();

    // Notification
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notBuilder;
    private RemoteViews notRemoteView;
    private RemoteViews bigNotRemoteView;
    private static final int NOTIFICATION_ID = 417308;
    public static final String ACTION_CLOSE = "org.schabi.newpipe.player.MainPlayerService.CLOSE";
    private static final String ACTION_PLAY_PAUSE = "org.schabi.newpipe.player.MainPlayerService.PLAY_PAUSE";
    private static final String ACTION_OPEN_CONTROLS = "org.schabi.newpipe.player.MainPlayerService.OPEN_CONTROLS";
    private static final String ACTION_REPEAT = "org.schabi.newpipe.player.MainPlayerService.REPEAT";
    private static final String ACTION_PLAY_NEXT = "org.schabi.newpipe.player.MainPlayerService.ACTION_PLAY_NEXT";
    private static final String ACTION_PLAY_PREVIOUS = "org.schabi.newpipe.player.MainPlayerService.ACTION_PLAY_PREVIOUS";
    private static final String ACTION_FAST_REWIND = "org.schabi.newpipe.player.MainPlayerService.ACTION_FAST_REWIND";
    private static final String ACTION_FAST_FORWARD = "org.schabi.newpipe.player.MainPlayerService.ACTION_FAST_FORWARD";

    private static final String SET_IMAGE_RESOURCE_METHOD = "setImageResource";
    private final String setAlphaMethodName = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) ? "setImageAlpha" : "setAlpha";

    private boolean shouldUpdateOnProgress;

    private LockManager lockManager;
    private PlayerEventListener activityListener;

    private SharedPreferences defaultPreferences;

    /*//////////////////////////////////////////////////////////////////////////
    // Service LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public IBinder onBind(Intent intent) {
        if(DEBUG) Log.d(TAG, "service in onBind");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if(DEBUG) Log.d(TAG, "service in onUnbind");
        return super.onUnbind(intent);
    }

    public class LocalBinder extends Binder {

        public MainPlayerService getService() {
            return MainPlayerService.this;
        }

        public VideoPlayerImpl getPlayer() {
            return MainPlayerService.this.playerImpl;
        }
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        if (DEBUG)
            Log.d(TAG, "onStartCommand() called with: intent = [" + intent + "], flags = [" + flags + "], startId = [" + startId + "]");

        // It's just a connection without action
        if(intent.getExtras() == null) return Service.START_NOT_STICKY;


        // If you want to open popup from the app just include Constants.KEY_POPUP into an extra
        playerImpl.isPopupPlayerSelected = (intent.getBooleanExtra(Constants.KEY_POPUP, false)
                || intent.getStringExtra(Constants.KEY_URL) != null)
                && !intent.getBooleanExtra(BasePlayer.AUDIO_ONLY, false);
        playerImpl.setStartedFromNewPipe(intent.getSerializableExtra(BasePlayer.PLAY_QUEUE) != null);

        if(playerImpl.popupPlayerSelected())
            initPopup();
        else
            initVideoPlayer();

        // Means we already have PlayQueue
        if(playerImpl.isStartedFromNewPipe()) {
            playerImpl.handleIntent(intent);
        }
        else {
            // We don't have PlayQueue. That's fine, download it and then we'll continue
            final int serviceId = intent.getIntExtra(Constants.KEY_SERVICE_ID, 0);
            final String url = intent.getStringExtra(Constants.KEY_URL);

            final FetcherHandler fetcherRunnable = new FetcherHandler(this, serviceId, url);
            currentWorker = ExtractorHelper.getStreamInfo(serviceId, url, false)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(fetcherRunnable::onReceive, fetcherRunnable::onError);
        }

        return Service.START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeHelper.setTheme(this);
        if(DEBUG) Log.d(TAG, "onCreate() called");
        notificationManager = ((NotificationManager) getSystemService(NOTIFICATION_SERVICE));
        lockManager = new LockManager(this);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        defaultPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        shouldUpdateOnProgress = true;

        createView();
    }

    private void createView() {
        View layout = View.inflate(this, R.layout.player_main, null);

        playerImpl = new VideoPlayerImpl(this);
        playerImpl.setup(layout);
    }

    public void stop() {
        if (DEBUG) Log.d(TAG, "stop() called");

        if (playerImpl.getPlayer() != null) {
            playerImpl.wasPlaying = playerImpl.getPlayer().getPlayWhenReady();
            playerImpl.getPlayer().setPlayWhenReady(false);
            playerImpl.setRecovery();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (DEBUG) Log.d(TAG, "onDestroy() called");

        if (notificationManager != null) notificationManager.cancel(NOTIFICATION_ID);
        if (lockManager != null) lockManager.releaseWifiAndCpu();

        if (playerImpl != null) {
            removeViewFromParent();

            playerImpl.destroy();
            playerImpl = null;
        }
        if (currentWorker != null) currentWorker.dispose();
        stopForeground(true);
        notificationManager.cancel(NOTIFICATION_ID);
        stopSelf();
    }

    private void onClose() {
        onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if(!playerImpl.popupPlayerSelected()) return;

        playerImpl.updateScreenSize();
        playerImpl.updatePopupSize(windowLayoutParams.width, -1);
        playerImpl.checkPositionBounds();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    public void toggleOrientation() {
        setLandScape(!isLandScape());
        defaultPreferences.edit()
                .putBoolean(getString(R.string.last_orientation_landscape_key), !isLandScape())
                .apply();
    }

    private boolean isLandScape() {
        return getResources().getDisplayMetrics().heightPixels < getResources().getDisplayMetrics().widthPixels;
    }

    private void setLandScape(boolean v) {
        Activity parent = playerImpl.getParentActivity();
        if(parent == null) return;

        parent.setRequestedOrientation(v
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
    }

    public long getPlaybackPosition() {
        return (playerImpl != null && playerImpl.getPlayer() != null)? playerImpl.getPlayer().getCurrentPosition() : 0;
    }

    private void setRepeatModeButton(final ImageButton imageButton, final int repeatMode) {
        switch (repeatMode) {
            case Player.REPEAT_MODE_OFF:
                imageButton.setImageResource(R.drawable.exo_controls_repeat_off);
                break;
            case Player.REPEAT_MODE_ONE:
                imageButton.setImageResource(R.drawable.exo_controls_repeat_one);
                break;
            case Player.REPEAT_MODE_ALL:
                imageButton.setImageResource(R.drawable.exo_controls_repeat_all);
                break;
        }
    }

    private void setShuffleButton(final ImageButton shuffleButton, final boolean shuffled) {
        final int shuffleAlpha = shuffled ? 255 : 77;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            shuffleButton.setImageAlpha(shuffleAlpha);
        } else {
            shuffleButton.setAlpha(shuffleAlpha);
        }
    }

    public View getView() {
        if(playerImpl == null) return null;

        return playerImpl.getRootView();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    @SuppressLint("RtlHardcoded")
    private void initPopup() {
        if (DEBUG) Log.d(TAG, "initPopup() called");

        shutdownFlingVelocity = PlayerHelper.getShutdownFlingVelocity(this);
        tossFlingVelocity = PlayerHelper.getTossFlingVelocity(this);

        playerImpl.updateScreenSize();

        final boolean popupRememberSizeAndPos = PlayerHelper.isRememberingPopupDimensions(this);
        final float defaultSize = getResources().getDimension(R.dimen.popup_default_width);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        popupWidth = popupRememberSizeAndPos ? sharedPreferences.getFloat(POPUP_SAVED_WIDTH, defaultSize) : defaultSize;

        final int layoutParamType = Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_PHONE : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

        windowLayoutParams = new WindowManager.LayoutParams(
                (int) popupWidth, (int) playerImpl.getMinimumVideoHeight(popupWidth),
                layoutParamType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        windowLayoutParams.gravity = Gravity.LEFT | Gravity.TOP;

        int centerX = (int) (screenWidth / 2f - popupWidth / 2f);
        int centerY = (int) (screenHeight / 2f - popupHeight / 2f);
        windowLayoutParams.x = popupRememberSizeAndPos ? sharedPreferences.getInt(POPUP_SAVED_X, centerX) : centerX;
        windowLayoutParams.y = popupRememberSizeAndPos ? sharedPreferences.getInt(POPUP_SAVED_Y, centerY) : centerY;

        playerImpl.checkPositionBounds();

        playerImpl.getLoadingPanel().setMinimumWidth(windowLayoutParams.width);
        playerImpl.getLoadingPanel().setMinimumHeight(windowLayoutParams.height);

        removeViewFromParent();
        windowManager.addView(getView(), windowLayoutParams);
    }

    private void initVideoPlayer () {
        getView().setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
    }

    public void removeViewFromParent() {
        if (getView().getParent() != null) {
            if (playerImpl.getParentActivity() != null) {
                // This means view was added to fragment
                ViewGroup parent = (ViewGroup) getView().getParent();
                parent.removeView(getView());
            } else
                // This means view was added by windowManager for popup player
                windowManager.removeViewImmediate(getView());
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Notification
    //////////////////////////////////////////////////////////////////////////*/

    private void resetNotification() {
        notBuilder = createNotification();
    }

    private NotificationCompat.Builder createNotification() {
        notRemoteView = new RemoteViews(BuildConfig.APPLICATION_ID, R.layout.player_notification);
        bigNotRemoteView = new RemoteViews(BuildConfig.APPLICATION_ID, R.layout.player_notification_expanded);

        setupNotification(notRemoteView);
        setupNotification(bigNotRemoteView);

        return new NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCustomContentView(notRemoteView)
                .setCustomBigContentView(bigNotRemoteView);
    }

    private void setupNotification(RemoteViews remoteViews) {
        // Don't show anything until player is playing
        if (playerImpl == null)
            return;

        if(playerImpl.cachedImage != null) remoteViews.setImageViewBitmap(R.id.notificationCover, playerImpl.cachedImage);

        remoteViews.setTextViewText(R.id.notificationSongName, playerImpl.getVideoTitle());
        remoteViews.setTextViewText(R.id.notificationArtist, playerImpl.getUploaderName());

        remoteViews.setOnClickPendingIntent(R.id.notificationPlayPause,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_PLAY_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT));
        remoteViews.setOnClickPendingIntent(R.id.notificationStop,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_CLOSE), PendingIntent.FLAG_UPDATE_CURRENT));
        // Starts VideoDetailFragment or opens BackgroundPlayerActivity.
        remoteViews.setOnClickPendingIntent(R.id.notificationContent,
                PendingIntent.getActivity(this, NOTIFICATION_ID, getIntentForNotification(), PendingIntent.FLAG_UPDATE_CURRENT));
        remoteViews.setOnClickPendingIntent(R.id.notificationRepeat,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_REPEAT), PendingIntent.FLAG_UPDATE_CURRENT));

        if (playerImpl.playQueue != null && playerImpl.playQueue.size() > 1) {
            remoteViews.setInt(R.id.notificationFRewind, SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_previous);
            remoteViews.setInt(R.id.notificationFForward, SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_next);
            remoteViews.setOnClickPendingIntent(R.id.notificationFRewind,
                    PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_PLAY_PREVIOUS), PendingIntent.FLAG_UPDATE_CURRENT));
            remoteViews.setOnClickPendingIntent(R.id.notificationFForward,
                    PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_PLAY_NEXT), PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            remoteViews.setInt(R.id.notificationFRewind, SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_rewind);
            remoteViews.setInt(R.id.notificationFForward, SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_fastforward);
            remoteViews.setOnClickPendingIntent(R.id.notificationFRewind,
                    PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_FAST_REWIND), PendingIntent.FLAG_UPDATE_CURRENT));
            remoteViews.setOnClickPendingIntent(R.id.notificationFForward,
                    PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_FAST_FORWARD), PendingIntent.FLAG_UPDATE_CURRENT));
        }


        setRepeatModeRemote(remoteViews, playerImpl.getRepeatMode());
    }

    /**
     * Updates the notification, and the play/pause button in it.
     * Used for changes on the remoteView
     *
     * @param drawableId if != -1, sets the drawable with that id on the play/pause button
     */
    private void updateNotification(int drawableId) {
        if (DEBUG) Log.d(TAG, "updateNotification() called with: drawableId = [" + drawableId + "]");
        if (notBuilder == null) return;
        if (drawableId != -1) {
            if (notRemoteView != null)
                notRemoteView.setImageViewResource(R.id.notificationPlayPause, drawableId);
            if (bigNotRemoteView != null)
                bigNotRemoteView.setImageViewResource(R.id.notificationPlayPause, drawableId);
        }
        notificationManager.notify(NOTIFICATION_ID, notBuilder.build());
    }

    private void setControlsOpacity(@IntRange(from = 0, to = 255) int opacity) {
        if (notRemoteView != null) notRemoteView.setInt(R.id.notificationPlayPause, setAlphaMethodName, opacity);
        if (bigNotRemoteView != null) bigNotRemoteView.setInt(R.id.notificationPlayPause, setAlphaMethodName, opacity);
        if (notRemoteView != null) notRemoteView.setInt(R.id.notificationFForward, setAlphaMethodName, opacity);
        if (bigNotRemoteView != null) bigNotRemoteView.setInt(R.id.notificationFForward, setAlphaMethodName, opacity);
        if (notRemoteView != null) notRemoteView.setInt(R.id.notificationFRewind, setAlphaMethodName, opacity);
        if (bigNotRemoteView != null) bigNotRemoteView.setInt(R.id.notificationFRewind, setAlphaMethodName, opacity);
    }

    private void setRepeatModeRemote(final RemoteViews remoteViews, final int repeatMode) {
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

    private Intent getIntentForNotification() {
        Intent intent;
        if(playerImpl.audioPlayerSelected() || playerImpl.popupPlayerSelected()) {
            intent = NavigationHelper.getBackgroundPlayerActivityIntent(getApplicationContext());
        }
        else {
            intent = NavigationHelper.getPlayerIntent(
                    getApplicationContext(),
                    MainActivity.class,
                    null
            );
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
        }
        return intent;
    }

    ///////////////////////////////////////////////////////////////////////////

    @SuppressWarnings({"unused", "WeakerAccess"})
    public class VideoPlayerImpl extends VideoPlayer {
        private TextView titleTextView;
        private TextView channelTextView;
        private TextView volumeTextView;
        private TextView brightnessTextView;
        private TextView qualityTextView;
        private TextView resizingIndicator;
        private ImageButton queueButton;
        private ImageButton repeatButton;
        private ImageButton shuffleButton;
        private ImageButton screenRotationButton;
        private Space spaceBeforeFullscreenButton;
        private ImageButton fullScreenButton;

        private ImageButton playPauseButton;
        private ImageButton playPreviousButton;
        private ImageButton playNextButton;

        private RelativeLayout queueLayout;
        private RecyclerView itemsList;
        private ItemTouchHelper itemTouchHelper;

        private boolean queueVisible;
        public boolean audioOnly = false;
        private boolean isAudioPlayerSelected = false;
        private boolean isPopupPlayerSelected = false;
        private boolean isFullscreen = false;

        private ImageButton moreOptionsButton;
        public int moreOptionsPopupMenuGroupId = 89;
        public PopupMenu moreOptionsPopupMenu;

        private Bitmap cachedImage;

        @Override
        public void handleIntent(Intent intent) {
            if(intent.getSerializableExtra(BasePlayer.PLAY_QUEUE) == null) return;

            selectAudioPlayer(intent.getBooleanExtra(BasePlayer.AUDIO_ONLY, false));
            audioOnly = audioPlayerSelected();
            // We need to setup audioOnly before super()
            super.handleIntent(intent);

            resetNotification();
            if (bigNotRemoteView != null) bigNotRemoteView.setProgressBar(R.id.notificationProgressBar, 100, 0, false);
            if (notRemoteView != null) notRemoteView.setProgressBar(R.id.notificationProgressBar, 100, 0, false);
            startForeground(NOTIFICATION_ID, notBuilder.build());
            setupElementsVisibility();

            if(!audioPlayerSelected())
                getView().setVisibility(View.VISIBLE);
            else
                removeViewFromParent();
        }

        VideoPlayerImpl(final Context context) {
            super("VideoPlayerImpl" + MainPlayerService.TAG, context);
        }

        @Override
        public void initViews(View rootView) {
            super.initViews(rootView);
            this.titleTextView = rootView.findViewById(R.id.titleTextView);
            this.channelTextView = rootView.findViewById(R.id.channelTextView);
            this.volumeTextView = rootView.findViewById(R.id.volumeTextView);
            this.brightnessTextView = rootView.findViewById(R.id.brightnessTextView);
            this.qualityTextView = rootView.findViewById(R.id.qualityTextView);
            this.queueButton = rootView.findViewById(R.id.queueButton);
            this.repeatButton = rootView.findViewById(R.id.repeatButton);
            this.shuffleButton = rootView.findViewById(R.id.shuffleButton);
            this.screenRotationButton = rootView.findViewById(R.id.screenRotationButton);
            this.playPauseButton = rootView.findViewById(R.id.playPauseButton);
            this.playPreviousButton = rootView.findViewById(R.id.playPreviousButton);
            this.playNextButton = rootView.findViewById(R.id.playNextButton);
            this.moreOptionsButton = rootView.findViewById(R.id.moreOptionsButton);
            this.moreOptionsPopupMenu = new PopupMenu(context, moreOptionsButton);
            this.moreOptionsPopupMenu.getMenuInflater().inflate(R.menu.menu_videooptions, moreOptionsPopupMenu.getMenu());
            this.resizingIndicator = rootView.findViewById(R.id.resizing_indicator);
            this.spaceBeforeFullscreenButton = rootView.findViewById(R.id.spaceBeforeFullscreenButton);
            this.fullScreenButton = rootView.findViewById(R.id.fullScreenButton);

            this.fullScreenButton.setOnClickListener(v -> onFullScreenButtonClicked());
            titleTextView.setSelected(true);
            channelTextView.setSelected(true);

            getRootView().setKeepScreenOn(true);
        }

        public void setupElementsVisibility() {
            if (popupPlayerSelected()) {
                fullScreenButton.setVisibility(View.VISIBLE);
                screenRotationButton.setVisibility(View.GONE);
                getView().findViewById(R.id.titleAndChannel).setVisibility(View.GONE);
                qualityTextView.setVisibility(View.VISIBLE);
                spaceBeforeFullscreenButton.setVisibility(View.VISIBLE);
            } else {
                fullScreenButton.setVisibility(View.GONE);
                screenRotationButton.setVisibility(View.VISIBLE);
                getView().findViewById(R.id.titleAndChannel).setVisibility(View.VISIBLE);
                qualityTextView.setVisibility(isInFullscreen()? View.VISIBLE : View.INVISIBLE);
                spaceBeforeFullscreenButton.setVisibility(View.GONE);
            }
        }

        @Override
        public void initListeners() {
            super.initListeners();

            MySimpleOnGestureListener listener = new MySimpleOnGestureListener();
            gestureDetector = new GestureDetector(context, listener);
            gestureDetector.setIsLongpressEnabled(true);
            getRootView().setOnTouchListener(listener);

            queueButton.setOnClickListener(this);
            repeatButton.setOnClickListener(this);
            shuffleButton.setOnClickListener(this);

            playPauseButton.setOnClickListener(this);
            playPreviousButton.setOnClickListener(this);
            playNextButton.setOnClickListener(this);
            screenRotationButton.setOnClickListener(this);
            moreOptionsButton.setOnClickListener(this);
        }

        public Activity getParentActivity() {
            // ! instanceof ViewGroup means that view was added via windowManager for Popup
            if(getView().getParent() == null || !(getView().getParent() instanceof ViewGroup)) return null;

                ViewGroup parent = (ViewGroup) getView().getParent();
                return (Activity) parent.getContext();
        }

        /*//////////////////////////////////////////////////////////////////////////
        // ExoPlayer Video Listener
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        public void onRepeatModeChanged(int i) {
            super.onRepeatModeChanged(i);
            setRepeatModeRemote(notRemoteView, i);
            updatePlaybackButtons();
            updatePlayback();
            updateNotification(-1);
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
        public void onPlayerError(ExoPlaybackException error) {
            super.onPlayerError(error);

            if(fragmentListener != null && !popupPlayerSelected())
                fragmentListener.onPlayerError(error);
        }

        @Override
        public void sync(@NonNull final PlayQueueItem item, @Nullable final StreamInfo info) {
            super.sync(item, info);
            titleTextView.setText(getVideoTitle());
            channelTextView.setText(getUploaderName());
            updateMetadata();
        }

        @Override
        public void onShuffleClicked() {
            super.onShuffleClicked();
            updatePlaybackButtons();
            updatePlayback();
        }

        @Override
        public void onUpdateProgress(int currentProgress, int duration, int bufferPercent) {
            super.onUpdateProgress(currentProgress, duration, bufferPercent);
            updateProgress(currentProgress, duration, bufferPercent);

            if (!shouldUpdateOnProgress || getCurrentState() == BasePlayer.STATE_COMPLETED || getCurrentState() == BasePlayer.STATE_PAUSED || playerImpl.getPlayQueue() == null) return;
            resetNotification();
            if (bigNotRemoteView != null) {
                bigNotRemoteView.setProgressBar(R.id.notificationProgressBar, duration, currentProgress, false);
                bigNotRemoteView.setTextViewText(R.id.notificationTime, getTimeString(currentProgress) + " / " + getTimeString(duration));
            }
            if (notRemoteView != null) {
                notRemoteView.setProgressBar(R.id.notificationProgressBar, duration, currentProgress, false);
            }
            updateNotification(-1);
        }

        @Override
        @Nullable
        public MediaSource sourceOf(final PlayQueueItem item, final StreamInfo info) {
            if(!audioOnly)
                return super.sourceOf(item, info);
            else {
                final int index = ListHelper.getDefaultAudioFormat(context, info.audio_streams);
                if (index < 0 || index >= info.audio_streams.size()) return null;

                final AudioStream audio = info.audio_streams.get(index);
                return buildMediaSource(audio.getUrl(), MediaFormat.getSuffixById(audio.format));
            }
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Player Overrides
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        public void onFullScreenButtonClicked() {
            if (DEBUG) Log.d(TAG, "onFullScreenButtonClicked() called");

            if(popupPlayerSelected()) {
                setRecovery();
                getPlayer().setPlayWhenReady(false);
                removeViewFromParent();
                Intent intent;
                if (!isUsingOldPlayer(getApplicationContext())) {
                    intent = NavigationHelper.getPlayerIntent(
                            context,
                            MainActivity.class,
                            this.getPlayQueue(),
                            this.getRepeatMode(),
                            this.getPlaybackSpeed(),
                            this.getPlaybackPitch(),
                            this.getPlaybackQuality()
                    );
                    intent.putExtra(VideoPlayer.STARTED_FROM_NEWPIPE, isStartedFromNewPipe());
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra(Constants.KEY_LINK_TYPE, StreamingService.LinkType.STREAM);
                    intent.putExtra(Constants.KEY_URL, getVideoUrl());
                    intent.putExtra(Constants.KEY_TITLE, getVideoTitle());
                    intent.putExtra(VideoDetailFragment.AUTO_PLAY, PreferenceManager.getDefaultSharedPreferences(context)
                            .getBoolean(context.getString(R.string.autoplay_through_intent_key), false));
                } else {
                    intent = new Intent(MainPlayerService.this, PlayVideoActivity.class)
                            .putExtra(PlayVideoActivity.VIDEO_TITLE, getVideoTitle())
                            .putExtra(PlayVideoActivity.STREAM_URL, getSelectedVideoStream().getUrl())
                            .putExtra(PlayVideoActivity.VIDEO_URL, getVideoUrl())
                            .putExtra(PlayVideoActivity.START_POSITION, Math.round(getPlayer().getCurrentPosition() / 1000f));
                }
                context.startActivity(intent);
            }
            else {
                if(fragmentListener == null) return;

                playerInFullscreenNow(!isInFullscreen());
                qualityTextView.setVisibility(isInFullscreen()? View.VISIBLE : View.GONE);
                fragmentListener.onFullScreenButtonClicked(isInFullscreen());
            }

        }

        public void onPlayBackgroundButtonClicked() {
            if (DEBUG) Log.d(TAG, "onPlayBackgroundButtonClicked() called");
            if (playerImpl.getPlayer() == null) return;

            setRecovery();
            final Intent intent = NavigationHelper.getPlayerIntent(
                    context,
                    MainPlayerService.class,
                    this.getPlayQueue(),
                    this.getRepeatMode(),
                    this.getPlaybackSpeed(),
                    this.getPlaybackPitch(),
                    this.getPlaybackQuality()
            );
            context.startService(intent);

            ((View) getControlAnimationView().getParent()).setVisibility(View.GONE);
            destroy();
        }


        @Override
        public void onClick(View v) {
            super.onClick(v);
            if (v.getId() == playPauseButton.getId()) {
                useVideoSource(true);
                onVideoPlayPause();

            } else if (v.getId() == playPreviousButton.getId()) {
                onPlayPrevious();

            } else if (v.getId() == playNextButton.getId()) {
                onPlayNext();

            } else if (v.getId() == screenRotationButton.getId()) {
                onScreenRotationClicked();

            } else if (v.getId() == queueButton.getId()) {
                onQueueClicked();
                return;
            } else if (v.getId() == repeatButton.getId()) {
                onRepeatClicked();
                return;
            } else if (v.getId() == shuffleButton.getId()) {
                onShuffleClicked();
                return;
            } else if (v.getId() == moreOptionsButton.getId()) {
                onMoreOptionsClicked();
            }

            if (getCurrentState() != STATE_COMPLETED) {
                getControlsVisibilityHandler().removeCallbacksAndMessages(null);
                animateView(getControlsRoot(), true, 300, 0, () -> {
                    if (getCurrentState() == STATE_PLAYING && !isSomePopupMenuVisible()) {
                        hideControls(300, DEFAULT_CONTROLS_HIDE_TIME);
                    }
                });
            }
        }

        private void onQueueClicked() {
            if(playQueue.getIndex() < 0) return;

            queueVisible = true;

            buildQueue();
            updatePlaybackButtons();

            getControlsRoot().setVisibility(View.INVISIBLE);
            queueLayout.setVisibility(View.VISIBLE);

            itemsList.scrollToPosition(playQueue.getIndex());

            if(playQueue.getStreams().size() > 4 && !isInFullscreen())
                onFullScreenButtonClicked();
        }

        private void onQueueClosed() {
            queueLayout.setVisibility(View.GONE);
            queueVisible = false;
        }

        private void onMoreOptionsClicked() {
            if (DEBUG) Log.d(TAG, "onMoreOptionsClicked() called");
            buildMoreOptionsMenu();

            try {
                Field[] fields = moreOptionsPopupMenu.getClass().getDeclaredFields();
                for (Field field : fields) {
                    if ("mPopup".equals(field.getName())) {
                        field.setAccessible(true);
                        Object menuPopupHelper = field.get(moreOptionsPopupMenu);
                        Class<?> classPopupHelper = Class.forName(menuPopupHelper
                                .getClass().getName());
                        Method setForceIcons = classPopupHelper.getMethod(
                                "setForceShowIcon", boolean.class);
                        setForceIcons.invoke(menuPopupHelper, true);
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            moreOptionsPopupMenu.show();
            isSomePopupMenuVisible = true;
            showControls(300);
        }

        private void onScreenRotationClicked() {
            if (DEBUG) Log.d(TAG, "onScreenRotationClicked() called");
            toggleOrientation();
            showControlsThenHide();
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
        protected int getDefaultResolutionIndex(final List<VideoStream> sortedVideos) {
            return ListHelper.getDefaultResolutionIndex(context, sortedVideos);
        }

        @Override
        protected int getOverrideResolutionIndex(final List<VideoStream> sortedVideos,
                                                 final String playbackQuality) {
            return ListHelper.getDefaultResolutionIndex(context, sortedVideos, playbackQuality);
        }

        /*//////////////////////////////////////////////////////////////////////////
        // States
        //////////////////////////////////////////////////////////////////////////*/

        private void animatePlayButtons(final boolean show, final int duration) {
            animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, show, duration);
            if(playQueue.getIndex() > 0)
                animateView(playPreviousButton, AnimationUtils.Type.SCALE_AND_ALPHA, show, duration);
            if(playQueue.getIndex() + 1 < playQueue.getStreams().size())
                animateView(playNextButton, AnimationUtils.Type.SCALE_AND_ALPHA, show, duration);
        }

        @Override
        public void changeState(int state) {
            super.changeState(state);
            updatePlayback();
        }

        @Override
        public void onBlocked() {
            super.onBlocked();
            playPauseButton.setImageResource(R.drawable.ic_pause_white);
            animatePlayButtons(false, 100);
            getRootView().setKeepScreenOn(true);
            setControlsOpacity(77);
            updateNotification(R.drawable.ic_play_arrow_white);
        }

        @Override
        public void onBuffering() {
            super.onBuffering();
            animatePlayButtons(false, 100);
            getRootView().setKeepScreenOn(true);
        }

        @Override
        public void onPlaying() {
            super.onPlaying();
            animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, false, 80, 0, new Runnable() {
                @Override
                public void run() {
                    playPauseButton.setImageResource(R.drawable.ic_pause_white);
                    animatePlayButtons(true, 200);
                }
            });
            checkLandscape();
            getRootView().setKeepScreenOn(true);
            getSurfaceView().setVisibility(View.VISIBLE);
            lockManager.acquireWifiAndCpu();
            resetNotification();
            setControlsOpacity(255);
            updateNotification(R.drawable.ic_pause_white);
            startForeground(NOTIFICATION_ID, notBuilder.build());
        }

        @Override
        public void onPaused() {
            super.onPaused();
            animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, false, 80, 0, () -> {
                playPauseButton.setImageResource(R.drawable.ic_play_arrow_white);
                animatePlayButtons(true, 200);
            });
            getRootView().setKeepScreenOn(false);
            lockManager.releaseWifiAndCpu();
            updateNotification(R.drawable.ic_play_arrow_white);

            if(audioPlayerSelected()) {
                stopForeground(false);
            } else {
                stopForeground(true);
                notificationManager.cancel(NOTIFICATION_ID);
            }
        }

        @Override
        public void onPausedSeek() {
            super.onPausedSeek();
            animatePlayButtons(false, 100);
            getRootView().setKeepScreenOn(true);
            updateNotification(R.drawable.ic_play_arrow_white);
        }


        @Override
        public void onCompleted() {
            animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, false, 0, 0, () -> {
                playPauseButton.setImageResource(R.drawable.ic_replay_white);
                animatePlayButtons(true, 300);
            });

            getRootView().setKeepScreenOn(false);
            showControls(100);
            lockManager.releaseWifiAndCpu();
            updateNotification(R.drawable.ic_play_arrow_white);

            if(audioPlayerSelected()) {
                stopForeground(false);
            } else {
                stopForeground(true);
                notificationManager.cancel(NOTIFICATION_ID);
            }
        }

        @Override
        public void shutdown() {
            if (DEBUG) Log.d(TAG, "Shutting down...");
            // Override it because we don't want playerImpl destroyed
        }

        @Override
        public void destroy() {
            super.destroy();
            cachedImage = null;
            setRootView(null);
            stopForeground(true);
            notificationManager.cancel(NOTIFICATION_ID);
            stopActivityBinding();
        }

        @Override
        public void initThumbnail(final String url) {
            resetNotification();
            if (notRemoteView != null) notRemoteView.setImageViewResource(R.id.notificationCover, R.drawable.dummy_thumbnail);
            if (bigNotRemoteView != null) bigNotRemoteView.setImageViewResource(R.id.notificationCover, R.drawable.dummy_thumbnail);
            updateNotification(-1);
            super.initThumbnail(url);
        }

        @Override
        public void onThumbnailReceived(Bitmap thumbnail) {
            super.onThumbnailReceived(thumbnail);
            if (thumbnail != null) {
                cachedImage = thumbnail;
                // rebuild notification here since remote view does not release bitmaps, causing memory leaks
                notBuilder = createNotification();

                if (notRemoteView != null) notRemoteView.setImageViewBitmap(R.id.notificationCover, thumbnail);
                if (bigNotRemoteView != null) bigNotRemoteView.setImageViewBitmap(R.id.notificationCover, thumbnail);

                updateNotification(-1);
            }
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
            intentFilter.addAction(ACTION_OPEN_CONTROLS);
            intentFilter.addAction(ACTION_REPEAT);
            intentFilter.addAction(ACTION_PLAY_PREVIOUS);
            intentFilter.addAction(ACTION_PLAY_NEXT);
            intentFilter.addAction(ACTION_FAST_REWIND);
            intentFilter.addAction(ACTION_FAST_FORWARD);

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
                case ACTION_PLAY_NEXT:
                    onPlayNext();
                    break;
                case ACTION_PLAY_PREVIOUS:
                    onPlayPrevious();
                    break;
                case ACTION_FAST_FORWARD:
                    onFastForward();
                    break;
                case ACTION_FAST_REWIND:
                    onFastRewind();
                    break;
                case ACTION_PLAY_PAUSE:
                    onVideoPlayPause();
                    break;
                case ACTION_REPEAT:
                    onRepeatClicked();
                    break;
                case Intent.ACTION_SCREEN_ON:
                    shouldUpdateOnProgress = true;
                    // Interrupt playback only when screen turns on and user is watching video in fragment
                    if(backgroundPlaybackEnabledInSettings() && !audioPlayerSelected() && getPlayer() != null && (isPlaying() || getPlayer().isLoading()))
                        useVideoSource(true);
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    shouldUpdateOnProgress = false;
                    // Interrupt playback only when screen turns off with video working
                    Log.d(TAG, "onBroadcastReceived: "+backgroundPlaybackEnabledInSettings());
                    Log.d(TAG, "onBroadcastReceived: "+!audioPlayerSelected());
                    Log.d(TAG, "onBroadcastReceived: "+(getPlayer() != null));
                    Log.d(TAG, "onBroadcastReceived: "+(isPlaying() || getPlayer().isLoading()));

                    if(backgroundPlaybackEnabledInSettings() && !audioPlayerSelected() && getPlayer() != null && (isPlaying() || getPlayer().isLoading()))
                        useVideoSource(false);
                    break;
            }
            resetNotification();
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Utils
        //////////////////////////////////////////////////////////////////////////*/

        public boolean backgroundPlaybackEnabledInSettings() {
            return defaultPreferences.getBoolean(getApplicationContext().getString(R.string.continue_in_background_key), false);
        }

        public void selectAudioPlayer(boolean background) {
            isAudioPlayerSelected = background;
        }

        public boolean audioPlayerSelected() {
            return isAudioPlayerSelected;
        }

        public boolean videoPlayerSelected() {
            return !popupPlayerSelected() && !audioPlayerSelected();
        }

        public boolean popupPlayerSelected() {
            return isPopupPlayerSelected;
        }


        public void playerInFullscreenNow(boolean fullscreen) {
            isFullscreen = fullscreen;
        }

        public boolean isInFullscreen() {
            return isFullscreen;
        }

        @Override
        public void showControlsThenHide() {
            if (queueVisible) return;

            showOrHideButtons();
            super.showControlsThenHide();
        }

        @Override
        public void showControls(long duration) {
            if (queueVisible) return;
            showOrHideButtons();
            super.showControls(duration);
        }

        @Override
        public void hideControls(final long duration, long delay) {
            if (DEBUG) Log.d(TAG, "hideControls() called with: delay = [" + delay + "]");
            showOrHideButtons();

            getControlsVisibilityHandler().removeCallbacksAndMessages(null);
            getControlsVisibilityHandler().postDelayed(() ->
                animateView(getControlsRoot(), false, duration, 0, () -> {
                        if(getView() == null || getView().getContext() == null || !isInFullscreen()) return;

                            Activity parent = getParentActivity();
                            if(parent == null) return;

                            Window window = parent.getWindow();

                            if (android.os.Build.VERSION.SDK_INT >= 16) {
                                int visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) visibility |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                                window.getDecorView().setSystemUiVisibility(visibility);
                            }
                            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
                    })
        , delay);
        }
        private  void showOrHideButtons(){
            if(playQueue == null) return;

            if(playQueue.getIndex() == 0)
                playPreviousButton.setVisibility(View.INVISIBLE);
            else
                playPreviousButton.setVisibility(View.VISIBLE);

            if(playQueue.getIndex() + 1 == playQueue.getStreams().size())
                playNextButton.setVisibility(View.INVISIBLE);
            else
                playNextButton.setVisibility(View.VISIBLE);

            if(playQueue.getStreams().size() <= 1 || popupPlayerSelected())
                queueButton.setVisibility(View.GONE);
            else
                queueButton.setVisibility(View.VISIBLE);
        }

        private void updatePlaybackButtons() {
            if (repeatButton == null || shuffleButton == null ||
                    simpleExoPlayer == null || playQueue == null) return;

            setRepeatModeButton(repeatButton, getRepeatMode());
            setShuffleButton(shuffleButton, playQueue.isShuffled());
        }

        public void checkLandscape() {
            Activity parent = playerImpl.getParentActivity();
            if(parent != null && isLandScape() && !isInFullscreen() && getCurrentState() != STATE_COMPLETED && !isAudioPlayerSelected)
                playerImpl.onFullScreenButtonClicked();
        }

        private void buildMoreOptionsMenu() {
            if (moreOptionsPopupMenu == null) return;
            moreOptionsPopupMenu.setOnMenuItemClickListener(menuItem -> {
                switch (menuItem.getItemId()) {
                    case R.id.toggleOrientation:
                        onScreenRotationClicked();
                        break;
                    case R.id.switchPopup:
                        onFullScreenButtonClicked();
                        break;
                    case R.id.switchBackground:
                        onPlayBackgroundButtonClicked();
                        break;
                }
                return false;
            });
        }

        private void buildQueue() {
            queueLayout = getRootView().findViewById(R.id.playQueuePanel);

            ImageButton itemsListCloseButton = getRootView().findViewById(R.id.playQueueClose);

            itemsList = getRootView().findViewById(R.id.playQueue);
            itemsList.setAdapter(playQueueAdapter);
            itemsList.setClickable(true);
            itemsList.setLongClickable(true);

            itemsList.clearOnScrollListeners();
            itemsList.addOnScrollListener(getQueueScrollListener());

            itemTouchHelper = new ItemTouchHelper(getItemTouchCallback());
            itemTouchHelper.attachToRecyclerView(itemsList);

            playQueueAdapter.setSelectedListener(getOnSelectedListener());

            itemsListCloseButton.setOnClickListener(view ->
                onQueueClosed()
            );
        }

        public void useVideoSource(boolean video) {
            if(playQueue == null || audioOnly == !video || audioPlayerSelected()) return;

            boolean shouldStartPlaying = true;
            if(getPlayer() != null)
                shouldStartPlaying = isPlaying();

            audioOnly = !video;
            setRecovery();
            // Here we are restarting playback. This method will be called with audioOnly setting - sourceOf(final PlayQueueItem item, final StreamInfo info)
            initPlayback(playQueue);
            getPlayer().setPlayWhenReady(shouldStartPlaying);
        }

        private OnScrollBelowItemsListener getQueueScrollListener() {
            return new OnScrollBelowItemsListener() {
                @Override
                public void onScrolledDown(RecyclerView recyclerView) {
                    if (playQueue != null && !playQueue.isComplete()) {
                        playQueue.fetch();
                    } else if (itemsList != null) {
                        itemsList.clearOnScrollListeners();
                    }
                }
            };
        }

        private ItemTouchHelper.SimpleCallback getItemTouchCallback() {
            return new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
                @Override
                public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder source, RecyclerView.ViewHolder target) {
                    if (source.getItemViewType() != target.getItemViewType()) {
                        return false;
                    }

                    final int sourceIndex = source.getLayoutPosition();
                    final int targetIndex = target.getLayoutPosition();
                    playQueue.move(sourceIndex, targetIndex);
                    return true;
                }

                @Override
                public boolean isLongPressDragEnabled() {
                    return false;
                }

                @Override
                public boolean isItemViewSwipeEnabled() {
                    return false;
                }

                @Override
                public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir) {}
            };
        }

        private PlayQueueItemBuilder.OnSelectedListener getOnSelectedListener() {
            return new PlayQueueItemBuilder.OnSelectedListener() {
                @Override
                public void selected(PlayQueueItem item, View view) {
                    onSelected(item);
                }

                @Override
                public void held(PlayQueueItem item, View view) {
                    final int index = playQueue.indexOf(item);
                    if (index != -1) playQueue.remove(index);
                }

                @Override
                public void onStartDrag(PlayQueueItemHolder viewHolder) {
                    if (itemTouchHelper != null) itemTouchHelper.startDrag(viewHolder);
                }
            };
        }

    /*//////////////////////////////////////////////////////////////////////////
    // Popup utils
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
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainPlayerService.this);
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
        // Manipulations with listener
        ///////////////////////////////////////////////////////////////////////////

        public void setFragmentListener(PlayerServiceEventListener listener) {
            fragmentListener = listener;
            updateMetadata();
            updatePlayback();
            triggerProgressUpdate();
        }

        public void removeFragmentListener(PlayerServiceEventListener listener) {
            if (fragmentListener == listener) {
                fragmentListener = null;
            }
        }

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
            if (fragmentListener != null && currentInfo != null && !popupPlayerSelected()) {
                fragmentListener.onMetadataUpdate(currentInfo);
            }
            if (activityListener != null && currentInfo != null) {
                activityListener.onMetadataUpdate(currentInfo);
            }
        }

        private void updatePlayback() {
            if (fragmentListener != null && simpleExoPlayer != null && playQueue != null && !popupPlayerSelected()) {
                fragmentListener.onPlaybackUpdate(currentState, getRepeatMode(), playQueue.isShuffled(), simpleExoPlayer.getPlaybackParameters());
            }
            if (activityListener != null && simpleExoPlayer != null && playQueue != null) {
                activityListener.onPlaybackUpdate(currentState, getRepeatMode(), playQueue.isShuffled(), getPlaybackParameters());
            }
        }

        private void updateProgress(int currentProgress, int duration, int bufferPercent) {
            if (fragmentListener != null && !popupPlayerSelected()) {
                fragmentListener.onProgressUpdate(currentProgress, duration, bufferPercent);
            }
            if (activityListener != null) {
                activityListener.onProgressUpdate(currentProgress, duration, bufferPercent);
            }
        }

        private void stopActivityBinding() {
            if (fragmentListener != null) {
                fragmentListener.onServiceStopped();
                fragmentListener = null;
            }
            if (activityListener != null) {
                activityListener.onServiceStopped();
                activityListener = null;
            }
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
            if (DEBUG) Log.d(TAG, "onDoubleTap() called with: e = [" + e + "]" + "rawXy = " + e.getRawX() + ", " + e.getRawY() + ", xy = " + e.getX() + ", " + e.getY());
            if (playerImpl.getPlayer() == null || !playerImpl.isPlaying() || !playerImpl.isPlayerReady()) return false;

            float widthToCheck = playerImpl.popupPlayerSelected() ? popupWidth / 2 : playerImpl.getRootView().getWidth() / 2;

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
                if(playerImpl.currentState == BasePlayer.STATE_COMPLETED)
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

            initialPopupX = windowLayoutParams.x;
            initialPopupY = windowLayoutParams.y;
            popupWidth = windowLayoutParams.width;
            popupHeight = windowLayoutParams.height;
            return super.onDown(e);
        }

        @Override
        public void onLongPress(MotionEvent e) {
            if (DEBUG) Log.d(TAG, "onLongPress() called with: e = [" + e + "]");

            if(!playerImpl.popupPlayerSelected()) return;

            playerImpl.updateScreenSize();
            playerImpl.checkPositionBounds();
            playerImpl.updatePopupSize((int) screenWidth, -1);
        }

        private boolean handleOnScrollInPopup(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
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
            windowManager.updateViewLayout(getView(), windowLayoutParams);
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (DEBUG) Log.d(TAG, "Fling velocity: dX=[" + velocityX + "], dY=[" + velocityY + "]");
            if(!playerImpl.popupPlayerSelected()) return true;

            final float absVelocityX = Math.abs(velocityX);
            final float absVelocityY = Math.abs(velocityY);
            if (absVelocityX > shutdownFlingVelocity) {
                onDestroy();
                return true;
            } else if (Math.max(absVelocityX, absVelocityY) > tossFlingVelocity) {
                if (absVelocityX > tossFlingVelocity) windowLayoutParams.x = (int) velocityX;
                if (absVelocityY > tossFlingVelocity) windowLayoutParams.y = (int) velocityY;
                playerImpl.checkPositionBounds();
                windowManager.updateViewLayout(getView(), windowLayoutParams);
                return true;
            }
            return false;
        }

        private boolean handleTouchInPopup(View v, MotionEvent event) {
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
                windowLayoutParams.x = (int) (event.getRawX() - diff);
            } else {
                // first pointer is the anchor
                windowLayoutParams.x = (int) event.getRawX();
            }

            playerImpl.checkPositionBounds();
            playerImpl.updateScreenSize();

            final int width = (int) Math.min(screenWidth, diff);
            playerImpl.updatePopupSize(width, -1);

            return true;
        }


        private final boolean isPlayerGestureEnabled = PlayerHelper.isPlayerGestureEnabled(getApplicationContext());

        private final float stepsBrightness = 15, stepBrightness = (1f / stepsBrightness), minBrightness = .01f;
        private float currentBrightness = .5f;

        private int currentVolume;
        private final int maxVolume = playerImpl.getAudioReactor().getMaxVolume();
        private final float stepsVolume = 15, stepVolume = (float) Math.ceil(maxVolume / stepsVolume), minVolume = 0;

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
                } else if(getView().getContext() != null) {
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
            gestureDetector.onTouchEvent(event);
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

    /**
     * Fetcher handler used if open by a link out of NewPipe
     */
    private class FetcherHandler {
        private final int serviceId;
        private final String url;

        private final Context context;
        private final Handler mainHandler;

        private FetcherHandler(Context context, int serviceId, String url) {
            this.mainHandler = new Handler(MainPlayerService.this.getMainLooper());
            this.context = context;
            this.url = url;
            this.serviceId = serviceId;
        }

        private void onReceive(final StreamInfo info) {
            mainHandler.post(() -> {
                final Intent intent = NavigationHelper.getPlayerIntent(getApplicationContext(),
                        MainPlayerService.class, new SinglePlayQueue(info));
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
