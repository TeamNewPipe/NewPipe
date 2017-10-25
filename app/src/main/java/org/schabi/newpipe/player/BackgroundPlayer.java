/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * BackgroundPlayer.java is part of NewPipe
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

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.event.PlayerEventListener;
import org.schabi.newpipe.playlist.PlayQueueItem;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.ThemeHelper;


/**
 * Base players joining the common properties
 *
 * @author mauriciocolli
 */
public final class BackgroundPlayer extends Service {
    private static final String TAG = "BackgroundPlayer";
    private static final boolean DEBUG = BasePlayer.DEBUG;

    public static final String ACTION_CLOSE = "org.schabi.newpipe.player.BackgroundPlayer.CLOSE";
    public static final String ACTION_PLAY_PAUSE = "org.schabi.newpipe.player.BackgroundPlayer.PLAY_PAUSE";
    public static final String ACTION_OPEN_DETAIL = "org.schabi.newpipe.player.BackgroundPlayer.OPEN_DETAIL";
    public static final String ACTION_REPEAT = "org.schabi.newpipe.player.BackgroundPlayer.REPEAT";
    public static final String ACTION_FAST_REWIND = "org.schabi.newpipe.player.BackgroundPlayer.ACTION_FAST_REWIND";
    public static final String ACTION_FAST_FORWARD = "org.schabi.newpipe.player.BackgroundPlayer.ACTION_FAST_FORWARD";

    private BasePlayerImpl basePlayerImpl;
    private PowerManager powerManager;
    private WifiManager wifiManager;

    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    /*//////////////////////////////////////////////////////////////////////////
    // Service-Activity Binder
    //////////////////////////////////////////////////////////////////////////*/

    private PlayerEventListener activityListener;
    private IBinder mBinder;

    class LocalBinder extends Binder {
        BasePlayerImpl getBackgroundPlayerInstance() {
            return BackgroundPlayer.this.basePlayerImpl;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Notification
    //////////////////////////////////////////////////////////////////////////*/

    private static final int NOTIFICATION_ID = 123789;
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notBuilder;
    private RemoteViews notRemoteView;
    private RemoteViews bigNotRemoteView;
    private final String setAlphaMethodName = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) ? "setImageAlpha" : "setAlpha";
    private final String setImageResourceMethodName = "setImageResource";

    private boolean shouldUpdateOnProgress;

    /*//////////////////////////////////////////////////////////////////////////
    // Service's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate() {
        if (DEBUG) Log.d(TAG, "onCreate() called");
        notificationManager = ((NotificationManager) getSystemService(NOTIFICATION_SERVICE));
        powerManager = ((PowerManager) getSystemService(POWER_SERVICE));
        wifiManager = ((WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE));

        ThemeHelper.setTheme(this);
        basePlayerImpl = new BasePlayerImpl(this);
        basePlayerImpl.setup();

        mBinder = new LocalBinder();
        shouldUpdateOnProgress = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "onStartCommand() called with: intent = [" + intent + "], flags = [" + flags + "], startId = [" + startId + "]");
        basePlayerImpl.handleIntent(intent);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "destroy() called");
        onClose();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Actions
    //////////////////////////////////////////////////////////////////////////*/

    public void openControl(final Context context) {
        final Intent intent = new Intent(context, BackgroundPlayerActivity.class);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
        context.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
    }

    private void onClose() {
        stopForeground(true);
        releaseWifiAndCpu();

        if (basePlayerImpl != null) {
            basePlayerImpl.stopActivityBinding();
            basePlayerImpl.destroy();
        }

        basePlayerImpl = null;
        mBinder = null;
        stopSelf();
    }

    private void onScreenOnOff(boolean on) {
        if (DEBUG) Log.d(TAG, "onScreenOnOff() called with: on = [" + on + "]");
        shouldUpdateOnProgress = on;
        if (on) {
            basePlayerImpl.triggerProgressUpdate();
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

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_play_circle_filled_white_24dp)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCustomContentView(notRemoteView)
                .setCustomBigContentView(bigNotRemoteView);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) builder.setPriority(NotificationCompat.PRIORITY_MAX);
        return builder;
    }

