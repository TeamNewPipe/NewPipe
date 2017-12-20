package org.schabi.newpipe.player.helper;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;

import org.schabi.newpipe.R;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Formatter;
import java.util.Locale;

import javax.annotation.Nonnull;

public class PlayerHelper {
    private PlayerHelper() {}

    private static final StringBuilder stringBuilder = new StringBuilder();
    private static final Formatter stringFormatter = new Formatter(stringBuilder, Locale.getDefault());
    private static final NumberFormat speedFormatter = new DecimalFormat("0.##x");
    private static final NumberFormat pitchFormatter = new DecimalFormat("##%");

    ////////////////////////////////////////////////////////////////////////////
    // Exposed helpers
    ////////////////////////////////////////////////////////////////////////////

    public static String getTimeString(int milliSeconds) {
        long seconds = (milliSeconds % 60000L) / 1000L;
        long minutes = (milliSeconds % 3600000L) / 60000L;
        long hours = (milliSeconds % 86400000L) / 3600000L;
        long days = (milliSeconds % (86400000L * 7L)) / 86400000L;

        stringBuilder.setLength(0);
        return days > 0 ? stringFormatter.format("%d:%02d:%02d:%02d", days, hours, minutes, seconds).toString()
                : hours > 0 ? stringFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString()
                : stringFormatter.format("%02d:%02d", minutes, seconds).toString();
    }

    public static String formatSpeed(float speed) {
        return speedFormatter.format(speed);
    }

    public static String formatPitch(float pitch) {
        return pitchFormatter.format(pitch);
    }

    public static boolean isResumeAfterAudioFocusGain(@NonNull final Context context) {
        return isResumeAfterAudioFocusGain(context, false);
    }

    public static boolean isPlayerGestureEnabled(@NonNull final Context context) {
        return isPlayerGestureEnabled(context, true);
    }

    public static boolean isUsingOldPlayer(@NonNull final Context context) {
        return isUsingOldPlayer(context, false);
    }

    public static boolean isRememberingPopupDimensions(@Nonnull final Context context) {
        return isRememberingPopupDimensions(context, true);
    }

    public static long getPreferredCacheSize(@NonNull final Context context) {
        return 64 * 1024 * 1024L;
    }

    public static long getPreferredFileSize(@NonNull final Context context) {
        return 512 * 1024L;
    }

    public static int getMinBufferMs(@NonNull final Context context) {
        return 15000;
    }

    public static int getMaxBufferMs(@NonNull final Context context) {
        return 30000;
    }

    public static long getBufferForPlaybackMs(@NonNull final Context context) {
        return 2500L;
    }

    public static long getBufferForPlaybackAfterRebufferMs(@NonNull final Context context) {
        return 5000L;
    }

    public static boolean isUsingDSP(@NonNull final Context context) {
        return true;
    }

    public static int getShutdownFlingVelocity(@Nonnull final Context context) {
        return 10000;
    }

    public static int getTossFlingVelocity(@Nonnull final Context context) {
        return 2500;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Private helpers
    ////////////////////////////////////////////////////////////////////////////

    @NonNull
    private static SharedPreferences getPreferences(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    private static boolean isResumeAfterAudioFocusGain(@NonNull final Context context, final boolean b) {
        return getPreferences(context).getBoolean(context.getString(R.string.resume_on_audio_focus_gain_key), b);
    }

    private static boolean isPlayerGestureEnabled(@NonNull final Context context, final boolean b) {
        return getPreferences(context).getBoolean(context.getString(R.string.player_gesture_controls_key), b);
    }

    private static boolean isUsingOldPlayer(@NonNull final Context context, final boolean b) {
        return getPreferences(context).getBoolean(context.getString(R.string.use_old_player_key), b);
    }

    private static boolean isRememberingPopupDimensions(@Nonnull final Context context, final boolean b) {
        return getPreferences(context).getBoolean(context.getString(R.string.popup_remember_size_pos_key), b);
    }
}
