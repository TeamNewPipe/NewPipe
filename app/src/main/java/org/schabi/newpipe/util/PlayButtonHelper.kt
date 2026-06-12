/*
 * SPDX-FileCopyrightText: 2023-2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util

import android.content.Context
import android.view.View
import android.view.View.OnLongClickListener
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.PlaylistControlBinding
import org.schabi.newpipe.fragments.list.playlist.PlaylistControlViewHolder
import org.schabi.newpipe.player.PlayerType

/**
 * Utility class for play buttons and their respective click listeners.
 */
object PlayButtonHelper {
    /**
     * Initialize [OnClickListener][View.OnClickListener]
     * and [OnLongClickListener][OnLongClickListener] for playlist control
     * buttons defined in [R.layout.playlist_control].
     *
     * @param activity The activity to use for the [Toast][Toast].
     * @param playlistControlBinding The binding of the
     * [playlist control layout][R.layout.playlist_control].
     * @param fragment The fragment to get the play queue from.
     */
    @JvmStatic
    fun initPlaylistControlClickListener(
        activity: AppCompatActivity,
        playlistControlBinding: PlaylistControlBinding,
        fragment: PlaylistControlViewHolder
    ) {
        // click listener
        playlistControlBinding.playlistCtrlPlayAllButton.setOnClickListener {
            NavigationHelper.playOnMainPlayer(activity, fragment.getPlayQueue())
            showHoldToAppendToastIfNeeded(activity)
        }
        playlistControlBinding.playlistCtrlPlayPopupButton.setOnClickListener {
            NavigationHelper.playOnPopupPlayer(activity, fragment.getPlayQueue(), false)
            showHoldToAppendToastIfNeeded(activity)
        }
        playlistControlBinding.playlistCtrlPlayBgButton.setOnClickListener {
            NavigationHelper.playOnBackgroundPlayer(activity, fragment.getPlayQueue(), false)
            showHoldToAppendToastIfNeeded(activity)
        }

        // long click listener
        playlistControlBinding.playlistCtrlPlayAllButton.setOnLongClickListener {
            NavigationHelper.enqueueOnPlayer(activity, fragment.getPlayQueue(), PlayerType.MAIN)
            true
        }
        playlistControlBinding.playlistCtrlPlayPopupButton.setOnLongClickListener {
            NavigationHelper.enqueueOnPlayer(activity, fragment.getPlayQueue(), PlayerType.POPUP)
            true
        }
        playlistControlBinding.playlistCtrlPlayBgButton.setOnLongClickListener {
            NavigationHelper.enqueueOnPlayer(activity, fragment.getPlayQueue(), PlayerType.AUDIO)
            true
        }
    }

    /**
     * Show the "hold to append" toast if the corresponding preference is enabled.
     *
     * @param context The context to show the toast.
     */
    private fun showHoldToAppendToastIfNeeded(context: Context) {
        if (shouldShowHoldToAppendTip(context)) {
            Toast.makeText(context, R.string.hold_to_append, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Check if the "hold to append" toast should be shown.
     *
     *
     *
     * The tip is shown if the corresponding preference is enabled.
     * This is the default behaviour.
     *
     *
     * @param context The context to get the preference.
     * @return `true` if the tip should be shown, `false` otherwise.
     */
    @JvmStatic
    fun shouldShowHoldToAppendTip(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context.getString(R.string.show_hold_to_append_key), true)
    }
}
