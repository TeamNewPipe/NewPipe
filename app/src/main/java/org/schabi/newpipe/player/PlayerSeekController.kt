/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Timeline
import org.schabi.newpipe.player.helper.CustomPlaybackControls

/** Owns playback-position queries and user-initiated seek operations. */
internal class PlayerSeekController(private val player: Player) {
    private val userHistory = UserSeekHistory()
    fun isApproachingPlaybackEdge(timeToEndMillis: Long): Boolean {
        if (player.exoPlayerIsNull() || isLive() || !player.isPlaying) {
            return false
        }

        val exoPlayer = player.exoPlayer
        return exoPlayer.duration - exoPlayer.currentPosition < timeToEndMillis
    }

    fun isLiveEdge(): Boolean {
        if (player.exoPlayerIsNull() || !isLive()) {
            return false
        }

        val exoPlayer = player.exoPlayer
        val currentTimeline = exoPlayer.currentTimeline
        val currentWindowIndex = exoPlayer.currentMediaItemIndex
        if (currentTimeline.isEmpty ||
            currentWindowIndex < 0 ||
            currentWindowIndex >= currentTimeline.windowCount
        ) {
            return false
        }

        val timelineWindow = Timeline.Window()
        currentTimeline.getWindow(currentWindowIndex, timelineWindow)
        return timelineWindow.defaultPositionMs <= exoPlayer.currentPosition
    }

    fun seekTo(positionMillis: Long) {
        if (player.isNormalLive() || player.isLive) return
        player.channelSkipController.manualSeek(positionMillis)
        if (Player.DEBUG) {
            Log.d(Player.TAG, "seekBy() called with: position = [$positionMillis]")
        }
        if (!player.exoPlayerIsNull()) {
            val exoPlayer = player.exoPlayer
            val target = resolveSeekPosition(positionMillis, exoPlayer.duration)
            userHistory.record(mediaIdentity(), exoPlayer.currentPosition, target)
            exoPlayer.seekTo(target)
        }
    }

    private fun mediaIdentity(): String {
        val exoPlayer = player.exoPlayer
        val item = exoPlayer.currentMediaItem ?: return ""
        return item.mediaId + "|" + item.localConfiguration?.uri + "|" + exoPlayer.currentMediaItemIndex
    }

    fun seekHistoryBack(): Boolean = navigateHistory(backward = true)

    fun seekHistoryForward(): Boolean = navigateHistory(backward = false)

    fun canSeekHistoryBack(): Boolean {
        return !player.exoPlayerIsNull() && !player.isLive && userHistory.canBack(mediaIdentity())
    }

    fun canSeekHistoryForward(): Boolean {
        return !player.exoPlayerIsNull() && !player.isLive && userHistory.canForward(mediaIdentity())
    }

    private fun navigateHistory(backward: Boolean): Boolean {
        if (player.exoPlayerIsNull() || player.isLive) return false
        val position = if (backward) userHistory.back(mediaIdentity()) else userHistory.forward(mediaIdentity())
        if (position == null) return false
        player.channelSkipController.manualSeek(position)
        player.exoPlayer.seekTo(resolveSeekPosition(position, player.exoPlayer.duration))
        player.triggerProgressUpdate()
        return true
    }

    fun seekToDefault() {
        player.channelSkipController.manualSeek(0)
        if (!player.exoPlayerIsNull()) {
            player.exoPlayer.seekToDefaultPosition()
        }
    }

    fun fastForward() {
        if (Player.DEBUG) {
            Log.d(Player.TAG, "fastForward() called")
        }
        seekBy(CustomPlaybackControls.seekMillis(player.context, forward = true).toLong())
        player.triggerProgressUpdate()
    }

    fun fastRewind() {
        if (Player.DEBUG) {
            Log.d(Player.TAG, "fastRewind() called")
        }
        seekBy(-CustomPlaybackControls.seekMillis(player.context, forward = false).toLong())
        player.triggerProgressUpdate()
    }

    private fun seekBy(offsetMillis: Long) {
        if (Player.DEBUG) {
            Log.d(Player.TAG, "seekBy() called with: offsetMillis = [$offsetMillis]")
        }
        if (!player.exoPlayerIsNull()) {
            seekTo(player.exoPlayer.currentPosition + offsetMillis)
        }
    }

    fun isLive(): Boolean = try {
        !player.exoPlayerIsNull() && player.exoPlayer.isCurrentMediaItemDynamic
    } catch (error: IndexOutOfBoundsException) {
        if (Player.DEBUG) {
            Log.d(Player.TAG, "player.isCurrentWindowDynamic() failed: ", error)
        }
        false
    }

    companion object {
        internal fun resolveSeekPosition(positionMillis: Long, durationMillis: Long): Long {
            val position = positionMillis.coerceAtLeast(0)
            return if (durationMillis == C.TIME_UNSET || durationMillis < 0) {
                position
            } else {
                position.coerceAtMost(durationMillis)
            }
        }
    }
}
