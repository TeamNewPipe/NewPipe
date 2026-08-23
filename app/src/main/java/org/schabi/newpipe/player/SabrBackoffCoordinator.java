package org.schabi.newpipe.player;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/** Publishes the SABR server-wait state independently from the media notification. */
public final class SabrBackoffCoordinator {
    public static final long NO_DEADLINE = -1L;
    static final int NOTIFICATION_ID = 123790;
    private static final long UPDATE_INTERVAL_MS = 1_000L;
    private static final SabrBackoffCoordinator INSTANCE = new SabrBackoffCoordinator();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updateTask = this::updateNotification;
    private Context appContext;
    private final Map<Object, Long> deadlinesByOwner = new IdentityHashMap<>();
    private boolean playerBuffering;

    private SabrBackoffCoordinator() {
    }

    @NonNull
    public static SabrBackoffCoordinator getInstance() {
        return INSTANCE;
    }

    public synchronized void begin(@NonNull final Context context,
                                   @NonNull final Object sourceOwner,
                                   final long remainingMs) {
        if (remainingMs <= 0L) {
            clear(context, sourceOwner);
            return;
        }
        appContext = context.getApplicationContext();
        deadlinesByOwner.put(sourceOwner, SystemClock.elapsedRealtime() + remainingMs);
        handler.removeCallbacks(updateTask);
        updateNotification();
    }

    public synchronized void clear(@NonNull final Context context,
                                   @NonNull final Object sourceOwner) {
        deadlinesByOwner.remove(sourceOwner);
        handler.removeCallbacks(updateTask);
        if (getRemainingMs() > 0L && playerBuffering) {
            updateNotification();
        } else {
            NotificationManagerCompat.from(context.getApplicationContext())
                    .cancel(NOTIFICATION_ID);
        }
    }

    public synchronized void setPlayerBuffering(@NonNull final Context context,
                                                final boolean buffering) {
        appContext = context.getApplicationContext();
        playerBuffering = buffering;
        handler.removeCallbacks(updateTask);
        if (buffering) {
            updateNotification();
        } else {
            NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID);
        }
    }

    public synchronized long getRemainingMs() {
        final long now = SystemClock.elapsedRealtime();
        long latestDeadline = NO_DEADLINE;
        final Iterator<Map.Entry<Object, Long>> iterator = deadlinesByOwner.entrySet().iterator();
        while (iterator.hasNext()) {
            final long deadline = iterator.next().getValue();
            if (deadline <= now) {
                iterator.remove();
            } else {
                latestDeadline = Math.max(latestDeadline, deadline);
            }
        }
        return latestDeadline == NO_DEADLINE ? 0L : latestDeadline - now;
    }

    public synchronized boolean isWaiting() {
        return getRemainingMs() > 0L;
    }

    static int remainingSeconds(final long remainingMs) {
        return remainingMs <= 0L ? 0 : (int) ((remainingMs + 999L) / 1_000L);
    }

    @SuppressLint("MissingPermission")
    private synchronized void updateNotification() {
        if (appContext == null || !playerBuffering) {
            return;
        }
        final long remainingMs = getRemainingMs();
        if (remainingMs <= 0L) {
            final Context context = appContext;
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID);
            return;
        }
        final int seconds = remainingSeconds(remainingMs);
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext,
                appContext.getString(R.string.sabr_backoff_notification_channel_id))
                .setSmallIcon(R.drawable.ic_pipeplay)
                .setContentTitle(appContext.getString(R.string.sabr_backoff_notification_title))
                .setContentText(appContext.getString(
                        R.string.sabr_backoff_notification_content, seconds))
                .setContentIntent(PendingIntent.getActivity(appContext, NOTIFICATION_ID,
                        new Intent(appContext, MainActivity.class),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, builder.build());
        handler.postDelayed(updateTask, UPDATE_INTERVAL_MS);
    }
}
