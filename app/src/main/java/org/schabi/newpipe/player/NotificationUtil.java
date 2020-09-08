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

import java.util.List;

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
    private static final String TAG = NotificationUtil.class.getSimpleName();
    private static final boolean DEBUG = BasePlayer.DEBUG;
    private static final int NOTIFICATION_ID = 123789;

    @Nullable private static NotificationUtil instance = null;

    @NotificationConstants.Action
    private int[] notificationSlots = NotificationConstants.SLOT_DEFAULTS.clone();

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
     * Creates the notification if it does not exist already and recreates it if forceRecreate is
     * true. Updates the notification with the data in the player.
     * @param player the player currently open, to take data from
     * @param forceRecreate whether to force the recreation of the notification even if it already
     *                      exists
     */
    synchronized void createNotificationIfNeededAndUpdate(final VideoPlayerImpl player,
                                                          final boolean forceRecreate) {
        if (notificationBuilder == null || forceRecreate) {
            if (DEBUG) {
                Log.d(TAG, "N_ createNotificationIfNeededAndUpdate(true)");
            }
            notificationBuilder = createNotification(player);
        }
        updateNotification(player);
    }

    private synchronized NotificationCompat.Builder createNotification(
            final VideoPlayerImpl player) {
        notificationManager =
                (NotificationManager) player.context.getSystemService(NOTIFICATION_SERVICE);
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(player.context,
                player.context.getString(R.string.notification_channel_id));

        initializeNotificationSlots(player);

        // count the number of real slots, to make sure compact slots indices are not out of bound
        int nonNothingSlotCount = 5;
        if (notificationSlots[3] == NotificationConstants.NOTHING) {
            --nonNothingSlotCount;
        }
        if (notificationSlots[4] == NotificationConstants.NOTHING) {
            --nonNothingSlotCount;
        }

        // build the compact slot indices array (need code to convert from Integer... because Java)
        final List<Integer> compactSlotList = NotificationConstants.getCompactSlotsFromPreferences(
                player.context, player.sharedPreferences, nonNothingSlotCount);
        final int[] compactSlots = new int[compactSlotList.size()];
        for (int i = 0; i < compactSlotList.size(); i++) {
            compactSlots[i] = compactSlotList.get(i);
        }

        builder.setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(player.mediaSessionManager.getSessionToken())
                    .setShowActionsInCompactView(compactSlots))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setColor(ContextCompat.getColor(player.context, R.color.gray))
                .setDeleteIntent(PendingIntent.getBroadcast(player.context, NOTIFICATION_ID,
                        new Intent(ACTION_CLOSE), FLAG_UPDATE_CURRENT));

        return builder;
    }

    /**
     * Updates the notification and the button icons depending on the playback state.
     * @param player the player currently open, to take data from
     */
    private synchronized void updateNotification(final VideoPlayerImpl player) {
        if (DEBUG) {
            Log.d(TAG, "N_ updateNotification()");
        }

        if (notificationBuilder == null) {
            return;
        }

        // also update content intent, in case the user switched players
        notificationBuilder.setContentIntent(PendingIntent.getActivity(player.context,
                NOTIFICATION_ID, getIntentForNotification(player), FLAG_UPDATE_CURRENT));
        notificationBuilder.setContentTitle(player.getVideoTitle());
        notificationBuilder.setContentText(player.getUploaderName());
        updateActions(notificationBuilder, player);
        setLargeIcon(notificationBuilder, player);

        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }


    boolean hasSlotWithBuffering() {
        return notificationSlots[1] == NotificationConstants.PLAY_PAUSE_BUFFERING
                || notificationSlots[2] == NotificationConstants.PLAY_PAUSE_BUFFERING;
    }


    void createNotificationAndStartForeground(final VideoPlayerImpl player, final Service service) {
        createNotificationIfNeededAndUpdate(player, true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            service.startForeground(NOTIFICATION_ID, notificationBuilder.build(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            service.startForeground(NOTIFICATION_ID, notificationBuilder.build());
        }
    }

    void cancelNotificationAndStopForeground(final Service service) {
        try {
            if (notificationManager != null) {
                notificationManager.cancel(NOTIFICATION_ID);
            }
        } catch (final Exception e) {
            Log.e(TAG, "Could not cancel notification", e);
        }
        notificationManager = null;
        notificationBuilder = null;

        service.stopForeground(true);
    }


    /////////////////////////////////////////////////////
    // ACTIONS
    /////////////////////////////////////////////////////

    private void initializeNotificationSlots(final VideoPlayerImpl player) {
        for (int i = 0; i < 5; ++i) {
            notificationSlots[i] = player.sharedPreferences.getInt(
                    player.context.getString(NotificationConstants.SLOT_PREF_KEYS[i]),
                    NotificationConstants.SLOT_DEFAULTS[i]);
        }
    }

    @SuppressLint("RestrictedApi")
    private void updateActions(final NotificationCompat.Builder builder,
                               final VideoPlayerImpl player) {
        builder.mActions.clear();
        for (int i = 0; i < 5; ++i) {
            addAction(builder, player, notificationSlots[i]);
        }
    }

    private void addAction(final NotificationCompat.Builder builder,
                           final VideoPlayerImpl player,
                           @NotificationConstants.Action final int slot) {
        final NotificationCompat.Action action = getAction(player, slot);
        if (action != null) {
            builder.addAction(action);
        }
    }

    @Nullable
    private NotificationCompat.Action getAction(
            final VideoPlayerImpl player,
            @NotificationConstants.Action final int selectedAction) {
        final int baseActionIcon = NotificationConstants.ACTION_ICONS[selectedAction];
        switch (selectedAction) {
            case NotificationConstants.PREVIOUS:
                return getAction(player, baseActionIcon, "Previous", ACTION_PLAY_PREVIOUS);

            case NotificationConstants.NEXT:
                return getAction(player, baseActionIcon, "Next", ACTION_PLAY_NEXT);

            case NotificationConstants.REWIND:
                return getAction(player, baseActionIcon, "Rewind", ACTION_FAST_REWIND);

            case NotificationConstants.FORWARD:
                return getAction(player, baseActionIcon, "Forward", ACTION_FAST_FORWARD);

            case NotificationConstants.SMART_REWIND_PREVIOUS:
                if (player.playQueue != null && player.playQueue.size() > 1) {
                    return getAction(player, R.drawable.exo_notification_previous,
                            "Previous", ACTION_PLAY_PREVIOUS);
                } else {
                    return getAction(player, R.drawable.exo_controls_rewind,
                            "Rewind", ACTION_FAST_REWIND);
                }

            case NotificationConstants.SMART_FORWARD_NEXT:
                if (player.playQueue != null && player.playQueue.size() > 1) {
                    return getAction(player, R.drawable.exo_notification_next,
                            "Next", ACTION_PLAY_NEXT);
                } else {
                    return getAction(player, R.drawable.exo_controls_fastforward,
                            "Forward", ACTION_FAST_FORWARD);
                }

            case NotificationConstants.PLAY_PAUSE:
                final boolean pauseOrPlay = player.isPlaying()
                        || player.getCurrentState() == BasePlayer.STATE_PREFLIGHT
                        || player.getCurrentState() == BasePlayer.STATE_BLOCKED
                        || player.getCurrentState() == BasePlayer.STATE_BUFFERING;
                return getAction(player,
                        pauseOrPlay ? R.drawable.exo_notification_pause
                                : R.drawable.exo_notification_play,
                        pauseOrPlay ? "Pause" : "Play",
                        ACTION_PLAY_PAUSE);

            case NotificationConstants.PLAY_PAUSE_BUFFERING:
                if (player.getCurrentState() == BasePlayer.STATE_PREFLIGHT
                        || player.getCurrentState() == BasePlayer.STATE_BLOCKED
                        || player.getCurrentState() == BasePlayer.STATE_BUFFERING) {
                    return getAction(player, R.drawable.ic_hourglass_top_white_24dp_png,
                            "Buffering", ACTION_BUFFERING);
                } else {
                    return getAction(player,
                            player.isPlaying() ? R.drawable.exo_notification_pause
                                    : R.drawable.exo_notification_play,
                            player.isPlaying() ? "Pause" : "Play",
                            ACTION_PLAY_PAUSE);
                }

            case NotificationConstants.REPEAT:
                return getAction(player, getRepeatModeDrawable(player.getRepeatMode()),
                        getRepeatModeTitle(player.getRepeatMode()), ACTION_REPEAT);

            case NotificationConstants.SHUFFLE:
                final boolean shuffled = player.playQueue != null && player.playQueue.isShuffled();
                return getAction(player,
                        shuffled ? R.drawable.exo_controls_shuffle_on
                                : R.drawable.exo_controls_shuffle_off,
                        shuffled ? "ShuffleOn" : "ShuffleOff",
                        ACTION_SHUFFLE);

            case NotificationConstants.CLOSE:
                return getAction(player, R.drawable.ic_close_white_24dp_png,
                        "Close", ACTION_CLOSE);

            case NotificationConstants.NOTHING:
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
        if (player.audioPlayerSelected() || player.popupPlayerSelected()) {
            // Means we play in popup or audio only. Let's show the play queue
            return NavigationHelper.getPlayQueueActivityIntent(player.context);
        } else {
            // We are playing in fragment. Don't open another activity just show fragment. That's it
            final Intent intent = NavigationHelper.getPlayerIntent(
                    player.context, MainActivity.class, null, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            return intent;
        }
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
