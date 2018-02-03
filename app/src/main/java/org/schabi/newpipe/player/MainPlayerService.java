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
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.IntRange;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.*;

import com.google.android.exoplayer2.Player;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
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
import org.schabi.newpipe.player.helper.LockManager;
import org.schabi.newpipe.playlist.SinglePlayQueue;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ThemeHelper;

import java.io.IOException;

/**
 * Player based on service implementing VideoPlayer
 *
 * @authors mauriciocolli and avently
 */
public class MainPlayerService extends Service {
    private static final String TAG = ".MainPlayerService";
    private static final boolean DEBUG = BasePlayer.DEBUG;

    private VideoPlayerImpl playerImpl;

    private Disposable currentWorker;
    private WindowManager windowManager;

    private final IBinder mBinder = new MainPlayerService.LocalBinder();

    // Notification
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notBuilder;
    private RemoteViews notRemoteView;
    private RemoteViews bigNotRemoteView;
    static final int NOTIFICATION_ID = 417308;
    static final String ACTION_CLOSE = "org.schabi.newpipe.player.MainPlayerService.CLOSE";
    static final String ACTION_PLAY_PAUSE = "org.schabi.newpipe.player.MainPlayerService.PLAY_PAUSE";
    static final String ACTION_OPEN_CONTROLS = "org.schabi.newpipe.player.MainPlayerService.OPEN_CONTROLS";
    static final String ACTION_REPEAT = "org.schabi.newpipe.player.MainPlayerService.REPEAT";
    static final String ACTION_PLAY_NEXT = "org.schabi.newpipe.player.MainPlayerService.ACTION_PLAY_NEXT";
    static final String ACTION_PLAY_PREVIOUS = "org.schabi.newpipe.player.MainPlayerService.ACTION_PLAY_PREVIOUS";
    static final String ACTION_FAST_REWIND = "org.schabi.newpipe.player.MainPlayerService.ACTION_FAST_REWIND";
    static final String ACTION_FAST_FORWARD = "org.schabi.newpipe.player.MainPlayerService.ACTION_FAST_FORWARD";

    private static final String SET_IMAGE_RESOURCE_METHOD = "setImageResource";
    private final String setAlphaMethodName = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) ? "setImageAlpha" : "setAlpha";

    private LockManager lockManager;

    private SharedPreferences defaultPreferences;

    public enum PlayerType {
        VIDEO,
        AUDIO,
        POPUP
    }

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

        playerImpl.setStartedFromNewPipe(intent.getSerializableExtra(BasePlayer.PLAY_QUEUE) != null);

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

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if(!playerImpl.popupPlayerSelected()) return;

        playerImpl.updateScreenSize();
        playerImpl.updatePopupSize(playerImpl.getWindowLayoutParams(), playerImpl.getWindowLayoutParams().width, -1);
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

    boolean isLandScape() {
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

    void setRepeatModeButton(final ImageButton imageButton, final int repeatMode) {
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

    void setShuffleButton(final ImageButton shuffleButton, final boolean shuffled) {
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

    void resetNotification() {
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

        if(playerImpl.getCachedImage() != null) remoteViews.setImageViewBitmap(R.id.notificationCover, playerImpl.getCachedImage());

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
    void updateNotification(int drawableId) {
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

    void setControlsOpacity(@IntRange(from = 0, to = 255) int opacity) {
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
            // Means we play in popup or audio only. Let's show BackgroundPlayerActivity
            intent = NavigationHelper.getBackgroundPlayerActivityIntent(getApplicationContext());
        }
        else {
            // We are playing in fragment. Don't open another activity just show fragment. That's it
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

    /*//////////////////////////////////////////////////////////////////////////
    // Getters
    //////////////////////////////////////////////////////////////////////////*/

    public NotificationManager getNotificationManager() {
        return notificationManager;
    }

    public LockManager getLockManager() {
        return lockManager;
    }

    public NotificationCompat.Builder getNotBuilder() {
        return notBuilder;
    }

    public RemoteViews getBigNotRemoteView() {
        return bigNotRemoteView;
    }

    public RemoteViews getNotRemoteView() {
        return notRemoteView;
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
