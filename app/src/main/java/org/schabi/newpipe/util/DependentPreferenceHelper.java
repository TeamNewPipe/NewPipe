package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;

/**
 * For preferences with dependencies and multiple use case,
 * this class can be used to reduce the lines of code.
 */
public final class DependentPreferenceHelper {

    private DependentPreferenceHelper() {
        // no instance
    }

    /**
     * Option `Resume playback` depends on `Watch history`, this method can be used to retrieve if
     * `Resume playback` and its dependencies are all enabled.
     *
     * @param context the Android context
     * @return returns true if `Resume playback` and `Watch history` are both enabled
     */
    public static boolean getResumePlaybackEnabled(final Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        return prefs.getBoolean(context.getString(
                        R.string.enable_watch_history_key), true)
                && prefs.getBoolean(context.getString(
                        R.string.enable_playback_resume_key), true);
    }

    /**
     * Option `Position in lists` depends on `Watch history`, this method can be used to retrieve if
     * `Position in lists` and its dependencies are all enabled.
     *
     * @param context the Android context
     * @return returns true if `Positions in lists` and `Watch history` are both enabled
     */
    public static boolean getPositionsInListsEnabled(final Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        return prefs.getBoolean(context.getString(
                        R.string.enable_watch_history_key), true)
                && prefs.getBoolean(context.getString(
                        R.string.enable_playback_state_lists_key), true);
    }
}
