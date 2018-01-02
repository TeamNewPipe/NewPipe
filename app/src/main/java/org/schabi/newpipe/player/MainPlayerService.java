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

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MenuItem;
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
import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.fragments.OnScrollBelowItemsListener;
import org.schabi.newpipe.player.event.PlayerEventListener;
import org.schabi.newpipe.player.helper.LockManager;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.playlist.PlayQueue;
import org.schabi.newpipe.playlist.PlayQueueItem;
import org.schabi.newpipe.playlist.PlayQueueItemBuilder;
import org.schabi.newpipe.playlist.PlayQueueItemHolder;
import org.schabi.newpipe.util.AnimationUtils;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ThemeHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.schabi.newpipe.player.helper.PlayerHelper.getTimeString;
import static org.schabi.newpipe.util.AnimationUtils.animateView;

/**
 * Player based on service implementing VideoPlayer
 *
 * @author mauriciocolli and avently
 */
public class MainPlayerService extends Service {
    private static final String TAG = ".MainPlayerService";
    private static final boolean DEBUG = BasePlayer.DEBUG;

    private GestureDetector gestureDetector;

    private VideoPlayerImpl playerImpl;

    private PlayerEventListener fragmentListener;
    private final IBinder mBinder = new MainPlayerService.LocalBinder();

    // Notification
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notBuilder;
    private RemoteViews notRemoteView;
    private RemoteViews bigNotRemoteView;
    private static final int NOTIFICATION_ID = 417308;
    private static final String ACTION_CLOSE = "org.schabi.newpipe.player.MainPlayerService.CLOSE";
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
        if(DEBUG) Log.d(TAG, "onStartCommand() called");
        playerImpl.handleIntent(intent);
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeHelper.setTheme(this);
        if(DEBUG) Log.d(TAG, "onCreate() called");
        notificationManager = ((NotificationManager) getSystemService(NOTIFICATION_SERVICE));
        lockManager = new LockManager(this);
        defaultPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        shouldUpdateOnProgress = true;

