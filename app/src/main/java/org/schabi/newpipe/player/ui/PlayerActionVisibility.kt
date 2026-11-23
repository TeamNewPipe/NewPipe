/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player.ui

import android.content.Context
import android.view.View
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R

/** Optional tools share a visibility preference without changing their feature availability. */
object PlayerActionVisibility {
    internal class Action(val key: String, @StringRes val title: Int, @IdRes vararg val views: Int)

    internal val actions = listOf(
        Action("resize", R.string.player_resize_help, R.id.resizeTextView),
        Action("projection", R.string.player_projection, R.id.projectionButton),
        Action("danmaku", R.string.danmaku_enable, R.id.danmakuToggle),
        Action("captions", R.string.caption_setting_title, R.id.captionTextView),
        Action("ab_repeat", R.string.ab_repeat, R.id.abRepeatButton),
        Action("kodi", R.string.play_with_kodi_title, R.id.playWithKodi, R.id.detail_controls_play_with_kodi),
        Action("cast", R.string.device_cast_title, R.id.castToPairedDevice),
        Action("cast_tv", R.string.cast_to_tv, R.id.detail_controls_cast),
        Action("browser", R.string.open_in_browser, R.id.openInBrowser, R.id.detail_controls_open_in_browser),
        Action("share", R.string.share, R.id.share, R.id.detail_controls_share),
        Action("info", R.string.playback_information, R.id.playbackInformationButton),
        Action("seek_back", R.string.seek_history_back, R.id.seekHistoryBack),
        Action("seek_forward", R.string.seek_history_forward, R.id.seekHistoryForward),
        Action("previous_playback", R.string.return_to_previous_playback, R.id.previousPlayback, R.id.detail_previous_playback),
        Action("replay_peak", R.string.next_replay_peak, R.id.nextReplayPeak),
        Action("sleep", R.string.sleep_timer, R.id.sleepTimerButton),
        Action("sponsor", R.string.sb_contribute_title, R.id.sponsorBlockContributeButton),
        Action("equalizer", R.string.equalizer, R.id.equalizerButton),
        Action("boost", R.string.volume_boost, R.id.volumeBoostButton),
        Action("adjust", R.string.video_adjustments, R.id.videoAdjustmentsButton),
        Action("lock", R.string.player_touch_lock, R.id.touchLockButton),
        Action("mute", R.string.mute, R.id.switchMute)
    )
    private val keys = actions.mapTo(mutableSetOf(), Action::key)

    internal fun normalize(hidden: Set<String>?): Set<String> = hidden.orEmpty().intersect(keys)

    internal fun actionForView(@IdRes id: Int): Action? = actions.firstOrNull { id in it.views }

    internal fun visibility(requested: Int, key: String?, hidden: Set<String>): Int = if (key != null && key in keys && key in hidden) View.GONE else requested

    internal fun preferenceKey(context: Context): String = context.getString(R.string.visible_player_actions_key)

    fun hidden(context: Context): Set<String> = normalize(
        PreferenceManager.getDefaultSharedPreferences(context).getStringSet(preferenceKey(context), emptySet())
    )

    fun save(context: Context, hidden: Set<String>) {
        val selected = normalize(hidden)
        PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
            if (selected.isEmpty()) remove(preferenceKey(context)) else putStringSet(preferenceKey(context), selected)
        }.apply()
    }
}
