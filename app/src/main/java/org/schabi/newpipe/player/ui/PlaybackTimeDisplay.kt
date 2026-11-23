/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.player.ui

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.cast.FCastManager
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.helper.PlayerHelper

/** One duration/remaining-time control, shared by main and popup playback. */
class PlaybackTimeDisplay internal constructor(private val view: TextView, private val playback: Playback) : AutoCloseable {
    fun interface Playback {
        fun read(): Reading
    }

    data class Reading(val durationMs: Long, val remainingMs: Long?)

    private val context = view.context
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val handler = Handler(Looper.getMainLooper())
    private val originalDescription = view.contentDescription
    private val originalClickable = view.isClickable
    private val originalFocusable = view.isFocusable
    private var closed = false
    private var labeledRemainingMode: Boolean? = null
    private var castObserver: AutoCloseable? = null
    private val refresh = Runnable { update() }
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        // Category switches can arrive from settings while the player is paused.
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }

    init {
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        view.isClickable = true
        view.isFocusable = true
        view.setOnClickListener {
            if (!closed && view.isEnabled) {
                preferences.edit().putBoolean(REMAINING_KEY, !showRemaining()).apply()
                update()
            }
        }
        update()
    }

    fun update() {
        if (closed) return
        val reading = playback.read()
        val remaining = showRemaining()
        val time = if (remaining) reading.remainingMs else reading.durationMs.takeIf { it > 0 }
        val value = time?.takeIf { it >= 0 }?.let {
            // Keep the final partial second visible until playback actually reaches the end.
            val displayMs = if (remaining && it in 1..Long.MAX_VALUE - 999) ((it + 999) / 1000) * 1000 else it
            PlayerHelper.getTimeString(displayMs)
        }
        val text = when {
            value == null -> context.getString(R.string.playback_time_unavailable_value)
            remaining -> context.getString(R.string.playback_time_remaining_value, value)
            else -> value
        }
        if (view.text.toString() != text) view.text = text
        view.contentDescription = when {
            value == null -> context.getString(R.string.playback_time_unavailable)
            remaining -> context.getString(R.string.playback_time_remaining_description, value)
            else -> context.getString(R.string.playback_time_total_description, value)
        }
        if (labeledRemainingMode != remaining) {
            labeledRemainingMode = remaining
            val action = context.getString(if (remaining) R.string.playback_time_show_total else R.string.playback_time_show_remaining)
            ViewCompat.replaceAccessibilityAction(view, AccessibilityActionCompat.ACTION_CLICK, action) { _, _ ->
                !closed && view.isEnabled && view.performClick()
            }
        }
    }

    private fun showRemaining(): Boolean = preferences.getBoolean(REMAINING_KEY, false)

    override fun close() {
        if (closed) return
        closed = true
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        handler.removeCallbacks(refresh)
        castObserver?.close()
        castObserver = null
        view.setOnClickListener(null)
        ViewCompat.removeAccessibilityAction(view, AccessibilityActionCompat.ACTION_CLICK.id)
        view.contentDescription = originalDescription
        view.isClickable = originalClickable
        view.isFocusable = originalFocusable
    }

    companion object {
        const val REMAINING_KEY = "player_time_remaining"

        @JvmStatic
        fun forPlayer(view: TextView, player: Player): PlaybackTimeDisplay {
            val display = PlaybackTimeDisplay(view) {
                val exo = player.getExoPlayer()
                Reading(
                    exo?.duration ?: 0,
                    if (FCastManager.isCasting()) null else player.remainingPlaybackTimeMillis()
                )
            }
            display.castObserver = FCastManager.observeSession { display.update() }
            return display
        }
    }
}
