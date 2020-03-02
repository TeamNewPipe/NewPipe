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

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.source.MediaSource;
import com.nostra13.universalimageloader.core.assist.FailReason;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.event.PlayerEventListener;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.player.resolver.AudioPlaybackResolver;
import org.schabi.newpipe.player.resolver.MediaSourceTag;
import org.schabi.newpipe.util.ThemeHelper;

import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;

/**
 * Service Background Player implementing {@link VideoPlayer}.
 *
 * @author mauriciocolli
 */
public final class BackgroundPlayer extends Service {
    private static final String TAG = "BackgroundPlayer";
    private static final boolean DEBUG = BasePlayer.DEBUG;

    public static final String ACTION_CLOSE
            = "org.schabi.newpipe.player.BackgroundPlayer.CLOSE";
    public static final String ACTION_PLAY_PAUSE
            = "org.schabi.newpipe.player.BackgroundPlayer.PLAY_PAUSE";
    public static final String ACTION_REPEAT
            = "org.schabi.newpipe.player.BackgroundPlayer.REPEAT";
    public static final String ACTION_PLAY_NEXT
            = "org.schabi.newpipe.player.BackgroundPlayer.ACTION_PLAY_NEXT";
    public static final String ACTION_PLAY_PREVIOUS
            = "org.schabi.newpipe.player.BackgroundPlayer.ACTION_PLAY_PREVIOUS";
    public static final String ACTION_FAST_REWIND
            = "org.schabi.newpipe.player.BackgroundPlayer.ACTION_FAST_REWIND";
    public static final String ACTION_FAST_FORWARD
            = "org.schabi.newpipe.player.BackgroundPlayer.ACTION_FAST_FORWARD";
    public static final String ACTION_BUFFERING
            = "org.schabi.newpipe.player.BackgroundPlayer.ACTION_BUFFERING";
    public static final String ACTION_SHUFFLE
            = "org.schabi.newpipe.player.BackgroundPlayer.ACTION_SHUFFLE";

    public static final String SET_IMAGE_RESOURCE_METHOD = "setImageResource";


    private BasePlayerImpl basePlayerImpl;
    private SharedPreferences sharedPreferences;

    private boolean shouldUpdateOnProgress; // only used for old notifications
    private boolean isForwardPressed;
    private boolean isRewindPressed;

    /*//////////////////////////////////////////////////////////////////////////
    // Service-Activity Binder
    //////////////////////////////////////////////////////////////////////////*/

    private PlayerEventListener activityListener;
    private IBinder mBinder;

