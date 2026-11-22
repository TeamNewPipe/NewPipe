/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player.ui

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.cast.FCastManager
import org.schabi.newpipe.databinding.PlayerBinding
import org.schabi.newpipe.extractor.stream.ReplayHeatmapSample
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.ReplayHeatmapPeaks
import org.schabi.newpipe.player.helper.PlayerHelper

/** Owns optional replay metadata and its local-player action for one video UI lifecycle. */
class ReplayHeatmapController internal constructor(private val binding: PlayerBinding, private val playback: Playback) : AutoCloseable {
    interface Playback {
        val durationMs: Long
        val positionMs: Long
        val eligible: Boolean
        fun seekTo(positionMs: Long)
    }

    private val context = binding.root.context
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val handler = Handler(Looper.getMainLooper())
    private var closed = false
    private var samples = emptyList<ReplayHeatmapSample>()
    private var peaks = emptyList<Long>()
    private var metadataDuration = 0L
    private var renderedDuration = 0L
    private val castObserver = FCastManager.observeSession { update() }
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        handler.removeCallbacksAndMessages(null)
        handler.post { update() }
    }

    init {
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        binding.nextReplayPeak.setOnClickListener {
            if (isVisible()) {
                ReplayHeatmapPeaks.next(peaks, playback.positionMs)?.let(playback::seekTo)
                update()
            }
        }
        update()
    }

    fun onMetadataChanged(info: StreamInfo) {
        val duration = info.duration.takeIf { it in 1..Long.MAX_VALUE / 1000 }?.times(1000) ?: 0
        setSamples(info.replayHeatmap, duration)
    }

    internal fun setSamples(value: List<ReplayHeatmapSample>, durationMs: Long) {
        val valid = value.filter {
            it.startTimeMs >= 0 && it.startTimeMs < durationMs && it.endTimeMs > it.startTimeMs &&
                it.intensity.isFinite() && it.intensity in 0f..1f
        }.sortedBy { it.startTimeMs }
        samples = valid.takeIf { it.any { sample -> sample.intensity > 0 } }.orEmpty()
        metadataDuration = durationMs
        renderedDuration = 0
        peaks = emptyList()
        update()
    }

    fun clear() {
        samples = emptyList()
        peaks = emptyList()
        metadataDuration = 0
        renderedDuration = 0
        binding.replayHeatmap.setSamples(emptyList(), 0)
        update()
    }

    fun update() {
        if (closed) return
        val duration = minOf(metadataDuration, playback.durationMs).coerceAtLeast(0)
        if (duration != renderedDuration) {
            renderedDuration = duration
            peaks = ReplayHeatmapPeaks.find(samples, duration)
            binding.replayHeatmap.setSamples(samples, duration)
        }
        val visible = isVisible()
        binding.replayHeatmap.visibility = if (visible) View.VISIBLE else View.GONE
        val next = if (visible) ReplayHeatmapPeaks.next(peaks, playback.positionMs) else null
        val button = binding.nextReplayPeak
        val actionVisible = visible && peaks.isNotEmpty()
        if (!actionVisible && button.hasFocus()) binding.playbackSeekBar.requestFocus()
        button.visibility = if (actionVisible) View.VISIBLE else View.GONE
        button.isEnabled = next != null
        button.alpha = if (next == null) 0.45f else 1f
        button.contentDescription = if (next != null) {
            context.getString(R.string.next_replay_peak_at, PlayerHelper.getTimeString(next))
        } else {
            context.getString(R.string.next_replay_peak)
        }
        val seekbar = binding.playbackSeekBar
        binding.replayHeatmap.setTrackInsets(seekbar.paddingLeft, seekbar.paddingRight)
        binding.replayHeatmap.layoutDirection = seekbar.layoutDirection
        seekbar.progressTintList?.defaultColor?.let(binding.replayHeatmap::setAccent)
    }

    private fun isVisible(): Boolean = !closed && preferences.getBoolean(ENABLED_KEY, false) &&
        renderedDuration > 0 && samples.any { it.startTimeMs < renderedDuration && it.intensity > 0 } && playback.eligible

    override fun close() {
        if (closed) return
        clear()
        closed = true
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        castObserver.close()
        handler.removeCallbacksAndMessages(null)
        binding.nextReplayPeak.setOnClickListener(null)
    }

    companion object {
        const val ENABLED_KEY = "player_replay_heatmap"

        @JvmStatic
        fun forPlayer(binding: PlayerBinding, player: Player): ReplayHeatmapController = ReplayHeatmapController(
            binding,
            object : Playback {
                override val durationMs: Long get() = player.getExoPlayer()?.duration ?: 0
                override val positionMs: Long get() = player.getExoPlayer()?.currentPosition ?: 0
                override val eligible: Boolean get() = player.getExoPlayer()?.let {
                    it.isCurrentMediaItemSeekable && !it.isCurrentMediaItemLive &&
                        !it.isCurrentMediaItemDynamic && !FCastManager.isCasting()
                } == true

                override fun seekTo(positionMs: Long) {
                    player.seekTo(positionMs)
                    player.triggerProgressUpdate()
                    SeekHistoryUi.update(binding, player)
                }
            }
        )
    }
}
