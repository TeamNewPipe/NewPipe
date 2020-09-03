package org.schabi.newpipe.player;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Build;
import android.util.Log;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

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

/**
 * This is a utility class for player notifications.
 *
 * @author cool-student
 */
public final class NotificationUtil {
    private static final String TAG = "NotificationUtil";
    private static final boolean DEBUG = BasePlayer.DEBUG;
    private static final int NOTIFICATION_ID = 123789;

    @Nullable private static NotificationUtil instance = null;

    private String notificationSlot0 = "smart_rewind_prev";
    private String notificationSlot1 = "play_pause_buffering";
    private String notificationSlot2 = "smart_forward_next";
    private String notificationSlot3 = "repeat";
    private String notificationSlot4 = "close";

    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;

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

    /**
     * Creates the notification if it does not exist already or unless forceRecreate is true.
     * @param player the player currently open, to take data from
     * @param forceRecreate whether to force the recreation of the notification even if it already
     *                      exists
     */
    void createNotificationIfNeeded(final VideoPlayerImpl player, final boolean forceRecreate) {
        if (notificationBuilder == null || forceRecreate) {
            if (DEBUG) {
                Log.d(TAG, "N_ createNotificationIfNeeded(true)");
            }
            notificationBuilder = createNotification(player);
        }
    }

    private NotificationCompat.Builder createNotification(final VideoPlayerImpl player) {
        notificationManager =
                (NotificationManager) player.context.getSystemService(NOTIFICATION_SERVICE);
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(player.context,
                player.context.getString(R.string.notification_channel_id));

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
                    .setShowActionsInCompactView(compactSlot0, compactSlot1, compactSlot2))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentTitle(player.getVideoTitle())
                .setContentText(player.getUploaderName())
                .setColor(ContextCompat.getColor(player.context, R.color.gray))
                .setContentIntent(PendingIntent.getActivity(player.context, NOTIFICATION_ID,
                        getIntentForNotification(player), FLAG_UPDATE_CURRENT))
                .setDeleteIntent(PendingIntent.getBroadcast(player.context, NOTIFICATION_ID,
                        new Intent(ACTION_CLOSE), FLAG_UPDATE_CURRENT));

        initializeNotificationSlots(player);
        updateActions(builder, player);
        setLargeIcon(builder, player);

