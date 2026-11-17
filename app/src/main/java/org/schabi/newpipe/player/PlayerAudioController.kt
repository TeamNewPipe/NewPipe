/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import org.schabi.newpipe.player.boost.VolumeBoostController
import org.schabi.newpipe.player.equalizer.EqualizerController
import org.schabi.newpipe.player.equalizer.EqualizerState

/** Owns mute, real-time audio effects, audio focus, and effective-volume coordination. */
internal class PlayerAudioController(private val player: Player) {
    private val equalizer = EqualizerController(player.context)
    private val volumeBoost = VolumeBoostController(player.context)
    val volume = PlaybackVolumeController(
        player.context,
        player::applyPlayerVolume,
        player::updateAudioTunneling
    )

    var isMuted = false
        private set

    val equalizerState: EqualizerState
        get() = equalizer.state

    val isEqualizerAvailable: Boolean
        get() = equalizer.isAvailable

    val isEqualizerOperational: Boolean
        get() = equalizer.isOperational

    val equalizerHeadroomMultiplier: Float
        get() = equalizer.headroomMultiplier

    val volumeBoostGainMb: Int
        get() = volumeBoost.gainMb

    val isVolumeBoostEnabled: Boolean
        get() = volumeBoost.isEnabled

    val isVolumeBoostAvailable: Boolean
        get() = volumeBoost.isAvailable

    val isVolumeBoostOperational: Boolean
        get() = volumeBoost.isOperational

    fun attachAudioSession(audioSessionId: Int) {
        volume.attach()
        equalizer.attachAudioSession(audioSessionId)
        volumeBoost.attachAudioSession(audioSessionId)
    }

    fun releaseAudioSession() {
        volume.detach()
        equalizer.releaseAudioSession()
        volumeBoost.releaseAudioSession()
    }

    fun onAudioSessionChanged(audioSessionId: Int) {
        attachAudioSession(audioSessionId)
        player.applyPlayerVolume()
        player.UIs().call { ui ->
            ui.onEqualizerStateChanged(equalizer.state, equalizer.isOperational)
            ui.onVolumeBoostChanged(volumeBoost.gainMb, volumeBoost.isOperational)
        }
    }

    fun toggleMute() {
        val audioReactor = player.audioReactor
        if (player.exoPlayerIsNull() || audioReactor == null) {
            return
        }
        isMuted = !isMuted
        player.applyPlayerVolume()
        if (isMuted) {
            audioReactor.abandonAudioFocus()
        } else {
            audioReactor.requestAudioFocus()
        }
        player.UIs().call { ui -> ui.onMuteUnmuteChanged(isMuted) }
        player.notifyPlaybackUpdateToListeners()
    }

    fun previewEqualizerState(state: EqualizerState) {
        applyEqualizerState(state, false)
    }

    fun updateEqualizerState(state: EqualizerState) {
        applyEqualizerState(state, true)
    }

    fun updateVolumeBoostGainMb(gainMb: Int) {
        val wasEnabled = volumeBoost.isEnabled
        volumeBoost.updateGainMb(gainMb)
        if (wasEnabled != volumeBoost.isEnabled) {
            player.updateAudioTunneling()
        }
        player.UIs().call { ui ->
            ui.onVolumeBoostChanged(volumeBoost.gainMb, volumeBoost.isOperational)
        }
    }

    private fun applyEqualizerState(state: EqualizerState, persist: Boolean) {
        val enabledChanged = equalizer.state.isEnabled != state.isEnabled
        if (persist) {
            equalizer.updateState(state)
        } else {
            equalizer.previewState(state)
        }
        player.applyPlayerVolume()
        if (enabledChanged) {
            player.updateAudioTunneling()
        }
        player.UIs().call { ui ->
            ui.onEqualizerStateChanged(state, equalizer.isOperational)
        }
    }
}
