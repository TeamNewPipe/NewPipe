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
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.IntRange;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.ThemeHelper;

import java.io.Serializable;


/**
 * Base players joining the common properties
 *
 * @author mauriciocolli
 */
public class BackgroundPlayer extends Service {
    private static final String TAG = "BackgroundPlayer";
    private static final boolean DEBUG = BasePlayer.DEBUG;

    public static final String ACTION_CLOSE = "org.schabi.newpipe.player.BackgroundPlayer.CLOSE";
    public static final String ACTION_PLAY_PAUSE = "org.schabi.newpipe.player.BackgroundPlayer.PLAY_PAUSE";
    public static final String ACTION_OPEN_DETAIL = "org.schabi.newpipe.player.BackgroundPlayer.OPEN_DETAIL";
    public static final String ACTION_REPEAT = "org.schabi.newpipe.player.BackgroundPlayer.REPEAT";
    public static final String ACTION_FAST_REWIND = "org.schabi.newpipe.player.BackgroundPlayer.ACTION_FAST_REWIND";
    public static final String ACTION_FAST_FORWARD = "org.schabi.newpipe.player.BackgroundPlayer.ACTION_FAST_FORWARD";

    public static final String AUDIO_STREAM = "video_only_audio_stream";
    private AudioStream audioStream;

    private BasePlayerImpl basePlayerImpl;
    private PowerManager powerManager;
    private WifiManager wifiManager;

    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    /*//////////////////////////////////////////////////////////////////////////
    // Notification
    //////////////////////////////////////////////////////////////////////////*/
    private static final int NOTIFICATION_ID = 123789;

