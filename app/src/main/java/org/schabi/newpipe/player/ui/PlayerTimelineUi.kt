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
    fun setDuration(binding: PlayerBinding, player: Player, durationMs: Int) {
        binding.playbackEndTime.text = PlayerHelper.getTimeString(durationMs.toLong())
        binding.playbackSeekBar.max = durationMs
        // Set max first: Android otherwise replaces TV seeking with (max - min) / 20.
        binding.playbackSeekBar.keyProgressIncrement = PlayerHelper.retrieveSeekDurationFromPreferences(player)
    }
}