        return builder;
    }

    /**
     * Updates the notification and the button icons depending on the playback state.
     * @param player the player currently open, to take data from
     */
    synchronized void updateNotification(final VideoPlayerImpl player) {
        if (DEBUG) {
            Log.d(TAG, "N_ updateNotification()");
        }

        if (notificationBuilder == null) {
            return;
        }

        notificationBuilder.setContentTitle(player.getVideoTitle());
        notificationBuilder.setContentText(player.getUploaderName());
        updateActions(notificationBuilder, player);
        setLargeIcon(notificationBuilder, player);

        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }


    void startForegroundServiceWithNotification(final Service service) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            service.startForeground(NOTIFICATION_ID, notificationBuilder.build(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            service.startForeground(NOTIFICATION_ID, notificationBuilder.build());
        }
    }


    boolean hasSlotWithBuffering() {
        return notificationSlot0.equals("play_pause_buffering")
                || notificationSlot1.equals("play_pause_buffering")
                || notificationSlot2.equals("play_pause_buffering")
                || notificationSlot3.equals("play_pause_buffering")
                || notificationSlot4.equals("play_pause_buffering");
    }

    public void cancelNotification() {
        try {
            if (notificationManager != null) {
                notificationManager.cancel(NOTIFICATION_ID);
                notificationManager = null;
            }
        } catch (Exception e) {
            Log.e("NotificationUtil", "Exception", e);
        }
    }


    /////////////////////////////////////////////////////
    // ACTIONS
    /////////////////////////////////////////////////////

    private void initializeNotificationSlots(final VideoPlayerImpl player) {
        notificationSlot0 = player.sharedPreferences.getString(
                player.context.getString(R.string.notification_action_0_key), notificationSlot0);
        notificationSlot1 = player.sharedPreferences.getString(
                player.context.getString(R.string.notification_action_1_key), notificationSlot1);
        notificationSlot2 = player.sharedPreferences.getString(
                player.context.getString(R.string.notification_action_2_key), notificationSlot2);
        notificationSlot3 = player.sharedPreferences.getString(
                player.context.getString(R.string.notification_action_3_key), notificationSlot3);
        notificationSlot4 = player.sharedPreferences.getString(
                player.context.getString(R.string.notification_action_4_key), notificationSlot4);
    }

    @SuppressLint("RestrictedApi")
    private void updateActions(final NotificationCompat.Builder builder,
                               final VideoPlayerImpl player) {
        builder.mActions.clear();
        addAction(builder, player, notificationSlot0);
        addAction(builder, player, notificationSlot1);
        addAction(builder, player, notificationSlot2);
        addAction(builder, player, notificationSlot3);
        addAction(builder, player, notificationSlot4);
    }

    private void addAction(final NotificationCompat.Builder builder,
                           final VideoPlayerImpl player,
                           final String slot) {
        final NotificationCompat.Action action = getAction(player, slot);
        if (action != null) {
            builder.addAction(action);
        }
    }

    @Nullable
    private NotificationCompat.Action getAction(final VideoPlayerImpl player,
                                                final String slot) {
        switch (slot) {
            case "play_pause_buffering":
                if (player.getCurrentState() == BasePlayer.STATE_PREFLIGHT
                        || player.getCurrentState() == BasePlayer.STATE_BLOCKED
                        || player.getCurrentState() == BasePlayer.STATE_BUFFERING) {
                    return getAction(player, R.drawable.ic_hourglass_top_white_24dp,
                            "Buffering", ACTION_BUFFERING);
                } else {
                    return getAction(player,
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
                return getAction(player,
                        pauseOrPlay ? R.drawable.exo_notification_pause
                                : R.drawable.exo_notification_play,
                        pauseOrPlay ? "Pause" : "Play",
                        ACTION_PLAY_PAUSE);
            case "rewind":
                return getAction(player, R.drawable.exo_controls_rewind,
                        "Rewind", ACTION_FAST_REWIND);
            case "smart_rewind_prev":
                if (player.playQueue != null && player.playQueue.size() > 1) {
                    return getAction(player, R.drawable.exo_notification_previous,
                            "Prev", ACTION_PLAY_PREVIOUS);
                } else {
                    return getAction(player, R.drawable.exo_controls_rewind,
                            "Rewind", ACTION_FAST_REWIND);
                }
            case "forward":
                return getAction(player, R.drawable.exo_controls_fastforward,
                        "Forward", ACTION_FAST_FORWARD);
            case "smart_forward_next":
                if (player.playQueue != null && player.playQueue.size() > 1) {
                    return getAction(player, R.drawable.exo_notification_next,
                            "Next", ACTION_PLAY_NEXT);
                } else {
                    return getAction(player, R.drawable.exo_controls_fastforward,
                            "Forward", ACTION_FAST_FORWARD);
                }
            case "next":
                return getAction(player, R.drawable.exo_notification_next,
                        "Next", ACTION_PLAY_NEXT);
            case "prev":
                return getAction(player, R.drawable.exo_notification_previous,
                        "Prev", ACTION_PLAY_PREVIOUS);
            case "repeat":
                return getAction(player, getRepeatModeDrawable(player.getRepeatMode()),
                        getRepeatModeTitle(player.getRepeatMode()), ACTION_REPEAT);
            case "shuffle":
                final boolean shuffled = player.playQueue != null && player.playQueue.isShuffled();
                return getAction(player,
                        shuffled ? R.drawable.exo_controls_shuffle_on
                                : R.drawable.exo_controls_shuffle_off,
                        shuffled ? "ShuffleOn" : "ShuffleOff",
                        ACTION_SHUFFLE);
            case "close":
                return getAction(player, R.drawable.ic_close_white_24dp,
                        "Close", ACTION_CLOSE);
            case "n/a":
            default:
                // do nothing
                return null;
        }
    }

    private NotificationCompat.Action getAction(final VideoPlayerImpl player,
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

    private void setLargeIcon(final NotificationCompat.Builder builder,
                              final VideoPlayerImpl player) {
        final boolean scaleImageToSquareAspectRatio = player.sharedPreferences.getBoolean(
                player.context.getString(R.string.scale_to_square_image_in_notifications_key),
                false);
        if (scaleImageToSquareAspectRatio) {
            builder.setLargeIcon(getBitmapWithSquareAspectRatio(player.getThumbnail()));
        } else {
            builder.setLargeIcon(player.getThumbnail());
        }
    }

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
