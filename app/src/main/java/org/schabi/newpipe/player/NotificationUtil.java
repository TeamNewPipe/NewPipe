package org.schabi.newpipe.player;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Build;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.exoplayer2.Player;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.R;
import org.schabi.newpipe.util.NavigationHelper;

import static android.content.Context.NOTIFICATION_SERVICE;
import static org.schabi.newpipe.player.helper.PlayerHelper.getTimeString;

/**
 * This is a utility class for player notifications.
 *
 * @author cool-student
 */
public final class NotificationUtil {
    private static final String TAG = "NotificationUtil";
    private static final boolean DEBUG = BasePlayer.DEBUG;

    public static final int NOTIFICATION_ID_BACKGROUND = 123789;
    public static final int NOTIFICATION_ID_POPUP = 40028922;
    static final int NOTIFICATION_UPDATES_BEFORE_RESET = 60; // only used for old notifications

    static int timesNotificationUpdated; // only used for old notifications

    NotificationCompat.Builder notificationBuilder;

    String notificationSlot0 = "smart_rewind_prev";
    String notificationSlot1 = "play_pause_buffering";
    String notificationSlot2 = "smart_forward_next";
    String notificationSlot3 = "repeat";
    String notificationSlot4 = "close";

    private NotificationManager notificationManager;
    /*private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;*/

    private RemoteViews notificationPopupRemoteView;
    private RemoteViews notificationRemoteView; // always null when new notifications are used
    private RemoteViews bigNotificationRemoteView; // always null when new notifications are used

    private int cachedDuration; // only used for old notifications
    private String cachedDurationString; // only used for old notifications

    private NotificationUtil() { }

    public static NotificationUtil getInstance() {
        return LazyHolder.INSTANCE;
    }

    void recreatePopupPlayerNotification(final Context context,
                                         final MediaSessionCompat.Token mediaSessionCompatToken,
                                         final PopupVideoPlayer.VideoPlayerImpl playerImpl,
                                         final SharedPreferences sharedPreferences,
                                         final boolean recreate) {
        final boolean areOldNotificationsEnabled = sharedPreferences
                .getBoolean(context.getString(R.string.enable_old_notifications_key), false);
        if (areOldNotificationsEnabled) {
            notificationBuilder = createOldPopupPlayerNotification(context, playerImpl);
        } else if (notificationBuilder == null || recreate) {
            if (DEBUG) {
                Log.d(TAG, "N_ recreatePopupPlayerNotification(true)");
            }
            notificationBuilder = createPopupPlayerNotification(context, mediaSessionCompatToken,
                    playerImpl, sharedPreferences);
        }
        timesNotificationUpdated = 0;
    }

    void recreatePopupPlayerNotification(final Context context,
                                         final MediaSessionCompat.Token mediaSessionCompatToken,
                                         final PopupVideoPlayer.VideoPlayerImpl playerImpl,
                                         final SharedPreferences sharedPreferences) {
        final boolean areOldNotificationsEnabled = sharedPreferences
                .getBoolean(context.getString(R.string.enable_old_notifications_key), false);
        if (areOldNotificationsEnabled) {
            notificationBuilder = createOldPopupPlayerNotification(context, playerImpl);
        } else if (notificationBuilder == null) {
            if (DEBUG) {
                Log.d(TAG, "N_ recreatePopupPlayerNotification()");
            }
            notificationBuilder = createPopupPlayerNotification(context,
                    mediaSessionCompatToken, playerImpl, sharedPreferences);
        }
        timesNotificationUpdated = 0;
    }

    void recreateBackgroundPlayerNotification(
            final Context context, final MediaSessionCompat.Token mediaSessionCompatToken,
            final BackgroundPlayer.BasePlayerImpl basePlayerImpl,
            final SharedPreferences sharedPreferences, final boolean recreate) {
        final boolean areOldNotificationsEnabled = sharedPreferences
                .getBoolean(context.getString(R.string.enable_old_notifications_key), false);
        if (notificationBuilder == null || recreate || areOldNotificationsEnabled) {
            if (DEBUG) {
                Log.d(TAG, "N_ recreateBackgroundPlayerNotification(true)");
            }
            notificationBuilder = createBackgroundPlayerNotification(context,
                    mediaSessionCompatToken, basePlayerImpl, sharedPreferences);
        }
        timesNotificationUpdated = 0;
    }

    void recreateBackgroundPlayerNotification(
            final Context context, final MediaSessionCompat.Token mediaSessionCompatToken,
            final BackgroundPlayer.BasePlayerImpl basePlayerImpl,
            final SharedPreferences sharedPreferences) {
        final boolean areOldNotificationsEnabled = sharedPreferences
                .getBoolean(context.getString(R.string.enable_old_notifications_key), false);
        if (notificationBuilder == null || areOldNotificationsEnabled) {
            if (DEBUG) {
                Log.d(TAG, "N_ recreateBackgroundPlayerNotification()");
            }
            notificationBuilder = createBackgroundPlayerNotification(context,
                    mediaSessionCompatToken, basePlayerImpl, sharedPreferences);
        }
        timesNotificationUpdated = 0;
    }

    NotificationCompat.Builder createBackgroundPlayerNotification(
            final Context context, final MediaSessionCompat.Token mediaSessionCompatToken,
            final BackgroundPlayer.BasePlayerImpl basePlayerImpl,
            final SharedPreferences sharedPreferences) {
        notificationManager = ((NotificationManager) context
                .getSystemService(NOTIFICATION_SERVICE));
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                context.getString(R.string.notification_channel_id));

