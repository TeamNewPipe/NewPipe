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

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.App;
import org.schabi.newpipe.R;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.ThemeHelper;

import javax.inject.Inject;

import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;


/**
 * One service for all players.
 *
 * @author mauriciocolli
 */
public final class MainPlayer extends Service {
    private static final String TAG = "MainPlayer";
    private static final boolean DEBUG = BasePlayer.DEBUG;

    @Inject
    HistoryRecordManager recordManager;

    private VideoPlayerImpl playerImpl;
    private WindowManager windowManager;

    private final IBinder mBinder = new MainPlayer.LocalBinder();

    public enum PlayerType {
        VIDEO,
        AUDIO,
        POPUP
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Notification
    //////////////////////////////////////////////////////////////////////////*/

    static final String ACTION_CLOSE
            = "org.schabi.newpipe.player.MainPlayer.CLOSE";
    static final String ACTION_PLAY_PAUSE
            = "org.schabi.newpipe.player.MainPlayer.PLAY_PAUSE";
    static final String ACTION_OPEN_CONTROLS
            = "org.schabi.newpipe.player.MainPlayer.OPEN_CONTROLS";
    static final String ACTION_REPEAT
            = "org.schabi.newpipe.player.MainPlayer.REPEAT";
    static final String ACTION_PLAY_NEXT
            = "org.schabi.newpipe.player.MainPlayer.ACTION_PLAY_NEXT";
    static final String ACTION_PLAY_PREVIOUS
            = "org.schabi.newpipe.player.MainPlayer.ACTION_PLAY_PREVIOUS";
    static final String ACTION_FAST_REWIND
            = "org.schabi.newpipe.player.MainPlayer.ACTION_FAST_REWIND";
    static final String ACTION_FAST_FORWARD
            = "org.schabi.newpipe.player.MainPlayer.ACTION_FAST_FORWARD";
    static final String ACTION_SHUFFLE
            = "org.schabi.newpipe.player.MainPlayer.ACTION_SHUFFLE";
    public static final String ACTION_RECREATE_NOTIFICATION
            = "org.schabi.newpipe.player.MainPlayer.ACTION_RECREATE_NOTIFICATION";

    /*//////////////////////////////////////////////////////////////////////////
    // Service's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate() {
        if (DEBUG) {
            Log.d(TAG, "onCreate() called");
        }
        App.getApp().getAppComponent().inject(this);
        assureCorrectAppLanguage(this);
        windowManager = ContextCompat.getSystemService(this, WindowManager.class);

        ThemeHelper.setTheme(this);
        createView();
    }

    private void createView() {
        final View layout = View.inflate(this, R.layout.player, null);

        playerImpl = new VideoPlayerImpl(this, recordManager);
        playerImpl.setup(layout);
        playerImpl.shouldUpdateOnProgress = true;

        NotificationUtil.getInstance().createNotificationAndStartForeground(playerImpl, this);
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
            NotificationUtil.getInstance().createNotificationAndStartForeground(playerImpl, this);
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
            playerImpl.onQueueClosed();
            // Notification shows information about old stream but if a user selects
            // a stream from backStack it's not actual anymore
            // So we should hide the notification at all.
            // When autoplay enabled such notification flashing is annoying so skip this case
            if (!autoplayEnabled) {
                NotificationUtil.getInstance().cancelNotificationAndStopForeground(this);
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
            // Exit from fullscreen when user closes the player via notification
            if (playerImpl.isFullscreen()) {
                playerImpl.toggleFullscreen();
            }
            removeViewFromParent();

            playerImpl.setRecovery();
            playerImpl.savePlaybackState();
            playerImpl.stopActivityBinding();
            playerImpl.removePopupFromView();
            playerImpl.destroy();
        }

        NotificationUtil.getInstance().cancelNotificationAndStopForeground(this);
        stopSelf();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    boolean isLandscape() {
        // DisplayMetrics from activity context knows about MultiWindow feature
        // while DisplayMetrics from app context doesn't
        final DisplayMetrics metrics = (playerImpl != null
                && playerImpl.getParentActivity() != null
                ? playerImpl.getParentActivity().getResources()
                : getResources()).getDisplayMetrics();
        return metrics.heightPixels < metrics.widthPixels;
    }

    @Nullable
    public View getView() {
        if (playerImpl == null) {
            return null;
        }

        return playerImpl.getRootView();
    }

    public void removeViewFromParent() {
        if (getView() != null && getView().getParent() != null) {
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


    public class LocalBinder extends Binder {

        public MainPlayer getService() {
            return MainPlayer.this;
        }

        public VideoPlayerImpl getPlayer() {
            return MainPlayer.this.playerImpl;
        }
    }
}
