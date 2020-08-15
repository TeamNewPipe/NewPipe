package org.schabi.newpipe.player;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Build;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.util.NavigationHelper;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.content.Context.NOTIFICATION_SERVICE;
import static com.google.android.exoplayer2.Player.REPEAT_MODE_ALL;
import static com.google.android.exoplayer2.Player.REPEAT_MODE_ONE;
import static org.schabi.newpipe.player.MainPlayer.ACTION_BUFFERING;
import static org.schabi.newpipe.player.MainPlayer.ACTION_CLOSE;
import static org.schabi.newpipe.player.MainPlayer.ACTION_FAST_FORWARD;
import static org.schabi.newpipe.player.MainPlayer.ACTION_FAST_REWIND;
import static org.schabi.newpipe.player.MainPlayer.ACTION_PLAY_NEXT;
import static org.schabi.newpipe.player.MainPlayer.ACTION_PLAY_PAUSE;
import static org.schabi.newpipe.player.MainPlayer.ACTION_PLAY_PREVIOUS;
import static org.schabi.newpipe.player.MainPlayer.ACTION_REPEAT;
import static org.schabi.newpipe.player.MainPlayer.ACTION_SHUFFLE;
import static org.schabi.newpipe.player.MainPlayer.SET_IMAGE_RESOURCE_METHOD;
import static org.schabi.newpipe.player.helper.PlayerHelper.getTimeString;

/**
 * This is a utility class for player notifications.
 *
 * @author cool-student
 */
public final class NotificationUtil {
    private static final String TAG = "NotificationUtil";
    private static final boolean DEBUG = BasePlayer.DEBUG;
    private static final int NOTIFICATION_ID = 123789;
    // only used for old notifications
    private static final int NOTIFICATION_UPDATES_BEFORE_RESET = 60;

    @Nullable private static NotificationUtil instance = null;

    private String notificationSlot0 = "smart_rewind_prev";
    private String notificationSlot1 = "play_pause_buffering";
    private String notificationSlot2 = "smart_forward_next";
    private String notificationSlot3 = "repeat";
    private String notificationSlot4 = "close";

    private NotificationManager notificationManager;
    private RemoteViews notificationRemoteView; // always null when new notifications are used
    private RemoteViews bigNotificationRemoteView; // always null when new notifications are used
    private NotificationCompat.Builder notificationBuilder;

    private int cachedDuration; // only used for old notifications
    private String cachedDurationString; // only used for old notifications
    private int timesNotificationUpdated; // only used for old notifications

    private NotificationUtil() {
    }

    public static NotificationUtil getInstance() {
        if (instance == null) {
            instance = new NotificationUtil();
        }
        return instance;
    }


    /////////////////////////////////////////////////////
    // NOTIFICATION
    /////////////////////////////////////////////////////