    private NotificationManager notificationManager;
    private NotificationCompat.Builder notBuilder;
    private RemoteViews notRemoteView;
    private RemoteViews bigNotRemoteView;
    private final String setAlphaMethodName = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) ? "setImageAlpha" : "setAlpha";

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
        releaseWifiAndCpu();
        stopForeground(true);
        if (basePlayerImpl != null) basePlayerImpl.destroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Actions
    //////////////////////////////////////////////////////////////////////////*/

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

    private void onClose() {
        if (basePlayerImpl != null) basePlayerImpl.destroyPlayer();
        stopForeground(true);
        releaseWifiAndCpu();
        stopSelf();
    }

    private void onScreenOnOff(boolean on) {
        if (DEBUG) Log.d(TAG, "onScreenOnOff() called with: on = [" + on + "]");
        if (on) {
            if (basePlayerImpl.isPlaying() && !basePlayerImpl.isProgressLoopRunning.get()) basePlayerImpl.startProgressLoop();
        } else basePlayerImpl.stopProgressLoop();

    }

    /*//////////////////////////////////////////////////////////////////////////
    // Notification
    //////////////////////////////////////////////////////////////////////////*/

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
        //if (videoThumbnail != null) remoteViews.setImageViewBitmap(R.id.notificationCover, videoThumbnail);
        ///else remoteViews.setImageViewResource(R.id.notificationCover, R.drawable.dummy_thumbnail);
        remoteViews.setTextViewText(R.id.notificationSongName, basePlayerImpl.getVideoTitle());
        remoteViews.setTextViewText(R.id.notificationArtist, basePlayerImpl.getUploaderName());

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

        switch (basePlayerImpl.getCurrentRepeatMode()) {
            case REPEAT_DISABLED:
                remoteViews.setInt(R.id.notificationRepeat, setAlphaMethodName, 77);
                break;
            case REPEAT_ONE:
                remoteViews.setInt(R.id.notificationRepeat, setAlphaMethodName, 255);
                break;
            case REPEAT_ALL:
                // Waiting :)
                break;
        }
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

    //////////////////////////////////////////////////////////////////////////

    private class BasePlayerImpl extends BasePlayer {

        BasePlayerImpl(Context context) {
            super(context);
        }

        @Override
        public void handleIntent(Intent intent) {
            super.handleIntent(intent);
            Serializable serializable = intent.getSerializableExtra(BackgroundPlayer.AUDIO_STREAM);
            if (serializable instanceof AudioStream) audioStream = (AudioStream) serializable;
            playUrl(audioStream.url, MediaFormat.getSuffixById(audioStream.format), true);

            if (bigNotRemoteView != null) bigNotRemoteView.setProgressBar(R.id.notificationProgressBar, 100, 0, false);
            if (notRemoteView != null) notRemoteView.setProgressBar(R.id.notificationProgressBar, 100, 0, false);
        }

        @Override
        public void initThumbnail() {
            if (notRemoteView != null) notRemoteView.setImageViewResource(R.id.notificationCover, R.drawable.dummy_thumbnail);
            if (bigNotRemoteView != null) bigNotRemoteView.setImageViewResource(R.id.notificationCover, R.drawable.dummy_thumbnail);
            updateNotification(-1);
            super.initThumbnail();
        }

        @Override
        public void onThumbnailReceived(Bitmap thumbnail) {
            super.onThumbnailReceived(thumbnail);
            if (thumbnail != null) {
                if (notRemoteView != null) notRemoteView.setImageViewBitmap(R.id.notificationCover, thumbnail);
                if (bigNotRemoteView != null) bigNotRemoteView.setImageViewBitmap(R.id.notificationCover, thumbnail);
                updateNotification(-1);
            }
        }

        @Override
        public void playUrl(String url, String format, boolean autoPlay) {
            super.playUrl(url, format, autoPlay);

            notBuilder = createNotification();
            startForeground(NOTIFICATION_ID, notBuilder.build());
        }

        @Override
        public void onPrepared(boolean playWhenReady) {
            super.onPrepared(playWhenReady);
            if (simpleExoPlayer.getDuration() < 15000) {
                FAST_FORWARD_REWIND_AMOUNT = 2000;
            } else if (simpleExoPlayer.getDuration() > 60 * 60 * 1000) {
                FAST_FORWARD_REWIND_AMOUNT = 60000;
            } else {
                FAST_FORWARD_REWIND_AMOUNT = 10000;
            }
            PROGRESS_LOOP_INTERVAL = 1000;
            basePlayerImpl.getPlayer().setVolume(1f);
        }

        @Override
        public void onRepeatClicked() {
            super.onRepeatClicked();

            int opacity = 255;
            switch (currentRepeatMode) {
                case REPEAT_DISABLED:
                    opacity = 77;
                    break;
                case REPEAT_ONE:
                    opacity = 255;
                    break;
                case REPEAT_ALL:
                    // Waiting :)
                    break;
            }
            if (notRemoteView != null) notRemoteView.setInt(R.id.notificationRepeat, setAlphaMethodName, opacity);
            if (bigNotRemoteView != null) bigNotRemoteView.setInt(R.id.notificationRepeat, setAlphaMethodName, opacity);
            updateNotification(-1);
        }

        @Override
        public void onUpdateProgress(int currentProgress, int duration, int bufferPercent) {
            if (bigNotRemoteView != null) bigNotRemoteView.setProgressBar(R.id.notificationProgressBar, duration, currentProgress, false);
            if (notRemoteView != null) notRemoteView.setProgressBar(R.id.notificationProgressBar, duration, currentProgress, false);
            if (bigNotRemoteView != null) bigNotRemoteView.setTextViewText(R.id.notificationTime, getTimeString(currentProgress) + " / " + getTimeString(duration));
            updateNotification(-1);
        }

        @Override
        public void onFastRewind() {
            super.onFastRewind();
            triggerProgressUpdate();
        }

        @Override
        public void onFastForward() {
            super.onFastForward();
            triggerProgressUpdate();
        }

        @Override
        public void onLoadingChanged(boolean isLoading) {
            // Disable default behavior
        }

        @Override
        public void onRepeatModeChanged(int i) {

        }

        @Override
        public void destroy() {
            super.destroy();
            if (notRemoteView != null) notRemoteView.setImageViewBitmap(R.id.notificationCover, null);
            if (bigNotRemoteView != null) bigNotRemoteView.setImageViewBitmap(R.id.notificationCover, null);
        }

        @Override
        public void onError(Exception exception) {
            exception.printStackTrace();
            stopSelf();
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
                    onOpenDetail(BackgroundPlayer.this, basePlayerImpl.getVideoUrl(), basePlayerImpl.getVideoTitle());
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
        public void onLoading() {
            super.onLoading();

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
            if (isProgressLoopRunning.get()) stopProgressLoop();

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