    private void setupNotification(RemoteViews remoteViews) {
        remoteViews.setOnClickPendingIntent(R.id.notificationPlayPause,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_PLAY_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT));
        remoteViews.setOnClickPendingIntent(R.id.notificationStop,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_CLOSE), PendingIntent.FLAG_UPDATE_CURRENT));
        remoteViews.setOnClickPendingIntent(R.id.notificationContent,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_OPEN_DETAIL), PendingIntent.FLAG_UPDATE_CURRENT));
        remoteViews.setOnClickPendingIntent(R.id.notificationRepeat,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_REPEAT), PendingIntent.FLAG_UPDATE_CURRENT));

        remoteViews.setOnClickPendingIntent(R.id.notificationFRewind,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_FAST_REWIND), PendingIntent.FLAG_UPDATE_CURRENT));
        remoteViews.setOnClickPendingIntent(R.id.notificationFForward,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_FAST_FORWARD), PendingIntent.FLAG_UPDATE_CURRENT));

        setRepeatModeIcon(remoteViews, basePlayerImpl.simpleExoPlayer.getRepeatMode());
    }

    /**
     * Updates the notification, and the play/pause button in it.
     * Used for changes on the remoteView
     *
     * @param drawableId if != -1, sets the drawable with that id on the play/pause button
     */
    private synchronized void updateNotification(int drawableId) {
        //if (DEBUG) Log.d(TAG, "updateNotification() called with: drawableId = [" + drawableId + "]");
        if (notBuilder == null) return;
        if (drawableId != -1) {
            if (notRemoteView != null) notRemoteView.setImageViewResource(R.id.notificationPlayPause, drawableId);
            if (bigNotRemoteView != null) bigNotRemoteView.setImageViewResource(R.id.notificationPlayPause, drawableId);
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

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void lockWifiAndCpu() {
        if (DEBUG) Log.d(TAG, "lockWifiAndCpu() called");
        if (wakeLock != null && wakeLock.isHeld() && wifiLock != null && wifiLock.isHeld()) return;

        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, TAG);

        if (wakeLock != null) wakeLock.acquire();
        if (wifiLock != null) wifiLock.acquire();
    }

    private void releaseWifiAndCpu() {
        if (DEBUG) Log.d(TAG, "releaseWifiAndCpu() called");
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();

        wakeLock = null;
        wifiLock = null;
    }

    private void setRepeatModeIcon(final RemoteViews remoteViews, final int repeatMode) {
        switch (repeatMode) {
            case Player.REPEAT_MODE_OFF:
                remoteViews.setInt(R.id.notificationRepeat, setImageResourceMethodName, R.drawable.exo_controls_repeat_off);
                break;
            case Player.REPEAT_MODE_ONE:
                remoteViews.setInt(R.id.notificationRepeat, setImageResourceMethodName, R.drawable.exo_controls_repeat_one);
                break;
            case Player.REPEAT_MODE_ALL:
                remoteViews.setInt(R.id.notificationRepeat, setImageResourceMethodName, R.drawable.exo_controls_repeat_all);
                break;
        }
    }
    //////////////////////////////////////////////////////////////////////////

    protected class BasePlayerImpl extends BasePlayer {

        BasePlayerImpl(Context context) {
            super(context);
        }

        @Override
        public void handleIntent(final Intent intent) {
            super.handleIntent(intent);

            resetNotification();
            startForeground(NOTIFICATION_ID, notBuilder.build());

            if (bigNotRemoteView != null) bigNotRemoteView.setProgressBar(R.id.notificationProgressBar, 100, 0, false);
            if (notRemoteView != null) notRemoteView.setProgressBar(R.id.notificationProgressBar, 100, 0, false);
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
                // rebuild notification here since remote view does not release bitmaps, causing memory leaks
                resetNotification();

                if (notRemoteView != null) notRemoteView.setImageViewBitmap(R.id.notificationCover, thumbnail);
                if (bigNotRemoteView != null) bigNotRemoteView.setImageViewBitmap(R.id.notificationCover, thumbnail);

                updateNotification(-1);
            }
        }

        @Override
        public void onPrepared(boolean playWhenReady) {
            super.onPrepared(playWhenReady);
            simpleExoPlayer.setVolume(1f);
        }

        @Override
        public void onShuffleClicked() {
            super.onShuffleClicked();
            updatePlayback();
        }

        @Override
        public void onUpdateProgress(int currentProgress, int duration, int bufferPercent) {
            updateProgress(currentProgress, duration, bufferPercent);

            if (!shouldUpdateOnProgress) return;

            resetNotification();
            if (bigNotRemoteView != null) {
                if (currentItem != null) {
                    bigNotRemoteView.setTextViewText(R.id.notificationSongName, getVideoTitle());
                    bigNotRemoteView.setTextViewText(R.id.notificationArtist, getUploaderName());
                }
                bigNotRemoteView.setProgressBar(R.id.notificationProgressBar, duration, currentProgress, false);
                bigNotRemoteView.setTextViewText(R.id.notificationTime, getTimeString(currentProgress) + " / " + getTimeString(duration));
            }
            if (notRemoteView != null) {
                if (currentItem != null) {
                    notRemoteView.setTextViewText(R.id.notificationSongName, getVideoTitle());
                    notRemoteView.setTextViewText(R.id.notificationArtist, getUploaderName());
                }
                notRemoteView.setProgressBar(R.id.notificationProgressBar, duration, currentProgress, false);
            }

            updateNotification(-1);
        }

        @Override
        public void onFastRewind() {
            if (!isPlayerReady()) return;

            onPlayPrevious();
            triggerProgressUpdate();
        }

        @Override
        public void onFastForward() {
            if (!isPlayerReady()) return;

            onPlayNext();
            triggerProgressUpdate();
        }

        @Override
        public void destroy() {
            super.destroy();
            if (notRemoteView != null) notRemoteView.setImageViewBitmap(R.id.notificationCover, null);
            if (bigNotRemoteView != null) bigNotRemoteView.setImageViewBitmap(R.id.notificationCover, null);
        }

        @Override
        public void onRecoverableError(Exception exception) {
            exception.printStackTrace();

            if (errorToast == null) {
                errorToast = Toast.makeText(context, R.string.player_audio_failure, Toast.LENGTH_SHORT);
                errorToast.show();
            }
        }

        @Override
        public void onUnrecoverableError(Exception exception) {
            exception.printStackTrace();

            if (errorToast != null) {
                errorToast.cancel();
            }
            errorToast = Toast.makeText(context, R.string.player_unexpected_failure, Toast.LENGTH_SHORT);
            errorToast.show();

            shutdown();
        }

        /*//////////////////////////////////////////////////////////////////////////
        // ExoPlayer Listener
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
            super.onPlaybackParametersChanged(playbackParameters);
            updatePlayback();
        }

        @Override
        public void onLoadingChanged(boolean isLoading) {
            // Disable default behavior
        }

        @Override
        public void onRepeatModeChanged(int i) {
            setRepeatModeIcon(notRemoteView, i);
            setRepeatModeIcon(bigNotRemoteView, i);
            updateNotification(-1);
            updatePlayback();
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Playback Listener
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        public void sync(@NonNull final PlayQueueItem item, @Nullable final StreamInfo info) {
            super.sync(item, info);

            resetNotification();
            notRemoteView.setTextViewText(R.id.notificationSongName, getVideoTitle());
            notRemoteView.setTextViewText(R.id.notificationArtist, getUploaderName());
            bigNotRemoteView.setTextViewText(R.id.notificationSongName, getVideoTitle());
            bigNotRemoteView.setTextViewText(R.id.notificationArtist, getUploaderName());
            updateNotification(-1);
            updateMetadata();
        }

        @Override
        public MediaSource sourceOf(final PlayQueueItem item, final StreamInfo info) {
            final int index = ListHelper.getDefaultAudioFormat(context, info.audio_streams);
            if (index < 0) return null;

            final AudioStream audio = info.audio_streams.get(index);
            return buildMediaSource(audio.url, MediaFormat.getSuffixById(audio.format));
        }

        @Override
        public void shutdown() {
            super.shutdown();
            stopActivityBinding();
            stopSelf();
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Activity Event Listener
        //////////////////////////////////////////////////////////////////////////*/

        public void setActivityListener(PlayerEventListener listener) {
            activityListener = listener;
            updateMetadata();
            updatePlayback();
            triggerProgressUpdate();
        }

        public void removeActivityListener(PlayerEventListener listener) {
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
                activityListener.onPlaybackUpdate(currentState, simpleExoPlayer.getRepeatMode(), playQueue.isShuffled(), simpleExoPlayer.getPlaybackParameters());
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
        // Broadcast Receiver
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        protected void setupBroadcastReceiver(IntentFilter intentFilter) {
            super.setupBroadcastReceiver(intentFilter);
            intentFilter.addAction(ACTION_CLOSE);
            intentFilter.addAction(ACTION_PLAY_PAUSE);
            intentFilter.addAction(ACTION_OPEN_DETAIL);
            intentFilter.addAction(ACTION_REPEAT);
            intentFilter.addAction(ACTION_FAST_FORWARD);
            intentFilter.addAction(ACTION_FAST_REWIND);

            intentFilter.addAction(Intent.ACTION_SCREEN_ON);
            intentFilter.addAction(Intent.ACTION_SCREEN_OFF);

            intentFilter.addAction(Intent.ACTION_HEADSET_PLUG);
        }

        @Override
        public void onBroadcastReceived(Intent intent) {
            super.onBroadcastReceived(intent);
            if (DEBUG) Log.d(TAG, "onBroadcastReceived() called with: intent = [" + intent + "]");
            switch (intent.getAction()) {
                case ACTION_CLOSE:
                    onClose();
                    break;
                case ACTION_PLAY_PAUSE:
                    onVideoPlayPause();
                    break;
                case ACTION_OPEN_DETAIL:
                    openControl(BackgroundPlayer.this);
                    break;
                case ACTION_REPEAT:
                    onRepeatClicked();
                    break;
                case ACTION_FAST_REWIND:
                    onFastRewind();
                    break;
                case ACTION_FAST_FORWARD:
                    onFastForward();
                    break;
                case Intent.ACTION_SCREEN_ON:
                    onScreenOnOff(true);
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    onScreenOnOff(false);
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

            setControlsOpacity(77);
            updateNotification(-1);
        }

        @Override
        public void onPlaying() {
            super.onPlaying();

            setControlsOpacity(255);
            updateNotification(R.drawable.ic_pause_white);

            lockWifiAndCpu();
        }

        @Override
        public void onPaused() {
            super.onPaused();

            updateNotification(R.drawable.ic_play_arrow_white);
            if (isProgressLoopRunning()) stopProgressLoop();

            releaseWifiAndCpu();
        }

        @Override
        public void onCompleted() {
            super.onCompleted();

            setControlsOpacity(255);
            if (bigNotRemoteView != null) bigNotRemoteView.setProgressBar(R.id.notificationProgressBar, 100, 100, false);
            if (notRemoteView != null) notRemoteView.setProgressBar(R.id.notificationProgressBar, 100, 100, false);
            updateNotification(R.drawable.ic_replay_white);

            releaseWifiAndCpu();
        }
    }
}