        createView();
    }

    private void createView() {
        View layout = View.inflate(this, R.layout.player_main, null);

        playerImpl = new VideoPlayerImpl(this);
        playerImpl.setStartedFromNewPipe(true);
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
            if (getView() != null && getView().getParent() != null) {
                ViewGroup parent = (ViewGroup) getView().getParent();
                parent.removeView(getView());
            }
            playerImpl.destroy();
            playerImpl = null;
        }
        stopForeground(true);
        notificationManager.cancel(NOTIFICATION_ID);
        stopSelf();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    public void loadVideo(PlayQueue queue, String videoResolution, long playbackPosition) {
        playerImpl.selectedResolution = videoResolution;
        playerImpl.notifyIsInBackground(false);
        playerImpl.audioOnly = false;
        playerImpl.playQueue = queue;
        playerImpl.playQueue.setRecovery(playerImpl.playQueue.getIndex(), playbackPosition);

        Intent playerIntent = NavigationHelper.getPlayerIntent(this, MainPlayerService.class, playerImpl.playQueue, videoResolution);
        playerImpl.handleIntent(playerIntent);

        getView().findViewById(R.id.surfaceView).setVisibility(View.GONE);
    }

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
                .setSmallIcon(R.drawable.ic_play_arrow_white)
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
        remoteViews.setOnClickPendingIntent(R.id.notificationContent,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_OPEN_CONTROLS), PendingIntent.FLAG_UPDATE_CURRENT));
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

    ///////////////////////////////////////////////////////////////////////////

    @SuppressWarnings({"unused", "WeakerAccess"})
    public class VideoPlayerImpl extends VideoPlayer {
        private TextView titleTextView;
        private TextView channelTextView;
        private TextView volumeTextView;
        private TextView brightnessTextView;
        private ImageButton queueButton;
        private ImageButton repeatButton;
        private ImageButton shuffleButton;
        private ImageButton screenRotationButton;

        private ImageButton playPauseButton;
        private ImageButton playPreviousButton;
        private ImageButton playNextButton;

        private RelativeLayout queueLayout;
        private RecyclerView itemsList;
        private ItemTouchHelper itemTouchHelper;

        private boolean queueVisible;
        private boolean audioOnly = false;
        private boolean isBackgroundPlayerSelected = false;
        private boolean isFullscreen = false;

        private String selectedResolution;

        private ImageButton moreOptionsButton;
        public int moreOptionsPopupMenuGroupId = 89;
        public PopupMenu moreOptionsPopupMenu;

        private Bitmap cachedImage;

        @Override
        public void handleIntent(Intent intent) {
            if(intent.getSerializableExtra(BasePlayer.PLAY_QUEUE) == null) return;

            notifyIsInBackground(intent.getBooleanExtra(BasePlayer.AUDIO_ONLY, false));
            audioOnly = isInBackground();
            // We need to setup audioOnly before super()
            super.handleIntent(intent);

            resetNotification();
            if (bigNotRemoteView != null) bigNotRemoteView.setProgressBar(R.id.notificationProgressBar, 100, 0, false);
            if (notRemoteView != null) notRemoteView.setProgressBar(R.id.notificationProgressBar, 100, 0, false);
            startForeground(NOTIFICATION_ID, notBuilder.build());
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

            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) getFullScreenButton().getLayoutParams();
            params.width = 0;
            params.leftMargin = 0;
            getFullScreenButton().setVisibility(View.INVISIBLE);
            getFullScreenButton().setLayoutParams(params);
            titleTextView.setSelected(true);
            channelTextView.setSelected(true);

            getRootView().setKeepScreenOn(true);
        }

        @Override
        public void initListeners() {
            super.initListeners();

            MySimpleOnGestureListener listener = new MySimpleOnGestureListener();
            gestureDetector = new GestureDetector(context, listener);
            gestureDetector.setIsLongpressEnabled(false);
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

        private Activity getParentActivity() {
            if(getView().getParent() == null) return null;

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

            if(fragmentListener != null)
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
                return buildMediaSource(audio.url, MediaFormat.getSuffixById(audio.format));
            }
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Player Overrides
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        public void onFullScreenButtonClicked() {
            if (DEBUG) Log.d(TAG, "onFullScreenButtonClicked() called");
            if(fragmentListener == null) return;

            notifyIsInFullscreen(!isInFullscreen());
            fragmentListener.onFullScreenButtonClicked(isInFullscreen());
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
            getRootView().findViewById(R.id.surfaceView).setVisibility(View.VISIBLE);
            lockManager.acquireWifiAndCpu();
            resetNotification();
            setControlsOpacity(255);
            updateNotification(R.drawable.ic_pause_white);
            startForeground(NOTIFICATION_ID, notBuilder.build());
        }

        @Override
        public void onPaused() {
            super.onPaused();
            animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, false, 80, 0, new Runnable() {
                @Override
                public void run() {
                    playPauseButton.setImageResource(R.drawable.ic_play_arrow_white);
                    animatePlayButtons(true, 200);
                }
            });
            getRootView().setKeepScreenOn(false);
            lockManager.releaseWifiAndCpu();
            updateNotification(R.drawable.ic_play_arrow_white);

            if(isInBackground()) {
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
            animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, false, 0, 0, new Runnable() {
                @Override
                public void run() {
                    playPauseButton.setImageResource(R.drawable.ic_replay_white);
                    animatePlayButtons(true, 300);
                }
            });

            getRootView().setKeepScreenOn(false);
            showControls(100);
            lockManager.releaseWifiAndCpu();
            updateNotification(R.drawable.ic_play_arrow_white);

            if(isInBackground()) {
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
                    getPlayer().setPlayWhenReady(false);
                    stopForeground(true);
                    notificationManager.cancel(NOTIFICATION_ID);
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
                case ACTION_OPEN_CONTROLS:
                    if(!isInBackground())
                        openControl(getApplicationContext());
                    else
                        NavigationHelper.openBackgroundPlayerControl(getApplicationContext());
                    break;
                case ACTION_REPEAT:
                    onRepeatClicked();
                    break;
                case Intent.ACTION_SCREEN_ON:
                    shouldUpdateOnProgress = true;
                    if(isBackgroundPlaybackEnabled())
                        useVideoSource(true);
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    shouldUpdateOnProgress = false;
                    if(isBackgroundPlaybackEnabled())
                        useVideoSource(false);
                    break;
            }
            resetNotification();
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Utils
        //////////////////////////////////////////////////////////////////////////*/

        public void enableVideoRenderer(final boolean enable) {
            final int videoRendererIndex = getVideoRendererIndex();
            if (trackSelector != null && videoRendererIndex != -1) {
                trackSelector.setRendererDisabled(videoRendererIndex, !enable);
            }
        }
        void openControl(Context context) {
            Intent intent = NavigationHelper.getPlayerIntent(
                    context,
                    MainActivity.class,
                    null
            );
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            context.startActivity(intent);
            context.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
        }

        public boolean isBackgroundPlaybackEnabled() {
            return defaultPreferences.getBoolean(getApplicationContext().getString(R.string.continue_in_background_key), false);
        }

        public void notifyIsInBackground (boolean background) {
            isBackgroundPlayerSelected = background;
        }

        public boolean isInBackground() {
            return isBackgroundPlayerSelected;
        }

        public void notifyIsInFullscreen(boolean fullscreen) {
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
            getControlsVisibilityHandler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    animateView(getControlsRoot(), false, duration, 0, new Runnable() {
                        @Override
                        public void run() {
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
                        }
                    });
                }
            }, delay);
        }
        private  void showOrHideButtons(){
            if(playQueue == null) return;

            if(playQueue.getIndex() == 0)
                playPreviousButton.setVisibility(View.GONE);
            if(playQueue.getIndex() + 1 == playQueue.getStreams().size())
                playNextButton.setVisibility(View.GONE);
            if(playQueue.getStreams().size() <= 1)
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
            if(parent != null && isLandScape() && !isInFullscreen() && getCurrentState() != STATE_COMPLETED && !isBackgroundPlayerSelected)
                playerImpl.onFullScreenButtonClicked();
        }

        private void buildMoreOptionsMenu() {
            if (moreOptionsPopupMenu == null) return;
            moreOptionsPopupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem menuItem) {
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
                }
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

            itemsListCloseButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onQueueClosed();
                }
            });
        }

        public void useVideoSource(boolean video) {
            if(playQueue == null || selectedResolution == null || audioOnly == !video) return;

            boolean shouldStartPlaying = true;
            if(getPlayer() != null)
                shouldStartPlaying = isPlaying();

            audioOnly = !video;
            setRecovery();
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

        ///////////////////////////////////////////////////////////////////////////
        // Manipulations with listener
        ///////////////////////////////////////////////////////////////////////////

        public void setFragmentListener(PlayerEventListener listener) {
            fragmentListener = listener;
            updateMetadata();
            updatePlayback();
            triggerProgressUpdate();
        }

        public void removeFragmentListener(PlayerEventListener listener) {
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
            if (fragmentListener != null && currentInfo != null) {
                fragmentListener.onMetadataUpdate(currentInfo);
            }
            if (activityListener != null && currentInfo != null) {
                activityListener.onMetadataUpdate(currentInfo);
            }
        }

        private void updatePlayback() {
            if (fragmentListener != null && simpleExoPlayer != null && playQueue != null) {
                fragmentListener.onPlaybackUpdate(currentState, getRepeatMode(), playQueue.isShuffled(), simpleExoPlayer.getPlaybackParameters());
            }
            if (activityListener != null && simpleExoPlayer != null && playQueue != null) {
                activityListener.onPlaybackUpdate(currentState, getRepeatMode(), playQueue.isShuffled(), getPlaybackParameters());
            }
        }

        private void updateProgress(int currentProgress, int duration, int bufferPercent) {
            if (fragmentListener != null) {
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

        public ImageButton getPlayPauseButton() {
            return playPauseButton;
        }
    }

    private class MySimpleOnGestureListener extends GestureDetector.SimpleOnGestureListener implements View.OnTouchListener {
        private boolean isMoving;

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (DEBUG) Log.d(TAG, "onDoubleTap() called with: e = [" + e + "]" + "rawXy = " + e.getRawX() + ", " + e.getRawY() + ", xy = " + e.getX() + ", " + e.getY());
            if (playerImpl.getPlayer() == null || !playerImpl.isPlaying()) return false;

            if (e.getX() > playerImpl.getRootView().getWidth() / 2) {
                playerImpl.onFastForward();
            } else {
                playerImpl.onFastRewind();
            }

            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (DEBUG) Log.d(TAG, "onSingleTapConfirmed() called with: e = [" + e + "]");
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
}
