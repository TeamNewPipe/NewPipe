/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import androidx.media3.common.PlaybackParameters
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.helper.ChannelPlaybackProfileManager
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.mediaitem.MediaItemTag
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.util.StreamTypeUtil

/** Owns playback parameter application, persistence, and channel-profile restoration. */
internal class PlaybackParametersController(private val player: Player) {
    private var streamType: StreamType? = null
    private var streamUrl: String? = null

    val skipSilenceAvailable: Boolean
        get() {
            val engine = player.getExoPlayer()
            val tag = engine?.currentMediaItem?.let { MediaItemTag.from(it).orElse(null) }
            // Queue synchronization can run before the engine switches items. Only use timeline
            // information belonging to this item, including lives misclassified by an extractor.
            val liveTimeline = tag?.streamUrl == streamUrl && engine != null &&
                engine.isCurrentMediaItemLive && engine.isCurrentMediaItemDynamic
            return !isLivePlayback(streamType, liveTimeline)
        }

    fun setStream(item: PlayQueueItem?) {
        streamType = item?.streamType
        streamUrl = item?.url
    }

    private val preferredSkipSilence: Boolean
        get() = player.prefs.getBoolean(
            player.context.getString(R.string.playback_skip_silence_key),
            false
        )

    val parameters: PlaybackParameters
        get() {
            return if (player.exoPlayerIsNull()) {
                PlaybackParameters.DEFAULT
            } else {
                player.exoPlayer.playbackParameters
            }
        }

    val speed: Float
        get() = parameters.speed

    val pitch: Float
        get() = parameters.pitch

    val skipSilence: Boolean
        get() = !player.exoPlayerIsNull() && player.exoPlayer.skipSilenceEnabled

    fun setSpeed(speed: Float) {
        setParameters(speed, pitch, skipSilence)
    }

    fun setSpeedTemporarily(speed: Float) {
        if (!player.exoPlayerIsNull()) {
            player.exoPlayer.playbackParameters = PlaybackParameters(speed, pitch)
        }
    }

    fun setParameters(speed: Float, pitch: Float, skipSilence: Boolean) {
        val roundedSpeed = Math.round(speed * DECIMAL_SCALE) / DECIMAL_SCALE
        val roundedPitch = Math.round(pitch * DECIMAL_SCALE) / DECIMAL_SCALE
        val currentInfo = player.currentStreamInfo.orElse(null)
        // Skip silence is unavailable on active live timelines, but playback speed and pitch
        // remain user-controlled, including when playing behind the live edge.
        val requestedSkipSilence = if (skipSilenceAvailable) skipSilence else preferredSkipSilence

        if (
            ChannelPlaybackProfileManager.saveSpeed(
                player.context,
                currentInfo,
                player.currentItem,
                roundedSpeed
            )
        ) {
            player.prefs.edit()
                .putFloat(player.context.getString(R.string.playback_pitch_key), roundedPitch)
                .putBoolean(
                    player.context.getString(R.string.playback_skip_silence_key),
                    requestedSkipSilence
                )
                .apply()
        } else {
            PlayerHelper.savePlaybackParametersToPrefs(
                player,
                roundedSpeed,
                roundedPitch,
                requestedSkipSilence
            )
        }
        applyParameters(roundedSpeed, roundedPitch, requestedSkipSilence)
    }

    fun applyParameters(speed: Float, pitch: Float, skipSilence: Boolean) {
        player.exoPlayer.playbackParameters = PlaybackParameters(speed, pitch)
        player.exoPlayer.skipSilenceEnabled = skipSilence && skipSilenceAvailable
    }

    fun refreshSkipSilence() {
        if (!player.exoPlayerIsNull()) {
            player.exoPlayer.skipSilenceEnabled = preferredSkipSilence && skipSilenceAvailable
        }
    }

    fun applySpeedProfile(item: PlayQueueItem) {
        setStream(item)
        val profileSpeed = if (ChannelPlaybackProfileManager.isAvailable(player.context, item)) {
            ChannelPlaybackProfileManager.getSpeed(player.context, item)
        } else {
            null
        }
        val preferredSpeed = PlayerHelper.retrievePlaybackParametersFromPrefs(player).speed
        val targetSpeed = resolvePlaybackSpeed(item.streamType, profileSpeed, preferredSpeed)
        player.exoPlayer.playbackParameters = PlaybackParameters(targetSpeed, pitch)
        refreshSkipSilence()
    }

    fun applySpeedProfile(info: StreamInfo) {
        streamType = info.streamType
        streamUrl = info.url
        val profileSpeed = if (ChannelPlaybackProfileManager.isAvailable(player.context, info)) {
            ChannelPlaybackProfileManager.getSpeed(player.context, info)
        } else {
            null
        }
        val preferredSpeed = PlayerHelper.retrievePlaybackParametersFromPrefs(player).speed
        val targetSpeed = resolvePlaybackSpeed(info.streamType, profileSpeed, preferredSpeed)
        player.exoPlayer.playbackParameters = PlaybackParameters(targetSpeed, pitch)
        refreshSkipSilence()
    }

    companion object {
        const val NORMAL_SPEED = 1.0f
        private const val DECIMAL_SCALE = 100.0f

        internal fun isLivePlayback(streamType: StreamType?, liveTimeline: Boolean): Boolean = liveTimeline || streamType?.let(StreamTypeUtil::isLiveStream) == true

        @JvmStatic
        fun resolvePlaybackSpeed(
            streamType: StreamType?,
            profileSpeed: Float?,
            preferredSpeed: Float
        ): Float {
            // A channel or global speed preference also applies to active livestreams.
            // Forcing live streams back to 1× loses explicit choices after DASH/HLS updates.
            return profileSpeed ?: preferredSpeed
        }
    }
}
