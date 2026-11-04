/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;

/**
 * YewPlay: sleep timer for the "Listen" screen. It only asks the player to pause when the time
 * is up, so the playback engine itself is untouched.
 */
public final class YpSleepTimer {
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static final Runnable PAUSE = YpSleepTimer::onTimeUp;

    @Nullable
    private static WeakReference<Player> playerRef = null;
    private static long deadlineUptimeMillis = 0;

    private YpSleepTimer() {
    }

    /**
     * Starts (or restarts) the timer.
     *
     * @param player  the player to pause when the time is up
     * @param minutes minutes until the player is paused
     */
    public static void start(@NonNull final Player player, final int minutes) {
        cancel();
        playerRef = new WeakReference<>(player);
        final long delay = minutes * 60_000L;
        deadlineUptimeMillis = SystemClock.uptimeMillis() + delay;
        HANDLER.postDelayed(PAUSE, delay);
    }

    /** Cancels the timer, if any. */
    public static void cancel() {
        HANDLER.removeCallbacks(PAUSE);
        playerRef = null;
        deadlineUptimeMillis = 0;
    }

    /**
     * Whether the timer is running.
     *
     * @return true when a pause is scheduled
     */
    public static boolean isActive() {
        return deadlineUptimeMillis > SystemClock.uptimeMillis();
    }

    /**
     * Minutes left until the pause, rounded up.
     *
     * @return the remaining minutes, or 0 when the timer is not running
     */
    public static int remainingMinutes() {
        final long left = deadlineUptimeMillis - SystemClock.uptimeMillis();
        return left <= 0 ? 0 : (int) ((left + 59_999L) / 60_000L);
    }

    private static void onTimeUp() {
        final Player player = playerRef == null ? null : playerRef.get();
        cancel();
        if (player != null) {
            player.pause();
        }
    }
}
