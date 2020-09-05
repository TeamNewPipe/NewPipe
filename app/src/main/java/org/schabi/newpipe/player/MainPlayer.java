/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * Part of NewPipe
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
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.view.WindowManager;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import com.google.android.exoplayer2.Player;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.util.BitmapUtils;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ThemeHelper;

import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;


/**
 * One service for all players.
 *
 * @author mauriciocolli
 */
public final class MainPlayer extends Service {
    private static final String TAG = "MainPlayer";
    private static final boolean DEBUG = BasePlayer.DEBUG;

    private VideoPlayerImpl playerImpl;
    private WindowManager windowManager;
    private SharedPreferences sharedPreferences;

    private final IBinder mBinder = new MainPlayer.LocalBinder();

    public enum PlayerType {
        VIDEO,
        AUDIO,
        POPUP
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Notification
    //////////////////////////////////////////////////////////////////////////*/

    static final int NOTIFICATION_ID = 123789;
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notBuilder;
    private RemoteViews notRemoteView;
    private RemoteViews bigNotRemoteView;

    static final String ACTION_CLOSE =
            "org.schabi.newpipe.player.MainPlayer.CLOSE";
    static final String ACTION_PLAY_PAUSE =
            "org.schabi.newpipe.player.MainPlayer.PLAY_PAUSE";
    static final String ACTION_OPEN_CONTROLS =
            "org.schabi.newpipe.player.MainPlayer.OPEN_CONTROLS";
    static final String ACTION_REPEAT =
            "org.schabi.newpipe.player.MainPlayer.REPEAT";
    static final String ACTION_PLAY_NEXT =
            "org.schabi.newpipe.player.MainPlayer.ACTION_PLAY_NEXT";
    static final String ACTION_PLAY_PREVIOUS =
            "org.schabi.newpipe.player.MainPlayer.ACTION_PLAY_PREVIOUS";
    static final String ACTION_FAST_REWIND =
            "org.schabi.newpipe.player.MainPlayer.ACTION_FAST_REWIND";
    static final String ACTION_FAST_FORWARD =
            "org.schabi.newpipe.player.MainPlayer.ACTION_FAST_FORWARD";

    private static final String SET_IMAGE_RESOURCE_METHOD = "setImageResource";

    /*//////////////////////////////////////////////////////////////////////////
    // Service's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate() {
        if (DEBUG) {
            Log.d(TAG, "onCreate() called");
        }
        assureCorrectAppLanguage(this);
        notificationManager = ((NotificationManager) getSystemService(NOTIFICATION_SERVICE));
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        ThemeHelper.setTheme(this);
        createView();
    }

    private void createView() {
        final View layout = View.inflate(this, R.layout.player, null);

        playerImpl = new VideoPlayerImpl(this);
        playerImpl.setup(layout);
        playerImpl.shouldUpdateOnProgress = true;
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        if (DEBUG) {
            Log.d(TAG, "onStartCommand() called with: intent = [" + intent
                    + "], flags = [" + flags + "], startId = [" + startId + "]");
        }
        if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())
                && playerImpl.playQueue == null) {
            // Player is not working, no need to process media button's action
            return START_NOT_STICKY;
        }

        if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())
                || intent.getStringExtra(VideoPlayer.PLAY_QUEUE_KEY) != null) {
            showNotificationAndStartForeground();
        }

        playerImpl.handleIntent(intent);
        if (playerImpl.mediaSessionManager != null) {
            playerImpl.mediaSessionManager.handleMediaButtonIntent(intent);
        }
        return START_NOT_STICKY;
    }

    public void stop(final boolean autoplayEnabled) {
        if (DEBUG) {
            Log.d(TAG, "stop() called");
        }

        if (playerImpl.getPlayer() != null) {
            playerImpl.wasPlaying = playerImpl.getPlayer().getPlayWhenReady();
            // Releases wifi & cpu, disables keepScreenOn, etc.
            if (!autoplayEnabled) {
                playerImpl.onPause();
            }
            // We can't just pause the player here because it will make transition
            // from one stream to a new stream not smooth
            playerImpl.getPlayer().stop(false);
            playerImpl.setRecovery();
            // Android TV will handle back button in case controls will be visible
            // (one more additional unneeded click while the player is hidden)
            playerImpl.hideControls(0, 0);
            // Notification shows information about old stream but if a user selects
            // a stream from backStack it's not actual anymore
            // So we should hide the notification at all.
            // When autoplay enabled such notification flashing is annoying so skip this case
            if (!autoplayEnabled) {
                stopForeground(true);
            }
        }
    }

    @Override
    public void onTaskRemoved(final Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        if (!playerImpl.videoPlayerSelected()) {
            return;
        }
        onDestroy();
        // Unload from memory completely
        Runtime.getRuntime().halt(0);
    }

    @Override
    public void onDestroy() {
        if (DEBUG) {
            Log.d(TAG, "destroy() called");
        }
        onClose();
    }

    @Override
    protected void attachBaseContext(final Context base) {
        super.attachBaseContext(AudioServiceLeakFix.preventLeakOf(base));
    }

    @Override
    public IBinder onBind(final Intent intent) {
        return mBinder;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Actions
    //////////////////////////////////////////////////////////////////////////*/
    private void onClose() {
        if (DEBUG) {
            Log.d(TAG, "onClose() called");
        }

        if (playerImpl != null) {
            removeViewFromParent();

            playerImpl.setRecovery();
            playerImpl.savePlaybackState();
            playerImpl.stopActivityBinding();
            playerImpl.removePopupFromView();
            playerImpl.destroy();
        }
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }

        stopForeground(true);
        stopSelf();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    boolean isLandscape() {
        // DisplayMetrics from activity context knows about MultiWindow feature
        // while DisplayMetrics from app context doesn't
        final DisplayMetrics metrics = (playerImpl != null
                && playerImpl.getParentActivity() != null)
                ? playerImpl.getParentActivity().getResources().getDisplayMetrics()
                : getResources().getDisplayMetrics();
        return metrics.heightPixels < metrics.widthPixels;
    }

    public View getView() {
        if (playerImpl == null) {
            return null;
        }

        return playerImpl.getRootView();
    }

    public void removeViewFromParent() {
        if (getView().getParent() != null) {
            if (playerImpl.getParentActivity() != null) {
                // This means view was added to fragment
                final ViewGroup parent = (ViewGroup) getView().getParent();
                parent.removeView(getView());
            } else {
                // This means view was added by windowManager for popup player
                windowManager.removeViewImmediate(getView());
            }
        }
    }

    private void showNotificationAndStartForeground() {
        resetNotification();
        if (getBigNotRemoteView() != null) {
            getBigNotRemoteView().setProgressBar(R.id.notificationProgressBar, 100, 0, false);
        }
        if (getNotRemoteView() != null) {
            getNotRemoteView().setProgressBar(R.id.notificationProgressBar, 100, 0, false);
        }
        startForeground(NOTIFICATION_ID, getNotBuilder().build());
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Notification
    //////////////////////////////////////////////////////////////////////////*/

    void resetNotification() {
        notBuilder = createNotification();
        playerImpl.timesNotificationUpdated = 0;
    }

    private NotificationCompat.Builder createNotification() {
        notRemoteView = new RemoteViews(BuildConfig.APPLICATION_ID,
                R.layout.player_notification);
        bigNotRemoteView = new RemoteViews(BuildConfig.APPLICATION_ID,
                R.layout.player_notification_expanded);

        setupNotification(notRemoteView);
        setupNotification(bigNotRemoteView);

        final NotificationCompat.Builder builder = new NotificationCompat
                .Builder(this, getString(R.string.notification_channel_id))
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCustomContentView(notRemoteView)
                .setCustomBigContentView(bigNotRemoteView);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setLockScreenThumbnail(builder);
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            builder.setPriority(NotificationCompat.PRIORITY_MAX);
        }
        return builder;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setLockScreenThumbnail(final NotificationCompat.Builder builder) {
        final boolean isLockScreenThumbnailEnabled = sharedPreferences.getBoolean(
                getString(R.string.enable_lock_screen_video_thumbnail_key), true);

        if (isLockScreenThumbnailEnabled) {
            playerImpl.mediaSessionManager.setLockScreenArt(
                    builder,
                    getCenteredThumbnailBitmap()
            );
        } else {
            playerImpl.mediaSessionManager.clearLockScreenArt(builder);
        }
    }

    @Nullable
    private Bitmap getCenteredThumbnailBitmap() {
        final int screenWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
        final int screenHeight = Resources.getSystem().getDisplayMetrics().heightPixels;

        return BitmapUtils.centerCrop(playerImpl.getThumbnail(), screenWidth, screenHeight);
    }

    private void setupNotification(final RemoteViews remoteViews) {
        // Don't show anything until player is playing
        if (playerImpl == null) {
            return;
        }

        remoteViews.setTextViewText(R.id.notificationSongName, playerImpl.getVideoTitle());
        remoteViews.setTextViewText(R.id.notificationArtist, playerImpl.getUploaderName());
        remoteViews.setImageViewBitmap(R.id.notificationCover, playerImpl.getThumbnail());

        remoteViews.setOnClickPendingIntent(R.id.notificationPlayPause,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID,
                        new Intent(ACTION_PLAY_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT));
        remoteViews.setOnClickPendingIntent(R.id.notificationStop,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID,
                        new Intent(ACTION_CLOSE), PendingIntent.FLAG_UPDATE_CURRENT));
        // Starts VideoDetailFragment or opens BackgroundPlayerActivity.
        remoteViews.setOnClickPendingIntent(R.id.notificationContent,
                PendingIntent.getActivity(this, NOTIFICATION_ID,
                        getIntentForNotification(), PendingIntent.FLAG_UPDATE_CURRENT));
        remoteViews.setOnClickPendingIntent(R.id.notificationRepeat,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID,
                        new Intent(ACTION_REPEAT), PendingIntent.FLAG_UPDATE_CURRENT));


