package org.schabi.newpipe.player.ui

import android.content.Context
import android.content.SharedPreferences
import org.schabi.newpipe.R

internal object PlayerControlsTimeout {
    @JvmStatic
    fun delayMillis(context: Context, preferences: SharedPreferences, touch: Boolean): Long {
        return when (preferences.getString(context.getString(R.string.player_controls_timeout_key), null)) {
            "never" -> -1
            "5" -> 5_000
            "10" -> 10_000
            "15" -> 15_000
            else -> if (touch) VideoPlayerUi.DEFAULT_CONTROLS_HIDE_TIME else VideoPlayerUi.DPAD_CONTROLS_HIDE_TIME
        }
    }
}