    /*//////////////////////////////////////////////////////////////////////////
    // Service's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate() {
        if (DEBUG) {
            Log.d(TAG, "onCreate() called");
        }

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        assureCorrectAppLanguage(this);
        ThemeHelper.setTheme(this);
        basePlayerImpl = new BasePlayerImpl(this);
        basePlayerImpl.setup();

        mBinder = new PlayerServiceBinder(basePlayerImpl);
        shouldUpdateOnProgress = true;
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        if (DEBUG) {
            Log.d(TAG, "N_ onStartCommand() called with: intent = [" + intent + "], "
                    + "flags = [" + flags + "], startId = [" + startId + "]");
        }
        basePlayerImpl.handleIntent(intent);
        if (basePlayerImpl.mediaSessionManager != null) {
            basePlayerImpl.mediaSessionManager.handleMediaButtonIntent(intent);
        }
        return START_NOT_STICKY;
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
            Log.d(TAG, "N_ onClose() called");
        }

        if (basePlayerImpl != null) {
            basePlayerImpl.savePlaybackState();
            basePlayerImpl.stopActivityBinding();
            basePlayerImpl.destroy();
        }
        NotificationUtil.getInstance()
                .cancelNotification(NotificationUtil.NOTIFICATION_ID_BACKGROUND);
        mBinder = null;
        basePlayerImpl = null;

        stopForeground(true);
        stopSelf();
    }

    private void onScreenOnOff(final boolean on) {
        if (DEBUG) {
            Log.d(TAG, "onScreenOnOff() called with: on = [" + on + "]");
        }
        shouldUpdateOnProgress = on;
        basePlayerImpl.triggerProgressUpdate();
        if (on) {
            basePlayerImpl.startProgressLoop();
        } else {
            basePlayerImpl.stopProgressLoop();
        }
    }

    protected class BasePlayerImpl extends BasePlayer {
        @NonNull
        private final AudioPlaybackResolver resolver;

        BasePlayerImpl(final Context context) {
            super(context);
            this.resolver = new AudioPlaybackResolver(context, dataSource);
        }

        @Override
        public void initPlayer(final boolean playOnReady) {
            super.initPlayer(playOnReady);
        }

        @Override
        public void handleIntent(final Intent intent) {
            super.handleIntent(intent);
            if (DEBUG) {
                Log.d(TAG, "N_ handleIntent()");
            }
            NotificationUtil.getInstance().recreateBackgroundPlayerNotification(context,
                    basePlayerImpl.mediaSessionManager.getSessionToken(), basePlayerImpl,
                    sharedPreferences, true); // false
            NotificationUtil.getInstance().setProgressbarOnOldNotifications(100, 0, false);
            startForeground(NotificationUtil.NOTIFICATION_ID_BACKGROUND,
                    NotificationUtil.getInstance().notificationBuilder.build());
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Thumbnail Loading
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        public void onLoadingComplete(final String imageUri, final View view,
                                      final Bitmap loadedImage) {
            super.onLoadingComplete(imageUri, view, loadedImage);
            if (DEBUG) {
                Log.d(TAG, "N_ onLoadingComplete()");
            }
            NotificationUtil.getInstance().recreateBackgroundPlayerNotification(context,
                    basePlayerImpl.mediaSessionManager.getSessionToken(), basePlayerImpl,
                    sharedPreferences, true); //true
            NotificationUtil.getInstance().updateOldNotificationsThumbnail(basePlayerImpl);
            NotificationUtil.getInstance().updateBackgroundPlayerNotification(-1,
                    getBaseContext(), basePlayerImpl, sharedPreferences);
        }

        @Override
        public void onLoadingFailed(final String imageUri, final View view,
                                    final FailReason failReason) {
            super.onLoadingFailed(imageUri, view, failReason);
            if (DEBUG) {
                Log.d(TAG, "N_ onLoadingFailed()");
            }
            NotificationUtil.getInstance().recreateBackgroundPlayerNotification(context,
                    basePlayerImpl.mediaSessionManager.getSessionToken(), basePlayerImpl,
                    sharedPreferences, true); //true
            NotificationUtil.getInstance().updateOldNotificationsThumbnail(basePlayerImpl);
            NotificationUtil.getInstance().updateBackgroundPlayerNotification(-1,
                    getBaseContext(), basePlayerImpl, sharedPreferences);
        }

        /*//////////////////////////////////////////////////////////////////////////
        // States Implementation
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        public void onPrepared(final boolean playWhenReady) {
            super.onPrepared(playWhenReady);
        }

        @Override
        public void onShuffleClicked() {
            super.onShuffleClicked();
            if (DEBUG) {
                Log.d(TAG, "N_ onShuffleClicked:");
            }
            NotificationUtil.getInstance().recreateBackgroundPlayerNotification(context,
                    basePlayerImpl.mediaSessionManager.getSessionToken(), basePlayerImpl,
                    sharedPreferences);
            NotificationUtil.getInstance().updateBackgroundPlayerNotification(-1,
                    getBaseContext(), basePlayerImpl, sharedPreferences);
            updatePlayback();
        }

        @Override
        public void onMuteUnmuteButtonClicked() {
            super.onMuteUnmuteButtonClicked();
            updatePlayback();
        }

