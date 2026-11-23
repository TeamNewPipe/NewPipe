/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.ui

import android.view.View
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding
import org.schabi.newpipe.databinding.PlayerBinding
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.event.PlayerServiceEventListener

/** The same explicit action is reachable from fullscreen and from video details. */
object PreviousPlaybackUi {
    @JvmStatic
    fun bindDetails(binding: FragmentVideoDetailBinding, listener: PlayerServiceEventListener) {
        binding.detailPreviousPlayback.setOnClickListener { listener.onReturnToPreviousPlayback() }
        updateDetails(binding, listener)
    }

    @JvmStatic
    fun updateDetails(binding: FragmentVideoDetailBinding, listener: PlayerServiceEventListener) {
        binding.detailPreviousPlayback.visibility = if (listener.canReturnToPreviousPlayback()) View.VISIBLE else View.GONE
    }

    @JvmStatic
    fun bind(binding: PlayerBinding, player: Player) {
        binding.previousPlayback.setOnClickListener {
            player.fragmentListener.ifPresent { it.onReturnToPreviousPlayback() }
            update(binding, player)
        }
        update(binding, player)
    }

    @JvmStatic
    fun clear(binding: PlayerBinding) {
        binding.previousPlayback.setOnClickListener(null)
    }

    @JvmStatic
    fun update(binding: PlayerBinding, player: Player) {
        val available = player.fragmentListener.map { it.canReturnToPreviousPlayback() }.orElse(false)
        binding.previousPlayback.visibility = if (available) View.VISIBLE else View.GONE
    }
}
