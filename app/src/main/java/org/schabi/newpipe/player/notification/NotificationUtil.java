package org.schabi.newpipe.player.notification;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static androidx.media.app.NotificationCompat.MediaStyle;
import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_CLOSE;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.PendingIntentCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.mediasession.MediaSessionPlayerUi;
import org.schabi.newpipe.util.NavigationHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * This is a utility class for player notifications.
 */
public final class NotificationUtil {
    private static final String TAG = NotificationUtil.class.getSimpleName();
    private static final boolean DEBUG = Player.DEBUG;
    private static final int NOTIFICATION_ID = 123789;

    @NotificationConstants.Action
    private final int[] notificationSlots = NotificationConstants.SLOT_DEFAULTS.clone();

    private NotificationManagerCompat notificationManager;
    private NotificationCompat.Builder notificationBuilder;

    private final Player player;

    public NotificationUtil(final Player player) {
        this.player = player;
    }


    /////////////////////////////////////////////////////
    // NOTIFICATION
    /////////////////////////////////////////////////////

    /**
     * Creates the notification if it does not exist already and recreates it if forceRecreate is
     * true. Updates the notification with the data in the player.
     * @param forceRecreate whether to force the recreation of the notification even if it already
     *                      exists
     */
    public synchronized void createNotificationIfNeededAndUpdate(final boolean forceRecreate) {
        if (forceRecreate || notificationBuilder == null) {
            notificationBuilder = createNotification();
        }
        updateNotification();
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }

    public synchronized void updateThumbnail() {
        if (notificationBuilder != null) {
            if (DEBUG) {
                Log.d(TAG, "updateThumbnail() called with thumbnail = [" + Integer.toHexString(
                        Optional.ofNullable(player.getThumbnail()).map(Objects::hashCode).orElse(0))
                        + "], title = [" + player.getVideoTitle() + "]");
            }

            setLargeIcon(notificationBuilder);
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
        }
    }

    private synchronized NotificationCompat.Builder createNotification() {
        if (DEBUG) {
            Log.d(TAG, "createNotification()");
        }
        notificationManager = NotificationManagerCompat.from(player.getContext());
        final NotificationCompat.Builder builder =
                new NotificationCompat.Builder(player.getContext(),
                player.getContext().getString(R.string.notification_channel_id));
        final MediaStyle mediaStyle = new MediaStyle();

        // setup media style (compact notification slots and media session)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // notification actions are ignored on Android 13+, and are replaced by code in
            // MediaSessionPlayerUi
            final int[] compactSlots = initializeNotificationSlots();
            mediaStyle.setShowActionsInCompactView(compactSlots);
        }
        player.UIs()
                .get(MediaSessionPlayerUi.class)
                .flatMap(MediaSessionPlayerUi::getSessionToken)
                .ifPresent(mediaStyle::setMediaSession);

        // setup notification builder
        builder.setStyle(mediaStyle)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setShowWhen(false)
                .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
                .setColor(ContextCompat.getColor(player.getContext(),
                        R.color.dark_background_color))
                .setColorized(player.getPrefs().getBoolean(
                        player.getContext().getString(R.string.notification_colorize_key), true))
                .setDeleteIntent(PendingIntentCompat.getBroadcast(player.getContext(),
                        NOTIFICATION_ID, new Intent(ACTION_CLOSE), FLAG_UPDATE_CURRENT, false));

        // set the initial value for the video thumbnail, updatable with updateNotificationThumbnail
        setLargeIcon(builder);