        @Override
        public void onUpdateProgress(final int currentProgress, final int duration,
                                     final int bufferPercent) {
            updateProgress(currentProgress, duration, bufferPercent);

            // setMetadata only updates the metadata when any of the metadata keys are null
            basePlayerImpl.mediaSessionManager.setMetadata(basePlayerImpl.getVideoTitle(),
                    basePlayerImpl.getUploaderName(), basePlayerImpl.getThumbnail(), duration);

            boolean areOldNotificationsEnabled = sharedPreferences.getBoolean(
                    getString(R.string.enable_old_notifications_key), false);
            if (areOldNotificationsEnabled) {
                if (!shouldUpdateOnProgress) {
                    return;
                }
                if (NotificationUtil.timesNotificationUpdated
                        > NotificationUtil.NOTIFICATION_UPDATES_BEFORE_RESET) {
                    NotificationUtil.getInstance().recreateBackgroundPlayerNotification(context,
                            basePlayerImpl.mediaSessionManager.getSessionToken(), basePlayerImpl,
                            sharedPreferences);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        NotificationUtil.getInstance()
                                .updateOldNotificationsThumbnail(basePlayerImpl);
                    }
                }

                NotificationUtil.getInstance().setCachedDuration(currentProgress, duration);
                NotificationUtil.getInstance().setProgressbarOnOldNotifications(duration,
                        currentProgress, false);

                NotificationUtil.getInstance().updateBackgroundPlayerNotification(-1,
                        getBaseContext(), basePlayerImpl, sharedPreferences);
            }
        }

        @Override
        public void onPlayPrevious() {
            super.onPlayPrevious();
            triggerProgressUpdate();
        }

        @Override
        public void onPlayNext() {
            super.onPlayNext();
            triggerProgressUpdate();
        }

        @Override
        public void destroy() {
            super.destroy();
            NotificationUtil.getInstance().unsetImageInOldNotifications();
        }

        /*//////////////////////////////////////////////////////////////////////////
        // ExoPlayer Listener
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        public void onPlaybackParametersChanged(final PlaybackParameters playbackParameters) {
            super.onPlaybackParametersChanged(playbackParameters);
            updatePlayback();
        }

        @Override
        public void onLoadingChanged(final boolean isLoading) {
            // Disable default behavior
        }

        @Override
        public void onRepeatModeChanged(final int i) {
            if (DEBUG) {
                Log.d(TAG, "N_ onRepeatModeChanged()");
            }
            NotificationUtil.getInstance().recreateBackgroundPlayerNotification(context,
                    basePlayerImpl.mediaSessionManager.getSessionToken(), basePlayerImpl,
                    sharedPreferences);
            NotificationUtil.getInstance().updateBackgroundPlayerNotification(-1,
                    getBaseContext(), basePlayerImpl, sharedPreferences);
            updatePlayback();
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Playback Listener
        //////////////////////////////////////////////////////////////////////////*/

        protected void onMetadataChanged(@NonNull final MediaSourceTag tag) {
            super.onMetadataChanged(tag);
            if (DEBUG) {
                Log.d(TAG, "N_ onMetadataChanged()");
            }
            NotificationUtil.getInstance().recreateBackgroundPlayerNotification(context,
                    basePlayerImpl.mediaSessionManager.getSessionToken(), basePlayerImpl,
                    sharedPreferences);
            NotificationUtil.getInstance().updateOldNotificationsThumbnail(basePlayerImpl);
            NotificationUtil.getInstance().updateBackgroundPlayerNotification(-1,
                    getBaseContext(), basePlayerImpl, sharedPreferences);
            updateMetadata();
        }

        @Override
        @Nullable
        public MediaSource sourceOf(final PlayQueueItem item, final StreamInfo info) {
            return resolver.resolve(info);
        }

        @Override
        public void onPlaybackShutdown() {
            super.onPlaybackShutdown();
            onClose();
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Activity Event Listener
        //////////////////////////////////////////////////////////////////////////*/

        /*package-private*/ void setActivityListener(final PlayerEventListener listener) {
            activityListener = listener;
            updateMetadata();
            updatePlayback();
            triggerProgressUpdate();
        }

        /*package-private*/ void removeActivityListener(final PlayerEventListener listener) {
            if (activityListener == listener) {
                activityListener = null;
            }
        }

        private void updateMetadata() {
            if (activityListener != null && getCurrentMetadata() != null) {
                activityListener.onMetadataUpdate(getCurrentMetadata().getMetadata());
            }
        }

        private void updatePlayback() {
            if (activityListener != null && simpleExoPlayer != null && playQueue != null) {
                activityListener.onPlaybackUpdate(currentState, getRepeatMode(),
                        playQueue.isShuffled(), getPlaybackParameters());
            }
        }

        private void updateProgress(final int currentProgress, final int duration,
                                    final int bufferPercent) {
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
        protected void setupBroadcastReceiver(final IntentFilter intentFilter) {
            super.setupBroadcastReceiver(intentFilter);
            intentFilter.addAction(ACTION_CLOSE);
            intentFilter.addAction(ACTION_PLAY_PAUSE);
            intentFilter.addAction(ACTION_REPEAT);
            intentFilter.addAction(ACTION_PLAY_PREVIOUS);
            intentFilter.addAction(ACTION_PLAY_NEXT);
            intentFilter.addAction(ACTION_FAST_REWIND);
            intentFilter.addAction(ACTION_FAST_FORWARD);
            intentFilter.addAction(ACTION_BUFFERING);
            intentFilter.addAction(ACTION_SHUFFLE);
            intentFilter.addAction(Intent.ACTION_SCREEN_ON);
            intentFilter.addAction(Intent.ACTION_SCREEN_OFF);

            intentFilter.addAction(Intent.ACTION_HEADSET_PLUG);
        }

        @Override
        public void onBroadcastReceived(final Intent intent) {
            super.onBroadcastReceived(intent);

            if (intent == null || intent.getAction() == null) {
                return;
            }

            if (DEBUG) {
                // FIXME remove N_
                Log.d(TAG, "N_ onBroadcastReceived() called with: intent = [" + intent + "]");
            }

            switch (intent.getAction()) {
                case ACTION_CLOSE:
                    onClose();
                    break;
                case ACTION_PLAY_PAUSE:
                    onPlayPause();
                    break;
                case ACTION_REPEAT:
                    onRepeatClicked();
                    break;
                case ACTION_PLAY_NEXT:
                    onPlayNext();
                    break;
                case ACTION_PLAY_PREVIOUS:
                    onPlayPrevious();
                    break;
                case ACTION_FAST_FORWARD:
                    isForwardPressed = true;
                    onFastForward();
                    break;
                case ACTION_FAST_REWIND:
                    isRewindPressed = true;
                    onFastRewind();
                    break;
                case Intent.ACTION_SCREEN_ON:
                    onScreenOnOff(true);
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    onScreenOnOff(false);
                    break;
                case ACTION_BUFFERING:
                    onBuffering();
                    break;
                case ACTION_SHUFFLE:
                    onShuffleClicked();
                    break;
                case "android.intent.action.HEADSET_PLUG": //FIXME
                    /*notificationManager.cancel(NOTIFICATION_ID);
                    mediaSessionManager.dispose();
                    mediaSessionManager.enable(getBaseContext(), basePlayerImpl.simpleExoPlayer);*/
                    break;
            }
        }

        /*//////////////////////////////////////////////////////////////////////////
        // States
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        public void onBuffering() {
            super.onBuffering();
            if (NotificationUtil.getInstance().notificationSlot0.contains("buffering")
                    || NotificationUtil.getInstance().notificationSlot1.contains("buffering")
                    || NotificationUtil.getInstance().notificationSlot2.contains("buffering")
                    || NotificationUtil.getInstance().notificationSlot3.contains("buffering")
                    || NotificationUtil.getInstance().notificationSlot4.contains("buffering")) {
                if (basePlayerImpl.getCurrentState() == BasePlayer.STATE_PREFLIGHT
                        || basePlayerImpl.getCurrentState() == BasePlayer.STATE_BLOCKED
                        || basePlayerImpl.getCurrentState() == BasePlayer.STATE_BUFFERING) {
                    if (!(isForwardPressed || isRewindPressed)) {
                        if (DEBUG) {
                            Log.d(TAG, "N_ onBuffering()");
                        }
                        NotificationUtil.getInstance().updateBackgroundPlayerNotification(-1,
                                getBaseContext(), basePlayerImpl, sharedPreferences);
                    } else {
                        isForwardPressed = false;
                        isRewindPressed = false;
                    }
                }
            }
        }

        @Override
        public void changeState(final int state) {
            super.changeState(state);
            updatePlayback();
        }

        @Override
        public void onPlaying() {
            super.onPlaying();

            if (DEBUG) {
                Log.d(TAG, "N_ onPlaying()");
            }
            NotificationUtil.getInstance().recreateBackgroundPlayerNotification(context,
                    basePlayerImpl.mediaSessionManager.getSessionToken(), basePlayerImpl,
                    sharedPreferences);
            NotificationUtil.getInstance().updateOldNotificationsThumbnail(basePlayerImpl);
            NotificationUtil.getInstance()
                    .updateBackgroundPlayerNotification(R.drawable.ic_pause_white_24dp,
                            getBaseContext(), basePlayerImpl, sharedPreferences);
        }

        @Override
        public void onPaused() {
            super.onPaused();

            if (DEBUG) {
                Log.d(TAG, "N_ onPaused()");
            }
            NotificationUtil.getInstance().recreateBackgroundPlayerNotification(context,
                    basePlayerImpl.mediaSessionManager.getSessionToken(), basePlayerImpl,
                    sharedPreferences);
            NotificationUtil.getInstance().updateOldNotificationsThumbnail(basePlayerImpl);
            NotificationUtil.getInstance()
                    .updateBackgroundPlayerNotification(R.drawable.ic_play_arrow_white_24dp,
                            getBaseContext(), basePlayerImpl, sharedPreferences);
        }

        @Override
        public void onCompleted() {
            super.onCompleted();
            if (DEBUG) {
                Log.d(TAG, "N_ onCompleted()");
            }

            NotificationUtil.getInstance().recreateBackgroundPlayerNotification(context,
                    basePlayerImpl.mediaSessionManager.getSessionToken(), basePlayerImpl,
                    sharedPreferences);
            NotificationUtil.getInstance().setProgressbarOnOldNotifications(100, 100, false);
            NotificationUtil.getInstance().updateOldNotificationsThumbnail(basePlayerImpl);
            NotificationUtil.getInstance()
                    .updateBackgroundPlayerNotification(R.drawable.ic_replay_white_24dp,
                            getBaseContext(), basePlayerImpl, sharedPreferences);
        }
    }
}