        if (playerImpl.playQueue != null && playerImpl.playQueue.size() > 1) {
            remoteViews.setInt(R.id.notificationFRewind, SET_IMAGE_RESOURCE_METHOD,
                    R.drawable.exo_controls_previous);
            remoteViews.setInt(R.id.notificationFForward, SET_IMAGE_RESOURCE_METHOD,
                    R.drawable.exo_controls_next);
            remoteViews.setOnClickPendingIntent(R.id.notificationFRewind,
                    PendingIntent.getBroadcast(this, NOTIFICATION_ID,
                            new Intent(ACTION_PLAY_PREVIOUS), PendingIntent.FLAG_UPDATE_CURRENT));
            remoteViews.setOnClickPendingIntent(R.id.notificationFForward,
                    PendingIntent.getBroadcast(this, NOTIFICATION_ID,
                            new Intent(ACTION_PLAY_NEXT), PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            remoteViews.setInt(R.id.notificationFRewind, SET_IMAGE_RESOURCE_METHOD,
                    R.drawable.exo_controls_rewind);
            remoteViews.setInt(R.id.notificationFForward, SET_IMAGE_RESOURCE_METHOD,
                    R.drawable.exo_controls_fastforward);
            remoteViews.setOnClickPendingIntent(R.id.notificationFRewind,
                    PendingIntent.getBroadcast(this, NOTIFICATION_ID,
                            new Intent(ACTION_FAST_REWIND), PendingIntent.FLAG_UPDATE_CURRENT));
            remoteViews.setOnClickPendingIntent(R.id.notificationFForward,
                    PendingIntent.getBroadcast(this, NOTIFICATION_ID,
                            new Intent(ACTION_FAST_FORWARD), PendingIntent.FLAG_UPDATE_CURRENT));
        }

        setRepeatModeIcon(remoteViews, playerImpl.getRepeatMode());
    }

    /**
     * Updates the notification, and the play/pause button in it.
     * Used for changes on the remoteView
     *
     * @param drawableId if != -1, sets the drawable with that id on the play/pause button
     */
    synchronized void updateNotification(final int drawableId) {
        /*if (DEBUG) {
            Log.d(TAG, "updateNotification() called with: drawableId = [" + drawableId + "]");
        }*/
        if (notBuilder == null) {
            return;
        }
        if (drawableId != -1) {
            if (notRemoteView != null) {
                notRemoteView.setImageViewResource(R.id.notificationPlayPause, drawableId);
            }
            if (bigNotRemoteView != null) {
                bigNotRemoteView.setImageViewResource(R.id.notificationPlayPause, drawableId);
            }
        }
        notificationManager.notify(NOTIFICATION_ID, notBuilder.build());
        playerImpl.timesNotificationUpdated++;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void setRepeatModeIcon(final RemoteViews remoteViews, final int repeatMode) {
        if (remoteViews == null) {
            return;
        }

        switch (repeatMode) {
            case Player.REPEAT_MODE_OFF:
                remoteViews.setInt(R.id.notificationRepeat,
                        SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_repeat_off);
                break;
            case Player.REPEAT_MODE_ONE:
                remoteViews.setInt(R.id.notificationRepeat,
                        SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_repeat_one);
                break;
            case Player.REPEAT_MODE_ALL:
                remoteViews.setInt(R.id.notificationRepeat,
                        SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_repeat_all);
                break;
        }
    }

    private Intent getIntentForNotification() {
        final Intent intent;
        if (playerImpl.audioPlayerSelected() || playerImpl.popupPlayerSelected()) {
            // Means we play in popup or audio only. Let's show BackgroundPlayerActivity
            intent = NavigationHelper.getBackgroundPlayerActivityIntent(getApplicationContext());
        } else {
            // We are playing in fragment. Don't open another activity just show fragment. That's it
            intent = NavigationHelper.getPlayerIntent(this, MainActivity.class, null, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
        }
        return intent;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Getters
    //////////////////////////////////////////////////////////////////////////*/

    NotificationCompat.Builder getNotBuilder() {
        return notBuilder;
    }

    RemoteViews getBigNotRemoteView() {
        return bigNotRemoteView;
    }

    RemoteViews getNotRemoteView() {
        return notRemoteView;
    }


    public class LocalBinder extends Binder {

        public MainPlayer getService() {
            return MainPlayer.this;
        }

        public VideoPlayerImpl getPlayer() {
            return MainPlayer.this.playerImpl;
        }
    }
}
