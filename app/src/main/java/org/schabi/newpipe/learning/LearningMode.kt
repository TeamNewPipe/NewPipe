package org.schabi.newpipe.learning

import android.content.Context
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R

object LearningMode {
    @JvmStatic
    fun isEnabled(context: Context): Boolean = boolPref(context, R.string.learning_mode_key, false)

    @JvmStatic
    fun areNotesEnabled(context: Context): Boolean =
        isEnabled(context) && boolPref(context, R.string.learning_notes_key, true)

    @JvmStatic
    fun shouldCountBackgroundPlayback(context: Context): Boolean =
        isEnabled(context) && boolPref(context, R.string.learning_count_background_key, true)

    @JvmStatic
    fun isPlaylistProgressEnabled(context: Context): Boolean =
        isEnabled(context) && boolPref(context, R.string.learning_playlist_progress_key, true)

    private fun boolPref(context: Context, keyRes: Int, fallback: Boolean): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .getBoolean(context.getString(keyRes), fallback)
}
