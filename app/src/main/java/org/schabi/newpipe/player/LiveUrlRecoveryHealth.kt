/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

/** Requires successful media loads and advancing playback before renewing the URL retry budget. */
internal class LiveUrlRecoveryHealth {
    private var key: String? = null
    private var startedAt: Long? = null
    private var lastPosition: Long? = null
    private var lastAt: Long? = null

    fun onProgress(streamKey: String, nowMs: Long, positionMs: Long, playing: Boolean): Boolean {
        if (key != streamKey || !playing || positionMs < 0 ||
            lastPosition?.let { positionMs < it } == true ||
            lastAt?.let { nowMs < it } == true
        ) {
            reset()
        }
        key = streamKey
        if (!playing || positionMs < 0) return false
        if (lastPosition == positionMs) {
            // Audio and video callbacks can report the same playback millisecond. Ignore those
            // duplicates, but do not treat an unmoving renderer as sustained healthy playback.
            if (lastAt?.let { nowMs - it >= 5_000 } == true) reset()
            return false
        }
        if (startedAt == null) startedAt = nowMs
        lastPosition = positionMs
        lastAt = nowMs
        return nowMs - (startedAt ?: nowMs) >= STABLE_PLAYBACK_MS
    }

    fun reset() {
        key = null
        startedAt = null
        lastPosition = null
        lastAt = null
    }

    private companion object {
        const val STABLE_PLAYBACK_MS = 30_000L
    }
}
