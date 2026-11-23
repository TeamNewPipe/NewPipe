/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player.ui

import org.schabi.newpipe.databinding.PlayerBinding
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.helper.PlayerHelper

/** Shared duration display and native seekbar configuration, in milliseconds. */
internal object PlayerTimelineUi {
    @JvmStatic
    fun updateProgress(binding: PlayerBinding, player: Player, positionMs: Int, durationMs: Int, bufferPercent: Int) {
        if (durationMs != binding.playbackSeekBar.max) setDuration(binding, player, durationMs)
        if (player.currentState != Player.STATE_PAUSED) updatePosition(binding, player, positionMs)
        if (player.isLoading || bufferPercent > 90) {
            binding.playbackSeekBar.secondaryProgress = (binding.playbackSeekBar.max * (bufferPercent.toFloat() / 100)).toInt()
        }
    }

    @JvmStatic
    fun updatePosition(binding: PlayerBinding, player: Player, positionMs: Int) {
        // Leave the user's seek position alone until the current gesture finishes.
        if (player.currentState != Player.STATE_PAUSED_SEEK) binding.playbackSeekBar.progress = positionMs
        binding.playbackCurrentTime.text = PlayerHelper.getTimeString(positionMs.toLong())
    }

    @JvmStatic
    fun setDuration(binding: PlayerBinding, player: Player, durationMs: Int) {
        binding.playbackEndTime.text = PlayerHelper.getTimeString(durationMs.toLong())
        binding.playbackSeekBar.max = durationMs
        // Set max first: Android otherwise replaces TV seeking with (max - min) / 20.
        binding.playbackSeekBar.keyProgressIncrement = PlayerHelper.retrieveSeekDurationFromPreferences(player)
    }
}