    NotificationCompat.Builder createNotification(final VideoPlayerImpl player) {
        notificationManager =
                (NotificationManager) player.context.getSystemService(NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(player.context,
                player.context.getString(R.string.notification_channel_id));

        final boolean areOldNotificationsEnabled = player.sharedPreferences.getBoolean(
                player.context.getString(R.string.enable_old_notifications_key), false);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || areOldNotificationsEnabled) {
            notificationRemoteView = new RemoteViews(BuildConfig.APPLICATION_ID,
                    R.layout.player_notification);
            bigNotificationRemoteView = new RemoteViews(BuildConfig.APPLICATION_ID,
                    R.layout.player_notification_expanded);

            setupOldNotification(notificationRemoteView, player);
            setupOldNotification(bigNotificationRemoteView, player);

            builder
                    .setOngoing(true)
                    .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setCustomContentView(notificationRemoteView)
                    .setCustomBigContentView(bigNotificationRemoteView)
                    .setPriority(NotificationCompat.PRIORITY_MAX);
        } else {
            final String compactView = player.sharedPreferences.getString(player.context.getString(
                    R.string.settings_notifications_compact_view_key), "0,1,2");
            int compactSlot0 = 0;
            int compactSlot1 = 1;
            int compactSlot2 = 2;
            try {
                if (compactView != null) {
                    final String[] parts = compactView.split(",");
                    compactSlot0 = Integer.parseInt(parts[0]);
                    compactSlot1 = Integer.parseInt(parts[1]);
                    compactSlot2 = Integer.parseInt(parts[2]);
                    if (compactSlot0 > 4) {
                        compactSlot0 = 0;
                    }
                    if (compactSlot1 > 4) {
                        compactSlot1 = 1;
                    }
                    if (compactSlot2 > 4) {
                        compactSlot2 = 2;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            builder.setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(player.mediaSessionManager.getSessionToken())
                    .setShowCancelButton(false)
                    .setShowActionsInCompactView(compactSlot0, compactSlot1, compactSlot2))
                    .setOngoing(false)
                    .setContentIntent(PendingIntent.getActivity(player.context, NOTIFICATION_ID,
                            getIntentForNotification(player), FLAG_UPDATE_CURRENT))
                    .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setContentTitle(player.getVideoTitle())
                    .setContentText(player.getUploaderName())
                    .setDeleteIntent(PendingIntent.getActivity(player.context, NOTIFICATION_ID,
                            new Intent(ACTION_CLOSE), FLAG_UPDATE_CURRENT))
                    .setColor(ContextCompat.getColor(player.context, R.color.gray))
                    .setPriority(NotificationCompat.PRIORITY_HIGH);
            final boolean scaleImageToSquareAspectRatio = player.sharedPreferences.getBoolean(
                    player.context.getString(R.string.scale_to_square_image_in_notifications_key),
                    false);
            if (scaleImageToSquareAspectRatio) {
                builder.setLargeIcon(getBitmapWithSquareAspectRatio(player.getThumbnail()));
            } else {
                builder.setLargeIcon(player.getThumbnail());
            }

            notificationSlot0 = player.sharedPreferences.getString(
                    player.context.getString(R.string.notification_slot_0_key), notificationSlot0);
            notificationSlot1 = player.sharedPreferences.getString(
                    player.context.getString(R.string.notification_slot_1_key), notificationSlot1);
            notificationSlot2 = player.sharedPreferences.getString(
                    player.context.getString(R.string.notification_slot_2_key), notificationSlot2);
            notificationSlot3 = player.sharedPreferences.getString(
                    player.context.getString(R.string.notification_slot_3_key), notificationSlot3);
            notificationSlot4 = player.sharedPreferences.getString(
                    player.context.getString(R.string.notification_slot_4_key), notificationSlot4);

            addAction(builder, player, notificationSlot0);
            addAction(builder, player, notificationSlot1);
            addAction(builder, player, notificationSlot2);
            addAction(builder, player, notificationSlot3);
            addAction(builder, player, notificationSlot4);
        }

        return builder;
    }

    /**
     * Updates the notification, and the button icons depending on the playback state.
     * On old notifications used for changes on the remoteView
     *
     * @param player the player currently open, to take data from
     * @param playPauseDrawable if != -1, sets the drawable with that id on the play/pause button
     */
    synchronized void updateNotification(final VideoPlayerImpl player,
                                         @DrawableRes final int playPauseDrawable) {
        if (DEBUG) {
            Log.d(TAG, "N_ updateNotification()");
        }

        if (notificationBuilder == null) {
            return;
        }
        if (playPauseDrawable != -1) {
            if (notificationRemoteView != null) {
                notificationRemoteView
                        .setImageViewResource(R.id.notificationPlayPause, playPauseDrawable);
            }
            if (bigNotificationRemoteView != null) {
                bigNotificationRemoteView
                        .setImageViewResource(R.id.notificationPlayPause, playPauseDrawable);
            }
        }

        final boolean areOldNotificationsEnabled = player.sharedPreferences.getBoolean(
                player.context.getString(R.string.enable_old_notifications_key), false);
        if (!areOldNotificationsEnabled) {
            notificationBuilder.setContentTitle(player.getVideoTitle());
            notificationBuilder.setContentText(player.getUploaderName());
            final boolean scaleImageToSquareAspectRatio = player.sharedPreferences.getBoolean(
                    player.context.getString(R.string.scale_to_square_image_in_notifications_key),
                    false);
            if (scaleImageToSquareAspectRatio) {
                notificationBuilder.setLargeIcon(
                        getBitmapWithSquareAspectRatio(player.getThumbnail()));
            } else {
                notificationBuilder.setLargeIcon(player.getThumbnail());
            }

            setAction(player, notificationSlot0, 0);
            setAction(player, notificationSlot1, 1);
            setAction(player, notificationSlot2, 2);
            setAction(player, notificationSlot3, 3);
            setAction(player, notificationSlot4, 4);
        }

        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());

        if (areOldNotificationsEnabled) {
            timesNotificationUpdated++;
        }
    }

    void recreateNotification(final VideoPlayerImpl player, final boolean recreate) {
        final boolean areOldNotificationsEnabled = player.sharedPreferences.getBoolean(
                player.context.getString(R.string.enable_old_notifications_key), false);
        if (notificationBuilder == null || recreate || areOldNotificationsEnabled) {
            if (DEBUG) {
                Log.d(TAG, "N_ recreateNotification(true)");
            }
            notificationBuilder = createNotification(player);
        }
        timesNotificationUpdated = 0;
    }


    void startForegroundServiceWithNotification(final Service service) {
        service.startForeground(NOTIFICATION_ID, notificationBuilder.build());
    }


    boolean hasSlotWithBuffering() {
        return notificationSlot0.contains("buffering")
                || notificationSlot1.contains("buffering")
                || notificationSlot2.contains("buffering")
                || notificationSlot3.contains("buffering")
                || notificationSlot4.contains("buffering");
    }


    /////////////////////////////////////////////////////
    // OLD NOTIFICATION
    /////////////////////////////////////////////////////

    @Deprecated
    boolean shouldRecreateOldNotification() {
        return timesNotificationUpdated > NOTIFICATION_UPDATES_BEFORE_RESET;
    }

    /**
     * @param bitmap if null, the thumbnail will be removed
     */
    @Deprecated // only used for old notifications
    void updateOldNotificationsThumbnail(@Nullable final Bitmap bitmap) {
        if (notificationRemoteView != null) {
            notificationRemoteView.setImageViewBitmap(R.id.notificationCover, bitmap);
        }
        if (bigNotificationRemoteView != null) {
            bigNotificationRemoteView.setImageViewBitmap(R.id.notificationCover, bitmap);
        }
    }

    @Deprecated // only used for old notifications
    void setProgressbarOnOldNotifications(final int max, final int progress,
                                          final boolean indeterminate) {
        if (bigNotificationRemoteView != null) { //FIXME put in Util and turn into a method
            bigNotificationRemoteView.setProgressBar(R.id.notificationProgressBar, max, progress,
                    indeterminate);
        }
        if (notificationRemoteView != null) {
            notificationRemoteView.setProgressBar(R.id.notificationProgressBar, max, progress,
                    indeterminate);
        }
    }

    @Deprecated // only used for old notifications
    void setCachedDuration(final int currentProgress, final int duration) {
        if (bigNotificationRemoteView != null) {
            if (cachedDuration != duration) {
                cachedDuration = duration;
                cachedDurationString = getTimeString(duration);
            }
            bigNotificationRemoteView.setTextViewText(R.id.notificationTime,
                    getTimeString(currentProgress) + " / " + cachedDurationString);
        }
    }

    public void cancelNotification() {
        try {
            if (notificationManager != null) {
                notificationManager.cancel(NOTIFICATION_ID);
            }
        } catch (Exception e) {
            Log.e("NotificationUtil", "Exception", e);
        }
    }


    /////////////////////////////////////////////////////
    // OLD NOTIFICATION UTILS
    /////////////////////////////////////////////////////

    @Deprecated // only used for old notifications
    private void setupOldNotification(final RemoteViews remoteViews,
                                      final VideoPlayerImpl player) {
        remoteViews.setTextViewText(R.id.notificationSongName, player.getVideoTitle());
        remoteViews.setTextViewText(R.id.notificationArtist, player.getUploaderName());
        remoteViews.setImageViewBitmap(R.id.notificationCover, player.getThumbnail());

        remoteViews.setOnClickPendingIntent(R.id.notificationPlayPause,
                PendingIntent.getBroadcast(player.context, NOTIFICATION_ID,
                        new Intent(ACTION_PLAY_PAUSE), FLAG_UPDATE_CURRENT));
        remoteViews.setOnClickPendingIntent(R.id.notificationStop,
                PendingIntent.getBroadcast(player.context, NOTIFICATION_ID,
                        new Intent(ACTION_CLOSE), FLAG_UPDATE_CURRENT));
        remoteViews.setOnClickPendingIntent(R.id.notificationRepeat,
                PendingIntent.getBroadcast(player.context, NOTIFICATION_ID,
                        new Intent(ACTION_REPEAT), FLAG_UPDATE_CURRENT));
        remoteViews.setOnClickPendingIntent(R.id.notificationContent,
                PendingIntent.getBroadcast(player.context, NOTIFICATION_ID,
                        getIntentForNotification(player), FLAG_UPDATE_CURRENT));

        if (player.playQueue != null && player.playQueue.size() > 1) {
            remoteViews.setInt(R.id.notificationFRewind,
                    SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_previous);
            remoteViews.setInt(R.id.notificationFForward,
                    SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_next);
            remoteViews.setOnClickPendingIntent(R.id.notificationFRewind,
                    PendingIntent.getBroadcast(player.context, NOTIFICATION_ID,
                            new Intent(ACTION_PLAY_PREVIOUS), FLAG_UPDATE_CURRENT));
            remoteViews.setOnClickPendingIntent(R.id.notificationFForward,
                    PendingIntent.getBroadcast(player.context, NOTIFICATION_ID,
                            new Intent(ACTION_PLAY_NEXT), FLAG_UPDATE_CURRENT));
        } else {
            remoteViews.setInt(R.id.notificationFRewind,
                    SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_rewind);
            remoteViews.setInt(R.id.notificationFForward,
                    SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_fastforward);
            remoteViews.setOnClickPendingIntent(R.id.notificationFRewind,
                    PendingIntent.getBroadcast(player.context, NOTIFICATION_ID,
                            new Intent(ACTION_FAST_REWIND), FLAG_UPDATE_CURRENT));
            remoteViews.setOnClickPendingIntent(R.id.notificationFForward,
                    PendingIntent.getBroadcast(player.context, NOTIFICATION_ID,
                            new Intent(ACTION_FAST_FORWARD), FLAG_UPDATE_CURRENT));
        }

        setRepeatModeIcon(remoteViews, player.getRepeatMode());
    }

    @Deprecated // only used for old notifications
    private void setRepeatModeIcon(final RemoteViews remoteViews, final int repeatMode) {
        remoteViews.setInt(R.id.notificationRepeat, SET_IMAGE_RESOURCE_METHOD,
                getRepeatModeDrawable(repeatMode));
    }


    /////////////////////////////////////////////////////
    // ACTIONS
    /////////////////////////////////////////////////////

    private void addAction(final NotificationCompat.Builder builder,
                           final VideoPlayerImpl player,
                           final String slot) {
        builder.addAction(getAction(builder, player, slot));
    }

    @SuppressLint("RestrictedApi")
    private void setAction(final VideoPlayerImpl player,
                           final String slot,
                           final int slotNumber) {
        notificationBuilder.mActions.set(slotNumber, getAction(notificationBuilder, player, slot));
    }

    private NotificationCompat.Action getAction(final NotificationCompat.Builder builder,
                                                final VideoPlayerImpl player,
                                                final String slot) {
        switch (slot) {
            case "play_pause_buffering":
                if (player.getCurrentState() == BasePlayer.STATE_PREFLIGHT
                        || player.getCurrentState() == BasePlayer.STATE_BLOCKED
                        || player.getCurrentState() == BasePlayer.STATE_BUFFERING) {
                    builder.setSmallIcon(android.R.drawable.stat_sys_download);
                    return getAction(builder, player, R.drawable.ic_file_download_white_24dp,
                            "Buffering", ACTION_BUFFERING);
                } else {
                    builder.setSmallIcon(R.drawable.ic_newpipe_triangle_white);
                    return getAction(builder, player,
                            player.isPlaying() ? R.drawable.exo_notification_pause
                                    : R.drawable.exo_notification_play,
                            player.isPlaying() ? "Pause" : "Play",
                            ACTION_PLAY_PAUSE);
                }
            case "play_pause":
                final boolean pauseOrPlay = player.isPlaying()
                        || player.getCurrentState() == BasePlayer.STATE_PREFLIGHT
                        || player.getCurrentState() == BasePlayer.STATE_BLOCKED
                        || player.getCurrentState() == BasePlayer.STATE_BUFFERING;
                return getAction(builder, player,
                        pauseOrPlay ? R.drawable.exo_notification_pause
                                : R.drawable.exo_notification_play,
                        pauseOrPlay ? "Pause" : "Play",
                        ACTION_PLAY_PAUSE);
            case "rewind":
                return getAction(builder, player, R.drawable.exo_controls_rewind,
                        "Rewind", ACTION_FAST_REWIND);
            case "smart_rewind_prev":
                if (player.playQueue != null && player.playQueue.size() > 1) {
                    return getAction(builder, player, R.drawable.exo_notification_previous,
                            "Prev", ACTION_PLAY_PREVIOUS);
                } else {
                    return getAction(builder, player, R.drawable.exo_controls_rewind,
                            "Rewind", ACTION_FAST_REWIND);
                }
            case "forward":
                return getAction(builder, player, R.drawable.exo_controls_fastforward,
                        "Forward", ACTION_FAST_FORWARD);
            case "smart_forward_next":
                if (player.playQueue != null && player.playQueue.size() > 1) {
                    return getAction(builder, player, R.drawable.exo_notification_next,
                            "Next", ACTION_PLAY_NEXT);
                } else {
                    return getAction(builder, player, R.drawable.exo_controls_fastforward,
                            "Forward", ACTION_FAST_FORWARD);
                }
            case "next":
                return getAction(builder, player, R.drawable.exo_notification_next,
                        "Next", ACTION_PLAY_NEXT);
            case "prev":
                return getAction(builder, player, R.drawable.exo_notification_previous,
                        "Prev", ACTION_PLAY_PREVIOUS);
            case "repeat":
                return getAction(builder, player, getRepeatModeDrawable(player.getRepeatMode()),
                        getRepeatModeTitle(player.getRepeatMode()), ACTION_REPEAT);
            case "shuffle":
                final boolean shuffled = player.playQueue != null && player.playQueue.isShuffled();
                return getAction(builder, player,
                        shuffled ? R.drawable.exo_controls_shuffle_on
                                : R.drawable.exo_controls_shuffle_off,
                        shuffled ? "ShuffleOn" : "ShuffleOff",
                        ACTION_SHUFFLE);
            case "close":
                return getAction(builder, player, R.drawable.ic_close_white_24dp,
                        "Close", ACTION_CLOSE);
            case "n/a":
            default:
                // do nothing
                return null;
        }
    }

    private NotificationCompat.Action getAction(final NotificationCompat.Builder builder,
                                                final VideoPlayerImpl player,
                                                @DrawableRes final int drawable,
                                                final String title,
                                                final String intentAction) {
        return new NotificationCompat.Action(drawable, title, PendingIntent.getBroadcast(
                player.context, NOTIFICATION_ID, new Intent(intentAction), FLAG_UPDATE_CURRENT));
    }

    @DrawableRes
    private int getRepeatModeDrawable(final int repeatMode) {
        if (repeatMode == REPEAT_MODE_ALL) {
            return R.drawable.exo_controls_repeat_all;
        } else if (repeatMode == REPEAT_MODE_ONE) {
            return R.drawable.exo_controls_repeat_one;
        } else /* repeatMode == REPEAT_MODE_OFF */ {
            return R.drawable.exo_controls_repeat_off;
        }
    }

    private String getRepeatModeTitle(final int repeatMode) {
        if (repeatMode == REPEAT_MODE_ALL) {
            return "RepeatAll";
        } else if (repeatMode == REPEAT_MODE_ONE) {
            return "RepeatOne";
        } else /* repeatMode == REPEAT_MODE_OFF */ {
            return "RepeatOff";
        }
    }

    private Intent getIntentForNotification(final VideoPlayerImpl player) {
        final Intent intent;
        if (player.audioPlayerSelected() || player.popupPlayerSelected()) {
            // Means we play in popup or audio only. Let's show BackgroundPlayerActivity
            intent = NavigationHelper.getBackgroundPlayerActivityIntent(player.context);
        } else {
            // We are playing in fragment. Don't open another activity just show fragment. That's it
            intent = NavigationHelper.getPlayerIntent(
                    player.context, MainActivity.class, null, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
        }
        return intent;
    }


    /////////////////////////////////////////////////////
    // BITMAP
    /////////////////////////////////////////////////////

    private Bitmap getBitmapWithSquareAspectRatio(final Bitmap bitmap) {
        return getResizedBitmap(bitmap, bitmap.getWidth(), bitmap.getWidth());
    }

    private Bitmap getResizedBitmap(final Bitmap bitmap, final int newWidth, final int newHeight) {
        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();
        final float scaleWidth = ((float) newWidth) / width;
        final float scaleHeight = ((float) newHeight) / height;
        final Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, false);
    }
}