        final boolean areOldNotificationsEnabled = sharedPreferences
                .getBoolean(context.getString(R.string.enable_old_notifications_key), false);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || areOldNotificationsEnabled) {
            notificationRemoteView = new RemoteViews(BuildConfig.APPLICATION_ID,
                    R.layout.player_background_notification);
            bigNotificationRemoteView = new RemoteViews(BuildConfig.APPLICATION_ID,
                    R.layout.player_background_notification_expanded);

            setupOldNotification(notificationRemoteView, context, basePlayerImpl);
            setupOldNotification(bigNotificationRemoteView, context, basePlayerImpl);

            builder
                    .setOngoing(true)
                    .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setCustomContentView(notificationRemoteView)
                    .setCustomBigContentView(bigNotificationRemoteView)
                    .setPriority(NotificationCompat.PRIORITY_MAX);
        } else {
            String compactView = sharedPreferences.getString(context.getString(
                    R.string.settings_notifications_compact_view_key), "0,1,2");
            int compactSlot0;
            int compactSlot1;
            int compactSlot2;
            try {
                String[] parts = compactView.split(",");
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
            } catch (Exception e) {
                e.printStackTrace();
                compactSlot0 = 0;
                compactSlot1 = 1;
                compactSlot2 = 2;
            }

            builder
                    .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                            .setMediaSession(mediaSessionCompatToken)
                            .setShowCancelButton(false)
                            .setShowActionsInCompactView(compactSlot0, compactSlot1, compactSlot2))
                    .setOngoing(false)
                    .setContentIntent(PendingIntent.getActivity(context, NOTIFICATION_ID_BACKGROUND,
                            new Intent(NavigationHelper.getBackgroundPlayerActivityIntent(context)),
                            PendingIntent.FLAG_UPDATE_CURRENT))
                    .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setContentTitle(basePlayerImpl.getVideoTitle())
                    .setContentText(basePlayerImpl.getUploaderName())
                    .setDeleteIntent(PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                            new Intent(BackgroundPlayer.ACTION_CLOSE),
                            PendingIntent.FLAG_UPDATE_CURRENT))
                    .setColor(ContextCompat.getColor(context, R.color.gray))
                    .setPriority(NotificationCompat.PRIORITY_HIGH);
            final boolean scaleImageToSquareAspectRatio = sharedPreferences.getBoolean(context
                    .getString(R.string.scale_to_square_image_in_notifications_key), false);
            if (scaleImageToSquareAspectRatio) {
                builder.setLargeIcon(getBitmapWithSquareAspectRatio(basePlayerImpl.getThumbnail()));
            } else {
                builder.setLargeIcon(basePlayerImpl.getThumbnail());
            }

            notificationSlot0 = sharedPreferences.getString(
                    context.getString(R.string.notification_slot_0_key), notificationSlot0);
            notificationSlot1 = sharedPreferences.getString(
                    context.getString(R.string.notification_slot_1_key), notificationSlot1);
            notificationSlot2 = sharedPreferences.getString(
                    context.getString(R.string.notification_slot_2_key), notificationSlot2);
            notificationSlot3 = sharedPreferences.getString(
                    context.getString(R.string.notification_slot_3_key), notificationSlot3);
            notificationSlot4 = sharedPreferences.getString(
                    context.getString(R.string.notification_slot_4_key), notificationSlot4);

            addAction(context, builder, basePlayerImpl, notificationSlot0);
            addAction(context, builder, basePlayerImpl, notificationSlot1);
            addAction(context, builder, basePlayerImpl, notificationSlot2);
            addAction(context, builder, basePlayerImpl, notificationSlot3);
            addAction(context, builder, basePlayerImpl, notificationSlot4);
        }

        return builder;
    }

    private void addAction(final Context context, final NotificationCompat.Builder builder,
                           final BackgroundPlayer.BasePlayerImpl basePlayerImpl,
                           final String slot) {
        switch (slot) {
            case "play_pause_buffering":
                if (basePlayerImpl.getCurrentState() == BasePlayer.STATE_PREFLIGHT
                        || basePlayerImpl.getCurrentState() == BasePlayer.STATE_BLOCKED
                        || basePlayerImpl.getCurrentState() == BasePlayer.STATE_BUFFERING) {
                    builder.addAction(R.drawable.ic_file_download_white_24dp, "Buffering",
                            PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                                    new Intent(BackgroundPlayer.ACTION_BUFFERING),
                                    PendingIntent.FLAG_UPDATE_CURRENT));
                    builder.setSmallIcon(android.R.drawable.stat_sys_download);
                } else {
                    builder.setSmallIcon(R.drawable.ic_newpipe_triangle_white);
                    if (basePlayerImpl.isPlaying()) {
                        builder.addAction(R.drawable.exo_notification_pause, "Pause",
                                PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                                        new Intent(BackgroundPlayer.ACTION_PLAY_PAUSE),
                                        PendingIntent.FLAG_UPDATE_CURRENT));
                    } else {
                        builder.addAction(R.drawable.exo_notification_play, "Play",
                                PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                                        new Intent(BackgroundPlayer.ACTION_PLAY_PAUSE),
                                        PendingIntent.FLAG_UPDATE_CURRENT));
                    }
                }
                break;
            case "play_pause":
                if (basePlayerImpl.getCurrentState() == BasePlayer.STATE_PREFLIGHT
                        || basePlayerImpl.getCurrentState() == BasePlayer.STATE_BLOCKED
                        || basePlayerImpl.getCurrentState() == BasePlayer.STATE_BUFFERING) {
                    builder.addAction(R.drawable.exo_notification_pause, "Pause",
                            PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                                    new Intent(BackgroundPlayer.ACTION_PLAY_PAUSE),
                                    PendingIntent.FLAG_UPDATE_CURRENT));
                } else if (basePlayerImpl.isPlaying()) {
                    builder.addAction(R.drawable.exo_notification_pause, "Pause",
                            PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                                    new Intent(BackgroundPlayer.ACTION_PLAY_PAUSE),
                                    PendingIntent.FLAG_UPDATE_CURRENT));
                } else {
                    builder.addAction(R.drawable.exo_notification_play, "Play",
                            PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                                    new Intent(BackgroundPlayer.ACTION_PLAY_PAUSE),
                                    PendingIntent.FLAG_UPDATE_CURRENT));
                }
                break;
            case "rewind":
                builder.addAction(R.drawable.exo_controls_rewind, "Rewind",
                        PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                                new Intent(BackgroundPlayer.ACTION_FAST_REWIND),
                                PendingIntent.FLAG_UPDATE_CURRENT));
                break;
            case "smart_rewind_prev":
                if (basePlayerImpl.playQueue != null && basePlayerImpl.playQueue.size() > 1) {
                    builder.addAction(R.drawable.exo_notification_previous, "Prev",
                            PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                                    new Intent(BackgroundPlayer.ACTION_PLAY_PREVIOUS),
                                    PendingIntent.FLAG_UPDATE_CURRENT));
                } else {
                    builder.addAction(R.drawable.exo_controls_rewind, "Rewind",
                            PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                                    new Intent(BackgroundPlayer.ACTION_FAST_REWIND),
                                    PendingIntent.FLAG_UPDATE_CURRENT));
                }
                break;
            case "forward":
                builder.addAction(R.drawable.exo_controls_fastforward, "Forward",
                        PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                                new Intent(BackgroundPlayer.ACTION_FAST_FORWARD),
                                PendingIntent.FLAG_UPDATE_CURRENT));
                break;
            case "smart_forward_next":
                if (basePlayerImpl.playQueue != null && basePlayerImpl.playQueue.size() > 1) {
                    builder.addAction(R.drawable.exo_notification_next, "Next",
                            PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                                    new Intent(BackgroundPlayer.ACTION_PLAY_NEXT),
                                    PendingIntent.FLAG_UPDATE_CURRENT));
                } else {
                    builder.addAction(R.drawable.exo_controls_fastforward, "Forward",
                            PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                                    new Intent(BackgroundPlayer.ACTION_FAST_FORWARD),
                                    PendingIntent.FLAG_UPDATE_CURRENT));
                }
                break;
            case "next":
                builder.addAction(R.drawable.exo_notification_next, "Next",
                        PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                                new Intent(BackgroundPlayer.ACTION_PLAY_NEXT),
                                PendingIntent.FLAG_UPDATE_CURRENT));
                break;
            case "repeat":
                switch (basePlayerImpl.getRepeatMode()) {
                    case Player.REPEAT_MODE_ONE:
                        builder.addAction(R.drawable.exo_controls_repeat_one, "RepeatOne",
                                PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                                        new Intent(BackgroundPlayer.ACTION_REPEAT),
                                        PendingIntent.FLAG_UPDATE_CURRENT));
                        break;
                    case Player.REPEAT_MODE_ALL:
                        builder.addAction(R.drawable.exo_controls_repeat_all, "RepeatAll",
                                PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                                        new Intent(BackgroundPlayer.ACTION_REPEAT),
                                        PendingIntent.FLAG_UPDATE_CURRENT));
                        break;
                    case Player.REPEAT_MODE_OFF:
                    default:
                        builder.addAction(R.drawable.exo_controls_repeat_off, "RepeatOff",
                                PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                                        new Intent(BackgroundPlayer.ACTION_REPEAT),
                                        PendingIntent.FLAG_UPDATE_CURRENT));
                        break;
                }
                break;
            case "shuffle":
                if (basePlayerImpl.playQueue.isShuffled()) {
                    builder.addAction(R.drawable.exo_controls_shuffle_on, "ShuffleOn",
                            PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                                    new Intent(BackgroundPlayer.ACTION_SHUFFLE),
                                    PendingIntent.FLAG_UPDATE_CURRENT));
                } else {
                    builder.addAction(R.drawable.exo_controls_shuffle_off, "ShuffleOff",
                            PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                                    new Intent(BackgroundPlayer.ACTION_SHUFFLE),
                                    PendingIntent.FLAG_UPDATE_CURRENT));
                }
                break;
            case "close":
                builder.addAction(R.drawable.ic_close_white_24dp, "Close",
                        PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                                new Intent(BackgroundPlayer.ACTION_CLOSE),
                                PendingIntent.FLAG_UPDATE_CURRENT));
                break;
            case "n/a":
                // do nothing
                break;
            case "prev":
            default:
                builder.addAction(R.drawable.exo_notification_previous, "Prev",
                        PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                                new Intent(BackgroundPlayer.ACTION_PLAY_PREVIOUS),
                                PendingIntent.FLAG_UPDATE_CURRENT));
                break;
        }
    }

    /**
     * Updates the notification, and the button icons depending on the playback state.
     * On old notifications used for changes on the remoteView
     *
     * @param drawableId if != -1, sets the drawable with that id on the play/pause button
     * @param context
     * @param basePlayerImpl
     * @param sharedPreferences
     */
    synchronized void updateBackgroundPlayerNotification(
            final int drawableId, final Context context,
            final BackgroundPlayer.BasePlayerImpl basePlayerImpl,
            final SharedPreferences sharedPreferences) {
        if (DEBUG) {
            Log.d(TAG, "N_ updateBackgroundPlayerNotification()");
        }

        if (notificationBuilder == null) {
            return;
        }
        if (drawableId != -1) {
            if (notificationRemoteView != null) {
                notificationRemoteView.setImageViewResource(R.id.notificationPlayPause, drawableId);
            }
            if (bigNotificationRemoteView != null) {
                bigNotificationRemoteView
                        .setImageViewResource(R.id.notificationPlayPause, drawableId);
            }
        }

        final boolean areOldNotificationsEnabled = sharedPreferences
                .getBoolean(context.getString(R.string.enable_old_notifications_key), false);
        if (!areOldNotificationsEnabled) {
            notificationBuilder.setContentTitle(basePlayerImpl.getVideoTitle());
            notificationBuilder.setContentText(basePlayerImpl.getUploaderName());
            final boolean scaleImageToSquareAspectRatio = sharedPreferences.getBoolean(
                    context.getString(R.string.scale_to_square_image_in_notifications_key), false);
            if (scaleImageToSquareAspectRatio) {
                notificationBuilder.setLargeIcon(getBitmapWithSquareAspectRatio(basePlayerImpl
                        .getThumbnail()));
            } else {
                notificationBuilder.setLargeIcon(basePlayerImpl.getThumbnail());
            }

            setAction(context, basePlayerImpl, notificationSlot0, 0);
            setAction(context, basePlayerImpl, notificationSlot1, 1);
            setAction(context, basePlayerImpl, notificationSlot2, 2);
            setAction(context, basePlayerImpl, notificationSlot3, 3);
            setAction(context, basePlayerImpl, notificationSlot4, 4);
        }

        notificationManager.notify(NOTIFICATION_ID_BACKGROUND, notificationBuilder.build());

        if (areOldNotificationsEnabled) {
            timesNotificationUpdated++;
        }
    }

    @SuppressLint("RestrictedApi")
    private void setAction(final Context context,
                           final BackgroundPlayer.BasePlayerImpl basePlayerImpl,
                           final String slot, final int slotNumber) {
        switch (slot) {
            case "play_pause_buffering":
                if (basePlayerImpl.getCurrentState() == BasePlayer.STATE_PREFLIGHT
                        || basePlayerImpl.getCurrentState() == BasePlayer.STATE_BLOCKED
                        || basePlayerImpl.getCurrentState() == BasePlayer.STATE_BUFFERING) {
                    notificationBuilder.mActions.set(slotNumber,
                            new NotificationCompat.Action(R.drawable.ic_file_download_white_24dp,
                                    "Buffering", PendingIntent.getBroadcast(context,
                                    NOTIFICATION_ID_BACKGROUND,
                                    new Intent(BackgroundPlayer.ACTION_BUFFERING),
                                    PendingIntent.FLAG_UPDATE_CURRENT)));
                    notificationBuilder.setSmallIcon(android.R.drawable.stat_sys_download);
                } else if (basePlayerImpl.getCurrentState() == BasePlayer.STATE_COMPLETED) {
                    notificationBuilder.mActions.set(slotNumber,
                            new NotificationCompat.Action(R.drawable.ic_replay_white_24dp,
                                    "Completed", PendingIntent.getBroadcast(context,
                                    NOTIFICATION_ID_BACKGROUND,
                                    new Intent(BackgroundPlayer.ACTION_PLAY_PAUSE),
                                    PendingIntent.FLAG_UPDATE_CURRENT)));
                } else {
                    notificationBuilder.setSmallIcon(R.drawable.ic_newpipe_triangle_white);
                    if (basePlayerImpl.isPlaying()) {
                        notificationBuilder.mActions.set(slotNumber,
                                new NotificationCompat.Action(R.drawable.exo_notification_pause,
                                        "Pause", PendingIntent.getBroadcast(context,
                                        NOTIFICATION_ID_BACKGROUND,
                                        new Intent(BackgroundPlayer.ACTION_PLAY_PAUSE),
                                        PendingIntent.FLAG_UPDATE_CURRENT)));
                    } else {
                        notificationBuilder.mActions.set(slotNumber,
                                new NotificationCompat.Action(R.drawable.exo_notification_play,
                                        "Play", PendingIntent.getBroadcast(context,
                                        NOTIFICATION_ID_BACKGROUND,
                                        new Intent(BackgroundPlayer.ACTION_PLAY_PAUSE),
                                        PendingIntent.FLAG_UPDATE_CURRENT)));
                    }
                }
                break;
            case "play_pause":
                if (basePlayerImpl.getCurrentState() == BasePlayer.STATE_PREFLIGHT
                        || basePlayerImpl.getCurrentState() == BasePlayer.STATE_BLOCKED
                        || basePlayerImpl.getCurrentState() == BasePlayer.STATE_BUFFERING) {
                    notificationBuilder.mActions.set(slotNumber,
                            new NotificationCompat.Action(R.drawable.exo_notification_pause,
                                    "Pause", PendingIntent.getBroadcast(context,
                                    NOTIFICATION_ID_BACKGROUND,
                                    new Intent(BackgroundPlayer.ACTION_PLAY_PAUSE),
                                    PendingIntent.FLAG_UPDATE_CURRENT)));
                } else if (basePlayerImpl.getCurrentState() == BasePlayer.STATE_COMPLETED) {
                    notificationBuilder.mActions.set(slotNumber,
                            new NotificationCompat.Action(R.drawable.ic_replay_white_24dp,
                                    "Completed", PendingIntent.getBroadcast(context,
                                    NOTIFICATION_ID_BACKGROUND,
                                    new Intent(BackgroundPlayer.ACTION_PLAY_PAUSE),
                                    PendingIntent.FLAG_UPDATE_CURRENT)));
                } else {
                    if (basePlayerImpl.isPlaying()) {
                        notificationBuilder.mActions.set(slotNumber,
                                new NotificationCompat.Action(R.drawable.exo_notification_pause,
                                        "Pause", PendingIntent.getBroadcast(context,
                                        NOTIFICATION_ID_BACKGROUND,
                                        new Intent(BackgroundPlayer.ACTION_PLAY_PAUSE),
                                        PendingIntent.FLAG_UPDATE_CURRENT)));
                    } else {
                        notificationBuilder.mActions.set(slotNumber,
                                new NotificationCompat.Action(R.drawable.exo_notification_play,
                                        "Play", PendingIntent.getBroadcast(context,
                                        NOTIFICATION_ID_BACKGROUND,
                                        new Intent(BackgroundPlayer.ACTION_PLAY_PAUSE),
                                        PendingIntent.FLAG_UPDATE_CURRENT)));
                    }
                }
                break;
            case "rewind":
                notificationBuilder.mActions.set(slotNumber,
                        new NotificationCompat.Action(R.drawable.exo_controls_rewind, "Rewind",
                                PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                                        new Intent(BackgroundPlayer.ACTION_FAST_REWIND),
                                        PendingIntent.FLAG_UPDATE_CURRENT)));
                break;
            case "smart_rewind_prev":
                if (basePlayerImpl.playQueue != null && basePlayerImpl.playQueue.size() > 1) {
                    notificationBuilder.mActions.set(slotNumber,
                            new NotificationCompat.Action(R.drawable.exo_notification_previous,
                                    "Prev", PendingIntent.getBroadcast(context,
                                    NOTIFICATION_ID_BACKGROUND,
                                    new Intent(BackgroundPlayer.ACTION_PLAY_PREVIOUS),
                                    PendingIntent.FLAG_UPDATE_CURRENT)));
                } else {
                    notificationBuilder.mActions.set(slotNumber,
                            new NotificationCompat.Action(R.drawable.exo_controls_rewind, "Rewind",
                                    PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                                            new Intent(BackgroundPlayer.ACTION_FAST_REWIND),
                                            PendingIntent.FLAG_UPDATE_CURRENT)));
                }
                break;
            case "forward":
                notificationBuilder.mActions.set(slotNumber,
                        new NotificationCompat.Action(R.drawable.exo_controls_fastforward,
                                "Forward", PendingIntent.getBroadcast(context,
                                NOTIFICATION_ID_BACKGROUND,
                                new Intent(BackgroundPlayer.ACTION_FAST_FORWARD),
                                PendingIntent.FLAG_UPDATE_CURRENT)));
                break;
            case "smart_forward_next":
                if (basePlayerImpl.playQueue != null && basePlayerImpl.playQueue.size() > 1) {
                    notificationBuilder.mActions.set(slotNumber,
                            new NotificationCompat.Action(R.drawable.exo_notification_next, "Next",
                                    PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                                            new Intent(BackgroundPlayer.ACTION_PLAY_NEXT),
                                            PendingIntent.FLAG_UPDATE_CURRENT)));
                } else {
                    notificationBuilder.mActions.set(slotNumber,
                            new NotificationCompat.Action(R.drawable.exo_controls_fastforward,
                                    "Forward", PendingIntent.getBroadcast(context,
                                    NOTIFICATION_ID_BACKGROUND,
                                    new Intent(BackgroundPlayer.ACTION_FAST_FORWARD),
                                    PendingIntent.FLAG_UPDATE_CURRENT)));
                }
                break;
            case "next":
                notificationBuilder.mActions.set(slotNumber,
                        new NotificationCompat.Action(R.drawable.exo_notification_next, "Next",
                                PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                                        new Intent(BackgroundPlayer.ACTION_PLAY_NEXT),
                                        PendingIntent.FLAG_UPDATE_CURRENT)));
                break;
            case "repeat":
                switch (basePlayerImpl.getRepeatMode()) {
                    case Player.REPEAT_MODE_ONE:
                        notificationBuilder.mActions.set(slotNumber,
                                new NotificationCompat.Action(R.drawable.exo_controls_repeat_one,
                                        "RepeatOne", PendingIntent.getBroadcast(context,
                                        NOTIFICATION_ID_BACKGROUND,
                                        new Intent(BackgroundPlayer.ACTION_REPEAT),
                                        PendingIntent.FLAG_UPDATE_CURRENT)));
                        break;
                    case Player.REPEAT_MODE_ALL:
                        notificationBuilder.mActions.set(slotNumber,
                                new NotificationCompat.Action(R.drawable.exo_controls_repeat_all,
                                        "RepeatAll", PendingIntent.getBroadcast(context,
                                        NOTIFICATION_ID_BACKGROUND,
                                        new Intent(BackgroundPlayer.ACTION_REPEAT),
                                        PendingIntent.FLAG_UPDATE_CURRENT)));
                        break;
                    case Player.REPEAT_MODE_OFF:
                    default:
                        notificationBuilder.mActions.set(slotNumber,
                                new NotificationCompat.Action(R.drawable.exo_controls_repeat_off,
                                        "RepeatOff", PendingIntent.getBroadcast(context,
                                        NOTIFICATION_ID_BACKGROUND,
                                        new Intent(BackgroundPlayer.ACTION_REPEAT),
                                        PendingIntent.FLAG_UPDATE_CURRENT)));
                        break;
                }
                break;
            case "shuffle":
                if (basePlayerImpl.playQueue.isShuffled()) {
                    notificationBuilder.mActions.set(slotNumber,
                            new NotificationCompat.Action(R.drawable.exo_controls_shuffle_on,
                                    "ShuffleOn", PendingIntent.getBroadcast(context,
                                    NOTIFICATION_ID_BACKGROUND,
                                    new Intent(BackgroundPlayer.ACTION_SHUFFLE),
                                    PendingIntent.FLAG_UPDATE_CURRENT)));
                } else {
                    notificationBuilder.mActions.set(slotNumber,
                            new NotificationCompat.Action(R.drawable.exo_controls_shuffle_off,
                                    "ShuffleOff", PendingIntent.getBroadcast(context,
                                    NOTIFICATION_ID_BACKGROUND,
                                    new Intent(BackgroundPlayer.ACTION_SHUFFLE),
                                    PendingIntent.FLAG_UPDATE_CURRENT)));
                }
                break;
            case "close":
                notificationBuilder.mActions.set(slotNumber,
                        new NotificationCompat.Action(R.drawable.ic_close_white_24dp, "Close",
                                PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                                        new Intent(BackgroundPlayer.ACTION_CLOSE),
                                        PendingIntent.FLAG_UPDATE_CURRENT)));
                break;
            case "n/a":
                // do nothing
                break;
            case "prev":
            default:
                notificationBuilder.mActions.set(slotNumber,
                        new NotificationCompat.Action(R.drawable.exo_notification_previous, "Prev",
                                PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                                        new Intent(BackgroundPlayer.ACTION_PLAY_PREVIOUS),
                                        PendingIntent.FLAG_UPDATE_CURRENT)));
                break;
        }
    }

    private NotificationCompat.Builder createPopupPlayerNotification(
            final Context context, final MediaSessionCompat.Token mediaSessionCompatToken,
            final PopupVideoPlayer.VideoPlayerImpl playerImpl,
            final SharedPreferences sharedPreferences) {
        notificationManager = ((NotificationManager) context
                .getSystemService(NOTIFICATION_SERVICE));
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                context.getString(R.string.notification_channel_id));

        String compactView = sharedPreferences.getString(context
                .getString(R.string.settings_notifications_compact_view_key), "0,1,2");
        int compactSlot0;
        int compactSlot1;
        int compactSlot2;
        try {
            String[] parts = compactView.split(",");
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
        } catch (Exception e) {
            e.printStackTrace();
            compactSlot0 = 0;
            compactSlot1 = 1;
            compactSlot2 = 2;
        }

        builder
                .setStyle(
                        new androidx.media.app.NotificationCompat.MediaStyle()
                                .setMediaSession(mediaSessionCompatToken)
                                .setShowCancelButton(false)
                                .setShowActionsInCompactView(compactSlot0, compactSlot1,
                                        compactSlot2))
                .setOngoing(false)
                .setContentIntent(PendingIntent.getActivity(context, NOTIFICATION_ID_POPUP,
                        new Intent(NavigationHelper.getPopupPlayerActivityIntent(context)),
                        PendingIntent.FLAG_UPDATE_CURRENT))
                .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentTitle(playerImpl.getVideoTitle())
                .setContentText(playerImpl.getUploaderName())
                .setDeleteIntent(PendingIntent.getBroadcast(context, NOTIFICATION_ID_POPUP,
                        new Intent(PopupVideoPlayer.ACTION_CLOSE),
                        PendingIntent.FLAG_UPDATE_CURRENT))
                .setColor(ContextCompat.getColor(context, R.color.gray));
        boolean scaleImageToSquareAspectRatio = sharedPreferences.getBoolean(context
                .getString(R.string.scale_to_square_image_in_notifications_key), false);
        if (scaleImageToSquareAspectRatio) {
            builder.setLargeIcon(getBitmapWithSquareAspectRatio(playerImpl.getThumbnail()));
        } else {
            builder.setLargeIcon(playerImpl.getThumbnail());
        }
        notificationSlot0 = sharedPreferences.getString(context
                .getString(R.string.notification_slot_0_key), notificationSlot0);
        notificationSlot1 = sharedPreferences.getString(context
                .getString(R.string.notification_slot_1_key), notificationSlot1);
        notificationSlot2 = sharedPreferences.getString(context.
                getString(R.string.notification_slot_2_key), notificationSlot2);
        notificationSlot3 = sharedPreferences.getString(context
                .getString(R.string.notification_slot_3_key), notificationSlot3);
        notificationSlot4 = sharedPreferences.getString(context
                .getString(R.string.notification_slot_4_key), notificationSlot4);

        addAction(context, builder, playerImpl, notificationSlot0);
        addAction(context, builder, playerImpl, notificationSlot1);
        addAction(context, builder, playerImpl, notificationSlot2);
        addAction(context, builder, playerImpl, notificationSlot3);
        addAction(context, builder, playerImpl, notificationSlot4);

        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        return builder;
    }

    private void addAction(final Context context, final NotificationCompat.Builder builder,
                           final PopupVideoPlayer.VideoPlayerImpl playerImpl, final String slot) {
        switch (slot) {
            case "play_pause_buffering":
                if (playerImpl.getCurrentState() == BasePlayer.STATE_PREFLIGHT
                        || playerImpl.getCurrentState() == BasePlayer.STATE_BLOCKED
                        || playerImpl.getCurrentState() == BasePlayer.STATE_BUFFERING) {
                    builder.addAction(R.drawable.ic_file_download_white_24dp, "Buffering",
                            PendingIntent.getBroadcast(context, NOTIFICATION_ID_POPUP,
                                    new Intent(PopupVideoPlayer.ACTION_BUFFERING),
                                    PendingIntent.FLAG_UPDATE_CURRENT));
                    builder.setSmallIcon(android.R.drawable.stat_sys_download);
                } else {
                    builder.setSmallIcon(R.drawable.ic_newpipe_triangle_white);
                    if (playerImpl.isPlaying()) {
                        builder.addAction(R.drawable.exo_notification_pause, "Pause",
                                PendingIntent.getBroadcast(context, NOTIFICATION_ID_POPUP,
                                        new Intent(PopupVideoPlayer.ACTION_PLAY_PAUSE),
                                        PendingIntent.FLAG_UPDATE_CURRENT));
                    } else {
                        builder.addAction(R.drawable.exo_notification_play, "Play",
                                PendingIntent.getBroadcast(context, NOTIFICATION_ID_POPUP,
                                        new Intent(PopupVideoPlayer.ACTION_PLAY_PAUSE),
                                        PendingIntent.FLAG_UPDATE_CURRENT));
                    }
                }
                break;
            case "play_pause":
                if (playerImpl.getCurrentState() == BasePlayer.STATE_PREFLIGHT
                        || playerImpl.getCurrentState() == BasePlayer.STATE_BLOCKED
                        || playerImpl.getCurrentState() == BasePlayer.STATE_BUFFERING) {
                    builder.addAction(R.drawable.exo_notification_pause, "Pause",
                            PendingIntent.getBroadcast(context, NOTIFICATION_ID_POPUP,
                                    new Intent(PopupVideoPlayer.ACTION_PLAY_PAUSE),
                                    PendingIntent.FLAG_UPDATE_CURRENT));
                } else if (playerImpl.isPlaying()) {
                    builder.addAction(R.drawable.exo_notification_pause, "Pause",
                            PendingIntent.getBroadcast(context, NOTIFICATION_ID_POPUP,
                                    new Intent(PopupVideoPlayer.ACTION_PLAY_PAUSE),
                                    PendingIntent.FLAG_UPDATE_CURRENT));
                } else {
                    builder.addAction(R.drawable.exo_notification_play, "Play",
                            PendingIntent.getBroadcast(context, NOTIFICATION_ID_POPUP,
                                    new Intent(PopupVideoPlayer.ACTION_PLAY_PAUSE),
                                    PendingIntent.FLAG_UPDATE_CURRENT));
                }
                break;
            case "rewind":
                builder.addAction(R.drawable.exo_controls_rewind, "Rewind",
                        PendingIntent.getBroadcast(context, NOTIFICATION_ID_POPUP,
                                new Intent(PopupVideoPlayer.ACTION_FAST_REWIND),
                                PendingIntent.FLAG_UPDATE_CURRENT));
                break;
            case "smart_rewind_prev":
                if (playerImpl.playQueue != null && playerImpl.playQueue.size() > 1) {
                    builder.addAction(R.drawable.exo_notification_previous, "Prev",
                            PendingIntent.getBroadcast(context, NOTIFICATION_ID_POPUP,
                                    new Intent(PopupVideoPlayer.ACTION_PLAY_PREVIOUS),
                                    PendingIntent.FLAG_UPDATE_CURRENT));
                } else {
                    builder.addAction(R.drawable.exo_controls_rewind, "Rewind",
                            PendingIntent.getBroadcast(context, NOTIFICATION_ID_POPUP,
                                    new Intent(PopupVideoPlayer.ACTION_FAST_REWIND),
                                    PendingIntent.FLAG_UPDATE_CURRENT));
                }
                break;
            case "forward":
                builder.addAction(R.drawable.exo_controls_fastforward, "Forward",
                        PendingIntent.getBroadcast(context, NOTIFICATION_ID_POPUP,
                                new Intent(PopupVideoPlayer.ACTION_FAST_FORWARD),
                                PendingIntent.FLAG_UPDATE_CURRENT));
                break;
            case "smart_forward_next":
                if (playerImpl.playQueue != null && playerImpl.playQueue.size() > 1) {
                    builder.addAction(R.drawable.exo_notification_next, "Next",
                            PendingIntent.getBroadcast(context, NOTIFICATION_ID_POPUP,
                                    new Intent(PopupVideoPlayer.ACTION_PLAY_NEXT),
                                    PendingIntent.FLAG_UPDATE_CURRENT));
                } else {
                    builder.addAction(R.drawable.exo_controls_fastforward, "Forward",
                            PendingIntent.getBroadcast(context, NOTIFICATION_ID_POPUP,
                                    new Intent(PopupVideoPlayer.ACTION_FAST_FORWARD),
                                    PendingIntent.FLAG_UPDATE_CURRENT));
                }
                break;
            case "next":
                builder.addAction(R.drawable.exo_notification_next, "Next",
                        PendingIntent.getBroadcast(context, NOTIFICATION_ID_POPUP,
                                new Intent(PopupVideoPlayer.ACTION_PLAY_NEXT),
                                PendingIntent.FLAG_UPDATE_CURRENT));
                break;
            case "repeat":
                switch (playerImpl.getRepeatMode()) {
                    case Player.REPEAT_MODE_ONE:
                        builder.addAction(R.drawable.exo_controls_repeat_one, "RepeatOne",
                                PendingIntent.getBroadcast(context, NOTIFICATION_ID_POPUP,
                                        new Intent(PopupVideoPlayer.ACTION_REPEAT),
                                        PendingIntent.FLAG_UPDATE_CURRENT));
                        break;
                    case Player.REPEAT_MODE_ALL:
                        builder.addAction(R.drawable.exo_controls_repeat_all, "RepeatAll",
                                PendingIntent.getBroadcast(context, NOTIFICATION_ID_POPUP,
                                        new Intent(PopupVideoPlayer.ACTION_REPEAT),
                                        PendingIntent.FLAG_UPDATE_CURRENT));
                        break;
                    case Player.REPEAT_MODE_OFF:
                    default:
                        builder.addAction(R.drawable.exo_controls_repeat_off, "RepeatOff",
                                PendingIntent.getBroadcast(context, NOTIFICATION_ID_POPUP,
                                        new Intent(PopupVideoPlayer.ACTION_REPEAT),
                                        PendingIntent.FLAG_UPDATE_CURRENT));
                        break;
                }
                break;
            case "shuffle":
                if (playerImpl.playQueue.isShuffled()) {
                    builder.addAction(R.drawable.exo_controls_shuffle_on, "ShuffleOn",
                            PendingIntent.getBroadcast(context, NOTIFICATION_ID_POPUP,
                                    new Intent(PopupVideoPlayer.ACTION_SHUFFLE),
                                    PendingIntent.FLAG_UPDATE_CURRENT));
                } else {
                    builder.addAction(R.drawable.exo_controls_shuffle_off, "ShuffleOff",
                            PendingIntent.getBroadcast(context, NOTIFICATION_ID_POPUP,
                                    new Intent(PopupVideoPlayer.ACTION_SHUFFLE),
                                    PendingIntent.FLAG_UPDATE_CURRENT));
                }
                break;
            case "close":
                builder.addAction(R.drawable.ic_close_white_24dp, "Close",
                        PendingIntent.getBroadcast(context, NOTIFICATION_ID_POPUP,
                                new Intent(PopupVideoPlayer.ACTION_CLOSE),
                                PendingIntent.FLAG_UPDATE_CURRENT));
                break;
            case "n/a":
                // do nothing
                break;
            case "prev":
            default:
                builder.addAction(R.drawable.exo_notification_previous, "Prev",
                        PendingIntent.getBroadcast(context, NOTIFICATION_ID_POPUP,
                                new Intent(PopupVideoPlayer.ACTION_PLAY_PREVIOUS),
                                PendingIntent.FLAG_UPDATE_CURRENT));
                break;
        }
    }

    /**
     * Updates the notification, and the button icons depending on the playback state.
     * On old notifications used for changes on the remoteView
     *
     * @param drawableId if != -1, sets the drawable with that id on the play/pause button
     * @param context
     * @param playerImpl
     * @param sharedPreferences
     */
    @SuppressLint("RestrictedApi")
    synchronized void updatePopupPlayerNotification(
            final int drawableId, final Context context,
            final PopupVideoPlayer.VideoPlayerImpl playerImpl,
            final SharedPreferences sharedPreferences) {
        if (DEBUG) {
            Log.d(TAG, "N_ updatePopupPlayerNotification()");
        }

        if (notificationBuilder == null) {
            return;
        }

        boolean areOldNotificationsEnabled = sharedPreferences.getBoolean(context
                .getString(R.string.enable_old_notifications_key), false);
        if (areOldNotificationsEnabled) {
            updateOldPopupPlayerNotification(drawableId);
        } else {
            notificationBuilder.setContentTitle(playerImpl.getVideoTitle());
            notificationBuilder.setContentText(playerImpl.getUploaderName());
            boolean scaleImageToSquareAspectRatio = sharedPreferences.getBoolean(context.
                    getString(R.string.scale_to_square_image_in_notifications_key), false);
            if (scaleImageToSquareAspectRatio) {
                notificationBuilder
                        .setLargeIcon(getBitmapWithSquareAspectRatio(playerImpl.getThumbnail()));
            } else {
                notificationBuilder.setLargeIcon(playerImpl.getThumbnail());
            }

            setAction(context, playerImpl, notificationSlot0, 0);
            setAction(context, playerImpl, notificationSlot1, 1);
            setAction(context, playerImpl, notificationSlot2, 2);
            setAction(context, playerImpl, notificationSlot3, 3);
            setAction(context, playerImpl, notificationSlot4, 4);
        }

        notificationManager.notify(NOTIFICATION_ID_POPUP, notificationBuilder.build());

        if (areOldNotificationsEnabled) {
            timesNotificationUpdated++;
        }
    }

    @SuppressLint("RestrictedApi")
    private void setAction(final Context context, final PopupVideoPlayer.VideoPlayerImpl playerImpl,
                           final String slot, final int slotNumber) {
        switch (slot) {
            case "play_pause_buffering":
                if (playerImpl.getCurrentState() == BasePlayer.STATE_PREFLIGHT
                        || playerImpl.getCurrentState() == BasePlayer.STATE_BLOCKED
                        || playerImpl.getCurrentState() == BasePlayer.STATE_BUFFERING) {
                    notificationBuilder.mActions.set(slotNumber,
                            new NotificationCompat.Action(R.drawable.ic_file_download_white_24dp,
                                    "Buffering", PendingIntent.getBroadcast(context,
                                    NOTIFICATION_ID_POPUP,
                                    new Intent(PopupVideoPlayer.ACTION_BUFFERING),
                                    PendingIntent.FLAG_UPDATE_CURRENT)));
                    notificationBuilder.setSmallIcon(android.R.drawable.stat_sys_download);
                } else if (playerImpl.getCurrentState() == BasePlayer.STATE_COMPLETED) {
                    notificationBuilder.mActions.set(slotNumber,
                            new NotificationCompat.Action(R.drawable.ic_replay_white_24dp,
                                    "Completed", PendingIntent.getBroadcast(context,
                                    NOTIFICATION_ID_POPUP,
                                    new Intent(PopupVideoPlayer.ACTION_PLAY_PAUSE),
                                    PendingIntent.FLAG_UPDATE_CURRENT)));
                } else {
                    notificationBuilder.setSmallIcon(R.drawable.ic_newpipe_triangle_white);
                    if (playerImpl.isPlaying()) {
                        notificationBuilder.mActions.set(slotNumber,
                                new NotificationCompat.Action(R.drawable.exo_notification_pause,
                                        "Pause", PendingIntent.getBroadcast(context,
                                        NOTIFICATION_ID_POPUP,
                                        new Intent(PopupVideoPlayer.ACTION_PLAY_PAUSE),
                                        PendingIntent.FLAG_UPDATE_CURRENT)));
                    } else {
                        notificationBuilder.mActions.set(slotNumber,
                                new NotificationCompat.Action(R.drawable.exo_notification_play,
                                        "Play", PendingIntent.getBroadcast(context,
                                        NOTIFICATION_ID_POPUP,
                                        new Intent(PopupVideoPlayer.ACTION_PLAY_PAUSE),
                                        PendingIntent.FLAG_UPDATE_CURRENT)));
                    }
                }
                break;
            case "play_pause":
                if (playerImpl.getCurrentState() == BasePlayer.STATE_PREFLIGHT
                        || playerImpl.getCurrentState() == BasePlayer.STATE_BLOCKED
                        || playerImpl.getCurrentState() == BasePlayer.STATE_BUFFERING) {
                    notificationBuilder.mActions.set(slotNumber,
                            new NotificationCompat.Action(R.drawable.exo_notification_pause,
                                    "Pause", PendingIntent.getBroadcast(context,
                                    NOTIFICATION_ID_POPUP,
                                    new Intent(PopupVideoPlayer.ACTION_PLAY_PAUSE),
                                    PendingIntent.FLAG_UPDATE_CURRENT)));
                } else if (playerImpl.getCurrentState() == BasePlayer.STATE_COMPLETED) {
                    notificationBuilder.mActions.set(slotNumber,
                            new NotificationCompat.Action(R.drawable.ic_replay_white_24dp,
                                    "Completed", PendingIntent.getBroadcast(context,
                                    NOTIFICATION_ID_POPUP,
                                    new Intent(PopupVideoPlayer.ACTION_PLAY_PAUSE),
                                    PendingIntent.FLAG_UPDATE_CURRENT)));
                } else {
                    if (playerImpl.isPlaying()) {
                        notificationBuilder.mActions.set(slotNumber,
                                new NotificationCompat.Action(R.drawable.exo_notification_pause,
                                        "Pause", PendingIntent.getBroadcast(context,
                                        NOTIFICATION_ID_POPUP,
                                        new Intent(PopupVideoPlayer.ACTION_PLAY_PAUSE),
                                        PendingIntent.FLAG_UPDATE_CURRENT)));
                    } else {
                        notificationBuilder.mActions.set(slotNumber,
                                new NotificationCompat.Action(R.drawable.exo_notification_play,
                                        "Play", PendingIntent.getBroadcast(context,
                                        NOTIFICATION_ID_POPUP,
                                        new Intent(PopupVideoPlayer.ACTION_PLAY_PAUSE),
                                        PendingIntent.FLAG_UPDATE_CURRENT)));
                    }
                }
                break;
            case "rewind":
                notificationBuilder.mActions.set(slotNumber,
                        new NotificationCompat.Action(R.drawable.exo_controls_rewind,
                                "Rewind", PendingIntent.getBroadcast(context,
                                NOTIFICATION_ID_POPUP,
                                new Intent(PopupVideoPlayer.ACTION_FAST_REWIND),
                                PendingIntent.FLAG_UPDATE_CURRENT)));
                break;
            case "smart_rewind_prev":
                if (playerImpl.playQueue != null && playerImpl.playQueue.size() > 1) {
                    notificationBuilder.mActions.set(slotNumber,
                            new NotificationCompat.Action(R.drawable.exo_notification_previous,
                                    "Prev", PendingIntent.getBroadcast(context,
                                    NOTIFICATION_ID_POPUP,
                                    new Intent(PopupVideoPlayer.ACTION_PLAY_PREVIOUS),
                                    PendingIntent.FLAG_UPDATE_CURRENT)));
                } else {
                    notificationBuilder.mActions.set(slotNumber,
                            new NotificationCompat.Action(R.drawable.exo_controls_rewind,
                                    "Rewind", PendingIntent.getBroadcast(context,
                                    NOTIFICATION_ID_POPUP,
                                    new Intent(PopupVideoPlayer.ACTION_FAST_REWIND),
                                    PendingIntent.FLAG_UPDATE_CURRENT)));
                }
                break;
            case "forward":
                notificationBuilder.mActions.set(slotNumber,
                        new NotificationCompat.Action(R.drawable.exo_controls_fastforward,
                                "Forward", PendingIntent.getBroadcast(context,
                                NOTIFICATION_ID_POPUP,
                                new Intent(PopupVideoPlayer.ACTION_FAST_FORWARD),
                                PendingIntent.FLAG_UPDATE_CURRENT)));
                break;
            case "smart_forward_next":
                if (playerImpl.playQueue != null && playerImpl.playQueue.size() > 1) {
                    notificationBuilder.mActions.set(slotNumber,
                            new NotificationCompat.Action(R.drawable.exo_notification_next, "Next",
                                    PendingIntent.getBroadcast(context, NOTIFICATION_ID_POPUP,
                                            new Intent(PopupVideoPlayer.ACTION_PLAY_NEXT),
                                            PendingIntent.FLAG_UPDATE_CURRENT)));
                } else {
                    notificationBuilder.mActions.set(slotNumber,
                            new NotificationCompat.Action(R.drawable.exo_controls_fastforward,
                                    "Forward", PendingIntent.getBroadcast(context,
                                    NOTIFICATION_ID_POPUP,
                                    new Intent(PopupVideoPlayer.ACTION_FAST_FORWARD),
                                    PendingIntent.FLAG_UPDATE_CURRENT)));
                }
                break;
            case "next":
                notificationBuilder.mActions.set(slotNumber,
                        new NotificationCompat.Action(R.drawable.exo_notification_next, "Next",
                                PendingIntent.getBroadcast(context, NOTIFICATION_ID_POPUP,
                                        new Intent(PopupVideoPlayer.ACTION_PLAY_NEXT),
                                        PendingIntent.FLAG_UPDATE_CURRENT)));
                break;
            case "repeat":
                switch (playerImpl.getRepeatMode()) {
                    case Player.REPEAT_MODE_ONE:
                        notificationBuilder.mActions.set(slotNumber,
                                new NotificationCompat.Action(R.drawable.exo_controls_repeat_one,
                                        "RepeatOne", PendingIntent.getBroadcast(context,
                                        NOTIFICATION_ID_POPUP,
                                        new Intent(PopupVideoPlayer.ACTION_REPEAT),
                                        PendingIntent.FLAG_UPDATE_CURRENT)));
                        break;
                    case Player.REPEAT_MODE_ALL:
                        notificationBuilder.mActions.set(slotNumber,
                                new NotificationCompat.Action(R.drawable.exo_controls_repeat_all,
                                        "RepeatAll", PendingIntent.getBroadcast(context,
                                        NOTIFICATION_ID_POPUP,
                                        new Intent(PopupVideoPlayer.ACTION_REPEAT),
                                        PendingIntent.FLAG_UPDATE_CURRENT)));
                        break;
                    case Player.REPEAT_MODE_OFF:
                    default:
                        notificationBuilder.mActions.set(slotNumber,
                                new NotificationCompat.Action(R.drawable.exo_controls_repeat_off,
                                        "RepeatOff", PendingIntent.getBroadcast(context,
                                        NOTIFICATION_ID_POPUP,
                                        new Intent(PopupVideoPlayer.ACTION_REPEAT),
                                        PendingIntent.FLAG_UPDATE_CURRENT)));
                        break;
                }
                break;
            case "shuffle":
                if (playerImpl.playQueue.isShuffled()) {
                    notificationBuilder.mActions.set(slotNumber,
                            new NotificationCompat.Action(R.drawable.exo_controls_shuffle_on,
                                    "ShuffleOn", PendingIntent.getBroadcast(context,
                                    NOTIFICATION_ID_POPUP,
                                    new Intent(PopupVideoPlayer.ACTION_SHUFFLE),
                                    PendingIntent.FLAG_UPDATE_CURRENT)));
                } else {
                    notificationBuilder.mActions.set(slotNumber,
                            new NotificationCompat.Action(R.drawable.exo_controls_shuffle_off,
                                    "ShuffleOff", PendingIntent.getBroadcast(context,
                                    NOTIFICATION_ID_POPUP,
                                    new Intent(PopupVideoPlayer.ACTION_SHUFFLE),
                                    PendingIntent.FLAG_UPDATE_CURRENT)));
                }
                break;
            case "close":
                notificationBuilder.mActions.set(slotNumber,
                        new NotificationCompat.Action(R.drawable.ic_close_white_24dp, "Close",
                                PendingIntent.getBroadcast(context, NOTIFICATION_ID_POPUP,
                                        new Intent(PopupVideoPlayer.ACTION_CLOSE),
                                        PendingIntent.FLAG_UPDATE_CURRENT)));
                break;
            case "n/a": // do nothing
                /*try { //FIXME maybe do nothing here ?
                    notificationBuilder.mActions.remove(slotNumber);
                } catch (ArrayIndexOutOfBoundsException e) {
                    e.printStackTrace();
                }*/
                break;
            case "prev":
            default:
                notificationBuilder.mActions.set(slotNumber,
                        new NotificationCompat.Action(R.drawable.exo_notification_previous, "Prev",
                                PendingIntent.getBroadcast(context, NOTIFICATION_ID_POPUP,
                                        new Intent(PopupVideoPlayer.ACTION_PLAY_PREVIOUS),
                                        PendingIntent.FLAG_UPDATE_CURRENT)));
                break;
        }
    }

    private Bitmap getBitmapWithSquareAspectRatio(final Bitmap bitmap) {
        return getResizedBitmap(bitmap, bitmap.getWidth(), bitmap.getWidth());
    }

    private Bitmap getResizedBitmap(final Bitmap bitmap, final int newWidth, final int newHeight) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, false);
    }

    @Deprecated // only used for old notifications
    private void setupOldNotification(final RemoteViews remoteViews, final Context context,
                                      final BackgroundPlayer.BasePlayerImpl basePlayerImpl) {
        if (basePlayerImpl == null) {
            return;
        }

        remoteViews.setTextViewText(R.id.notificationSongName, basePlayerImpl.getVideoTitle());
        remoteViews.setTextViewText(R.id.notificationArtist, basePlayerImpl.getUploaderName());

        remoteViews.setOnClickPendingIntent(R.id.notificationPlayPause,
                PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                        new Intent(BackgroundPlayer.ACTION_PLAY_PAUSE),
                        PendingIntent.FLAG_UPDATE_CURRENT));
        remoteViews.setOnClickPendingIntent(R.id.notificationStop,
                PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                        new Intent(BackgroundPlayer.ACTION_CLOSE),
                        PendingIntent.FLAG_UPDATE_CURRENT));
        remoteViews.setOnClickPendingIntent(R.id.notificationRepeat,
                PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                        new Intent(BackgroundPlayer.ACTION_REPEAT),
                        PendingIntent.FLAG_UPDATE_CURRENT));

        // Starts background player activity -- attempts to unlock lockscreen
        final Intent intent = NavigationHelper.getBackgroundPlayerActivityIntent(context);
        remoteViews.setOnClickPendingIntent(R.id.notificationContent,
                PendingIntent.getActivity(context, NOTIFICATION_ID_BACKGROUND, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT));

        if (basePlayerImpl.playQueue != null && basePlayerImpl.playQueue.size() > 1) {
            remoteViews.setInt(R.id.notificationFRewind,
                    BackgroundPlayer.SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_previous);
            remoteViews.setInt(R.id.notificationFForward,
                    BackgroundPlayer.SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_next);
            remoteViews.setOnClickPendingIntent(R.id.notificationFRewind,
                    PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                            new Intent(BackgroundPlayer.ACTION_PLAY_PREVIOUS),
                            PendingIntent.FLAG_UPDATE_CURRENT));
            remoteViews.setOnClickPendingIntent(R.id.notificationFForward,
                    PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                            new Intent(BackgroundPlayer.ACTION_PLAY_NEXT),
                            PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            remoteViews.setInt(R.id.notificationFRewind,
                    BackgroundPlayer.SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_rewind);
            remoteViews.setInt(R.id.notificationFForward,
                    BackgroundPlayer.SET_IMAGE_RESOURCE_METHOD,
                    R.drawable.exo_controls_fastforward);
            remoteViews.setOnClickPendingIntent(R.id.notificationFRewind,
                    PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                            new Intent(BackgroundPlayer.ACTION_FAST_REWIND),
                            PendingIntent.FLAG_UPDATE_CURRENT));
            remoteViews.setOnClickPendingIntent(R.id.notificationFForward,
                    PendingIntent.getBroadcast(context, NOTIFICATION_ID_BACKGROUND,
                            new Intent(BackgroundPlayer.ACTION_FAST_FORWARD),
                            PendingIntent.FLAG_UPDATE_CURRENT));
        }

        setRepeatModeIcon(remoteViews, basePlayerImpl.getRepeatMode());
    }

    @Deprecated // only used for old notifications
    private void setRepeatModeIcon(final RemoteViews remoteViews, final int repeatMode) {
        switch (repeatMode) {
            case Player.REPEAT_MODE_OFF:
                remoteViews.setInt(R.id.notificationRepeat,
                        BackgroundPlayer.SET_IMAGE_RESOURCE_METHOD,
                        R.drawable.exo_controls_repeat_off);
                break;
            case Player.REPEAT_MODE_ONE:
                remoteViews.setInt(R.id.notificationRepeat,
                        BackgroundPlayer.SET_IMAGE_RESOURCE_METHOD,
                        R.drawable.exo_controls_repeat_one);
                break;
            case Player.REPEAT_MODE_ALL:
                remoteViews.setInt(R.id.notificationRepeat,
                        BackgroundPlayer.SET_IMAGE_RESOURCE_METHOD,
                        R.drawable.exo_controls_repeat_all);
                break;
        }
    }

    @Deprecated // only used for old notifications
    public void updateOldNotificationsThumbnail(
            final BackgroundPlayer.BasePlayerImpl basePlayerImpl) {
        if (basePlayerImpl == null) {
            return;
        }
        if (notificationRemoteView != null) {
            notificationRemoteView.setImageViewBitmap(R.id.notificationCover,
                    basePlayerImpl.getThumbnail());
        }
        if (bigNotificationRemoteView != null) {
            bigNotificationRemoteView.setImageViewBitmap(R.id.notificationCover,
                    basePlayerImpl.getThumbnail());
        }
    }

    @Deprecated // only used for old notifications
    public void setProgressbarOnOldNotifications(final int max, final int progress,
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
    public void unsetImageInOldNotifications() {
        if (notificationRemoteView != null) {
            notificationRemoteView.setImageViewBitmap(R.id.notificationCover, null);
        }
        if (bigNotificationRemoteView != null) {
            bigNotificationRemoteView.setImageViewBitmap(R.id.notificationCover, null);
        }
    }

    @Deprecated // only used for old notifications
    public void setCachedDuration(final int currentProgress, final int duration) {
        if (bigNotificationRemoteView != null) {
            if (cachedDuration != duration) {
                cachedDuration = duration;
                cachedDurationString = getTimeString(duration);
            }
            bigNotificationRemoteView.setTextViewText(R.id.notificationTime,
                    getTimeString(currentProgress) + " / " + cachedDurationString);
        }
    }

    @Deprecated
    private NotificationCompat.Builder createOldPopupPlayerNotification(
            final Context context, final PopupVideoPlayer.VideoPlayerImpl playerImpl) {
        notificationManager = ((NotificationManager) context
                .getSystemService(NOTIFICATION_SERVICE));
        notificationBuilder = new NotificationCompat.Builder(context,
                context.getString(R.string.notification_channel_id));

        notificationPopupRemoteView = new RemoteViews(BuildConfig.APPLICATION_ID,
                R.layout.player_popup_notification);

        notificationPopupRemoteView.setTextViewText(R.id.notificationSongName,
                playerImpl.getVideoTitle());
        notificationPopupRemoteView.setTextViewText(R.id.notificationArtist,
                playerImpl.getUploaderName());
        notificationPopupRemoteView.setImageViewBitmap(R.id.notificationCover,
                playerImpl.getThumbnail());

        notificationPopupRemoteView.setOnClickPendingIntent(R.id.notificationPlayPause,
                PendingIntent.getBroadcast(context, NOTIFICATION_ID_POPUP,
                        new Intent(PopupVideoPlayer.ACTION_PLAY_PAUSE),
                        PendingIntent.FLAG_UPDATE_CURRENT));
        notificationPopupRemoteView.setOnClickPendingIntent(R.id.notificationStop,
                PendingIntent.getBroadcast(context, NOTIFICATION_ID_POPUP,
                        new Intent(PopupVideoPlayer.ACTION_CLOSE),
                        PendingIntent.FLAG_UPDATE_CURRENT));
        notificationPopupRemoteView.setOnClickPendingIntent(R.id.notificationRepeat,
                PendingIntent.getBroadcast(context, NOTIFICATION_ID_POPUP,
                        new Intent(PopupVideoPlayer.ACTION_REPEAT),
                        PendingIntent.FLAG_UPDATE_CURRENT));

        // Starts popup player activity -- attempts to unlock lockscreen
        final Intent intent = NavigationHelper.getPopupPlayerActivityIntent(context);
        notificationPopupRemoteView.setOnClickPendingIntent(R.id.notificationContent,
                PendingIntent.getActivity(context, NOTIFICATION_ID_POPUP, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT));

        setRepeatPopupModeRemote(notificationPopupRemoteView, playerImpl.getRepeatMode());

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                context.getString(R.string.notification_channel_id))
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContent(notificationPopupRemoteView);

        builder.setPriority(NotificationCompat.PRIORITY_MAX);
        return builder;
    }

    /*
     * Updates the notification, and the play/pause button in it.
     * Used for changes on the remoteView
     *
     * @param drawableId if != -1, sets the drawable with that id on the play/pause button
     */
    @Deprecated
    private void updateOldPopupPlayerNotification(final int drawableId) {
        if (DEBUG) {
            Log.d(TAG, "updateNotification() called with: drawableId = [" + drawableId + "]");
        }
        if (notificationBuilder == null || notificationPopupRemoteView == null) {
            return;
        }
        if (drawableId != -1) {
            notificationPopupRemoteView.setImageViewResource(R.id.notificationPlayPause,
                    drawableId);
        }
        notificationManager.notify(NOTIFICATION_ID_POPUP, notificationBuilder.build());
    }

    @Deprecated // only used for old notifications
    protected void setRepeatPopupModeRemote(final RemoteViews remoteViews, final int repeatMode) {
        final String methodName = "setImageResource";

        if (remoteViews == null) {
            return;
        }

        switch (repeatMode) {
            case Player.REPEAT_MODE_OFF:
                remoteViews.setInt(R.id.notificationRepeat, methodName,
                        R.drawable.exo_controls_repeat_off);
                break;
            case Player.REPEAT_MODE_ONE:
                remoteViews.setInt(R.id.notificationRepeat, methodName,
                        R.drawable.exo_controls_repeat_one);
                break;
            case Player.REPEAT_MODE_ALL:
                remoteViews.setInt(R.id.notificationRepeat, methodName,
                        R.drawable.exo_controls_repeat_all);
                break;
        }
    }

    @Deprecated // only used for old notifications
    public void unsetImageInOldPopupNotifications() {
        if (notificationRemoteView != null) {
            notificationRemoteView.setImageViewBitmap(R.id.notificationCover, null);
        }
    }

    public void cancelNotification(final int id) {
        try {
            if (notificationManager != null) {
                notificationManager.cancel(id);
            }
        } catch (Exception e) {
            Log.e("NotificationUtil", "Exception", e);
        }
    }

    private static class LazyHolder {
        private static final NotificationUtil INSTANCE = new NotificationUtil();
    }

}