        return builder;
    }

    /**
     * Updates the notification builder and the button icons depending on the playback state.
     */
    private synchronized void updateNotification() {
        if (DEBUG) {
            Log.d(TAG, "updateNotification()");
        }

        // also update content intent, in case the user switched players
        notificationBuilder.setContentIntent(PendingIntentCompat.getActivity(player.getContext(),
                NOTIFICATION_ID, getIntentForNotification(), FLAG_UPDATE_CURRENT, false));
        notificationBuilder.setContentTitle(player.getVideoTitle());
        notificationBuilder.setContentText(player.getUploaderName());
        notificationBuilder.setTicker(player.getVideoTitle());

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // notification actions are ignored on Android 13+, and are replaced by code in
            // MediaSessionPlayerUi
            updateActions(notificationBuilder);
        }
    }


    @SuppressLint("RestrictedApi")
    public boolean shouldUpdateBufferingSlot() {
        if (notificationBuilder == null) {
            // if there is no notification active, there is no point in updating it
            return false;
        } else if (notificationBuilder.mActions.size() < 3) {
            // this should never happen, but let's make sure notification actions are populated
            return true;
        }

        // only second and third slot could contain PLAY_PAUSE_BUFFERING, update them only if they
        // are not already in the buffering state (the only one with a null action intent)
        return (notificationSlots[1] == NotificationConstants.PLAY_PAUSE_BUFFERING
                && notificationBuilder.mActions.get(1).actionIntent != null)
                || (notificationSlots[2] == NotificationConstants.PLAY_PAUSE_BUFFERING
                && notificationBuilder.mActions.get(2).actionIntent != null);
    }


    public void createNotificationAndStartForeground() {
        if (notificationBuilder == null) {
            notificationBuilder = createNotification();
        }
        updateNotification();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            player.getService().startForeground(NOTIFICATION_ID, notificationBuilder.build(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            player.getService().startForeground(NOTIFICATION_ID, notificationBuilder.build());
        }
    }

    public void cancelNotificationAndStopForeground() {
        ServiceCompat.stopForeground(player.getService(), ServiceCompat.STOP_FOREGROUND_REMOVE);

        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
        notificationManager = null;
        notificationBuilder = null;
    }


    /////////////////////////////////////////////////////
    // ACTIONS
    /////////////////////////////////////////////////////

    /**
     * The compact slots array from settings contains indices from 0 to 4, each referring to one of
     * the five actions configurable by the user. However, if the user sets an action to "Nothing",
     * then all of the actions coming after will have a "settings index" different than the index
     * of the corresponding action when sent to the system.
     *
     * @return the indices of compact slots referred to the list of non-nothing actions that will be
     *         sent to the system
     */
    private int[] initializeNotificationSlots() {
        final Collection<Integer> settingsCompactSlots = NotificationConstants
                .getCompactSlotsFromPreferences(player.getContext(), player.getPrefs());
        final List<Integer> adjustedCompactSlots = new ArrayList<>();

        int nonNothingIndex = 0;
        for (int i = 0; i < 5; ++i) {
            notificationSlots[i] = player.getPrefs().getInt(
                    player.getContext().getString(NotificationConstants.SLOT_PREF_KEYS[i]),
                    NotificationConstants.SLOT_DEFAULTS[i]);

            if (notificationSlots[i] != NotificationConstants.NOTHING) {
                if (settingsCompactSlots.contains(i)) {
                    adjustedCompactSlots.add(nonNothingIndex);
                }
                nonNothingIndex += 1;
            }
        }

        return adjustedCompactSlots.stream().mapToInt(Integer::intValue).toArray();
    }

    @SuppressLint("RestrictedApi")
    private void updateActions(final NotificationCompat.Builder builder) {
        builder.mActions.clear();
        for (int i = 0; i < 5; ++i) {
            addAction(builder, notificationSlots[i]);
        }
    }

    private void addAction(final NotificationCompat.Builder builder,
                           @NotificationConstants.Action final int slot) {
        @Nullable final NotificationActionData data =
                NotificationActionData.fromNotificationActionEnum(player, slot);
        if (data == null) {
            return;
        }

        final PendingIntent intent = PendingIntentCompat.getBroadcast(player.getContext(),
                NOTIFICATION_ID, new Intent(data.action()), FLAG_UPDATE_CURRENT, false);
        builder.addAction(new NotificationCompat.Action(data.icon(), data.name(), intent));
    }

    private Intent getIntentForNotification() {
        if (player.audioPlayerSelected() || player.popupPlayerSelected()) {
            // Means we play in popup or audio only. Let's show the play queue
            return NavigationHelper.getPlayQueueActivityIntent(player.getContext());
        } else {
            // We are playing in fragment. Don't open another activity just show fragment. That's it
            final Intent intent = NavigationHelper.getPlayerIntent(
                    player.getContext(), MainActivity.class, null, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            return intent;
        }
    }


    /////////////////////////////////////////////////////
    // BITMAP
    /////////////////////////////////////////////////////

    private void setLargeIcon(final NotificationCompat.Builder builder) {
        final boolean showThumbnail = player.getPrefs().getBoolean(
                player.getContext().getString(R.string.show_thumbnail_key), true);
        final Bitmap thumbnail = player.getThumbnail();
        if (thumbnail == null || !showThumbnail) {
            // since the builder is reused, make sure the thumbnail is unset if there is not one
            builder.setLargeIcon((Bitmap) null);
            return;
        }

        final boolean scaleImageToSquareAspectRatio = player.getPrefs().getBoolean(
                player.getContext().getString(R.string.scale_to_square_image_in_notifications_key),
                false);
        if (scaleImageToSquareAspectRatio) {
            builder.setLargeIcon(getBitmapWithSquareAspectRatio(thumbnail));
        } else {
            builder.setLargeIcon(thumbnail);
        }
    }

    private Bitmap getBitmapWithSquareAspectRatio(@NonNull final Bitmap bitmap) {
        // Find the smaller dimension and then take a center portion of the image that
        // has that size.
        final int w = bitmap.getWidth();
        final int h = bitmap.getHeight();
        final int dstSize = Math.min(w, h);
        final int x = (w - dstSize) / 2;
        final int y = (h - dstSize) / 2;
        return Bitmap.createBitmap(bitmap, x, y, dstSize, dstSize);
    }
}
