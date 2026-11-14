/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_NO_PERMISSION
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED
import androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
import androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
import androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED
import androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED
import androidx.media3.common.PlaybackException.ERROR_CODE_TIMEOUT
import androidx.media3.common.PlaybackException.ERROR_CODE_UNSPECIFIED
import androidx.media3.common.Player as Media3Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.datasource.InvidiousMediaResponse
import org.schabi.newpipe.player.helper.PlayerDataSource
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.resolver.PlaybackResolver
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver
import org.schabi.newpipe.util.InfoCache

/** Owns playback-error classification, recovery scheduling, and user-facing error reporting. */
internal class PlayerErrorController(
    private val player: Player,
    private val eventDispatcher: PlayerEventDispatcher,
    private val videoResolver: VideoPlaybackResolver
) {
    private val recoveryGuard = PlayerHttpErrorRecovery.RecoveryGuard()
    private val rejectedFormatRecoveryGuard = PlayerHttpErrorRecovery.OneShotRecoveryGuard()
    private val decoderRecoveryGuard = PlayerHttpErrorRecovery.OneShotRecoveryGuard()
    private val surfaceRecoveryGuard = PlayerHttpErrorRecovery.OneShotRecoveryGuard()
    private var audioDecoderRecoveryItem: Pair<Int, String>? = null
    private val audioDecoderRecoveryTracks = mutableSetOf<String?>()
    private val audioTrackRecoveryGuard = PlayerHttpErrorRecovery.OneShotRecoveryGuard()
    private val liveStallRecoveryGuard = PlayerHttpErrorRecovery.OneShotRecoveryGuard()
    private val liveHlsRefreshGuard = PlayerHttpErrorRecovery.OneShotRecoveryGuard()
    private val liveDashRefreshGuard = PlayerHttpErrorRecovery.RecoveryGuard()
    private val liveMedia403Policy = LiveMedia403RecoveryPolicy()
    private val liveUrlRecoveryHealth = LiveUrlRecoveryHealth()
    private val liveManifestRefresher = LiveManifestRefresher(player, videoResolver::isDvrRequiredForLiveStream)
    private val recoveryHandler = Handler(Looper.getMainLooper())
    private var pendingMediaUrlRecovery: Runnable? = null
    private var pendingLiveStallRecovery: Runnable? = null
    private var pendingLiveSegmentRecovery: Runnable? = null
    private var lastPlayingLiveKey: String? = null
    private var recoveringLiveKey: String? = null
    private var liveReadySince: Long? = null
    private val livePreparationRecovery = LivePreparationRecovery(
        player,
        onArchive = {
            videoResolver.clearLiveDvrRequirement()
            videoResolver.clearLiveHlsFallback()
        },
        onStopped = { error, reason ->
            if (!player.exoPlayerIsNull()) player.exoPlayer.pause()
            player.changeState(Player.STATE_PAUSED)
            val message = when (reason) {
                LivePreparationRecovery.StopReason.EXHAUSTED -> R.string.live_recovery_exhausted
                LivePreparationRecovery.StopReason.ENDED -> R.string.live_recovery_ended
                LivePreparationRecovery.StopReason.UNAVAILABLE -> R.string.live_recovery_unavailable
            }
            createErrorNotification(error, "livePreparation=${reason.name}", message)
            eventDispatcher.notifyPlayerError(error, true)
        }
    )

    fun onPlayerError(error: PlaybackException) {
        liveUrlRecoveryHealth.reset()
        liveReadySince = null
        cancelPendingLiveStallRecovery()
        cancelPendingLiveSegmentRecovery()
        liveMedia403Policy.clearFailureWindow()
        if (player.playQueue?.item?.let { it.isRadio || it.isLiveTv } == true) {
            val message = if (player.playQueue?.item?.isLiveTv == true) R.string.tv_playback_failed else R.string.radio_playback_failed
            Toast.makeText(player.context, message, Toast.LENGTH_LONG).show()
            player.onPlaybackShutdown()
            return
        }
        Log.e(Player.TAG, "ExoPlayer - onPlayerError() called with:", error)

        if (player.videoAdjustments.recover(error)) return

        player.saveStreamProgressState()
        if (livePreparationRecovery.recover(error)) return
        if (tryRecoverFromVideoSurfaceFailure(error) ||
            tryRecoverFromAudioDecoderOutputFailure(error) ||
            tryRecoverFromAudioTrackInitFailure(error)
        ) {
            return
        }

        val downloaded = org.schabi.newpipe.player.mediaitem.MediaItemTag.from(player.exoPlayer.currentMediaItem)
            .flatMap { it.maybeStreamInfo }.orElse(null) as? org.schabi.newpipe.download.DownloadedStreamInfo
        if (downloaded != null && org.schabi.newpipe.download.DownloadedCopyRepository.reject(downloaded)) {
            player.setRecovery()
            player.reloadPlayQueueManager()
            return
        }
        if (tryRecoverFromYouTubeAv1DecoderFailure(error)) {
            return
        }
        if (tryRecoverFromYouTubeMediaUrlFailure(error)) {
            return
        }

        var isCatchableException = false
        when (error.errorCode) {
            ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                isCatchableException = true
                player.exoPlayer.seekToDefaultPosition()
                player.exoPlayer.prepare()
                player.onBuffering()
            }

            ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> {
                if (generateSequence(error as Throwable) { it.cause }.any { it is InvidiousMediaResponse.InvalidResponseException }) {
                    isCatchableException = true
                    if (!player.exoPlayerIsNull()) {
                        player.exoPlayer.pause()
                    }
                    player.changeState(Player.STATE_PAUSED)
                    createErrorNotification(error, toastMessageRes = R.string.invidious_media_error)
                } else if (!player.exoPlayerIsNull()) {
                    player.playQueue?.error()
                }
            }

            ERROR_CODE_IO_BAD_HTTP_STATUS,
            ERROR_CODE_IO_FILE_NOT_FOUND,
            ERROR_CODE_IO_NO_PERMISSION,
            ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
            ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
            ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> {
                if (!player.exoPlayerIsNull()) {
                    player.playQueue?.error()
                }
            }

            ERROR_CODE_TIMEOUT,
            ERROR_CODE_IO_UNSPECIFIED,
            ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            ERROR_CODE_UNSPECIFIED -> {
                player.setRecovery()
                player.reloadPlayQueueManager()
            }

            else -> player.onPlaybackShutdown()
        }

        if (!isCatchableException) {
            createErrorNotification(error)
        }
        eventDispatcher.notifyPlayerError(error, isCatchableException)
    }

    fun onPlayWhenReadyChanged(playWhenReady: Boolean) {
        livePreparationRecovery.onPlayWhenReadyChanged(playWhenReady)
        if (!playWhenReady) {
            liveUrlRecoveryHealth.reset()
            liveReadySince = null
            cancelPendingLiveStallRecovery()
            cancelPendingLiveSegmentRecovery()
            liveMedia403Policy.clearFailureWindow()
        } else if (!player.exoPlayerIsNull()) {
            // Resuming during an existing buffering state does not emit a new state event.
            onPlaybackStateChanged(player.exoPlayer.playbackState)
        }
    }

    fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState != Media3Player.STATE_READY) liveUrlRecoveryHealth.reset()
        livePreparationRecovery.onPlaybackStateChanged(playbackState)
        val item = player.playQueue?.item
        val key = item?.let { "${it.serviceId}:${it.url}" }
        when (playbackState) {
            Media3Player.STATE_READY -> {
                cancelPendingLiveStallRecovery()
                if (item != null && !player.isNormalLive() && isYouTubeManifestLive(item) &&
                    player.exoPlayer.isCurrentMediaItemSeekable && player.exoPlayer.duration > 60_000L
                ) {
                    videoResolver.requireDvrForLiveStream(item.url)
                }
                if (key != lastPlayingLiveKey) liveReadySince = null
                if (player.playWhenReady && liveReadySince == null) {
                    liveReadySince = SystemClock.elapsedRealtime()
                }
                recoveringLiveKey = null
                lastPlayingLiveKey = if (item != null && player.playWhenReady &&
                    isRecoverableLiveManifest(item)
                ) {
                    key
                } else {
                    null
                }
            }

            Media3Player.STATE_BUFFERING -> {
                // A successful recovery must not exhaust DASH refresh for the whole broadcast.
                // Brief READY flickers still count as the same failure and retain the HLS limit.
                val readySince = liveReadySince
                liveReadySince = null
                if (key == lastPlayingLiveKey && readySince != null &&
                    SystemClock.elapsedRealtime() - readySince >= LIVE_RECOVERY_STABLE_MILLIS
                ) {
                    liveDashRefreshGuard.reset()
                }
                if (key == null ||
                    !player.playWhenReady || item == null || !isRecoverableLiveManifest(item) ||
                    pendingLiveStallRecovery != null
                ) {
                    return
                }

                // Initial manifest/segment loading gets more time than a playback interruption.
                // Metadata can arrive after BUFFERING; onEvents rechecks eligibility then.
                val timeout = if (key != lastPlayingLiveKey && key != recoveringLiveKey) {
                    LIVE_STARTUP_TIMEOUT_MILLIS
                } else {
                    LIVE_STALL_TIMEOUT_MILLIS
                }
                var lastBufferedPosition = player.exoPlayer.bufferedPosition
                var lastPlaybackPosition = player.exoPlayer.currentPosition
                var lastProgressAt = SystemClock.elapsedRealtime()
                val recovery = object : Runnable {
                    override fun run() {
                        pendingLiveStallRecovery = null
                        val queue = player.playQueue ?: return
                        val currentItem = queue.item ?: return
                        val currentKey = "${currentItem.serviceId}:${currentItem.url}"
                        val info = player.currentStreamInfo.orElse(null) ?: return
                        if (currentKey != key || !player.playWhenReady || player.exoPlayerIsNull() ||
                            player.exoPlayer.playbackState != Media3Player.STATE_BUFFERING ||
                            !isRecoverableLiveManifest(currentItem)
                        ) {
                            return
                        }

                        // Buffering alone is not a deadlock: slow downloads can still fill the
                        // buffer. Restart only after a full interval without playback/buffer progress.
                        val engine = player.exoPlayer
                        val now = SystemClock.elapsedRealtime()
                        if (engine.bufferedPosition > lastBufferedPosition ||
                            engine.currentPosition > lastPlaybackPosition
                        ) {
                            lastProgressAt = now
                        }
                        lastBufferedPosition = engine.bufferedPosition
                        lastPlaybackPosition = engine.currentPosition
                        if (now - lastProgressAt < timeout) {
                            pendingLiveStallRecovery = this
                            recoveryHandler.postDelayed(this, LIVE_STALL_POLL_MILLIS)
                            return
                        }

                        if (!player.isNormalLive() &&
                            (videoResolver.isDvrRequiredForLiveStream(info.url) || canFallBackToLiveHls(currentItem))
                        ) {
                            if (!prepareFullLiveDashRecovery(currentItem, key)) return
                        } else if (canFallBackToLiveHls(currentItem)) {
                            if (!liveStallRecoveryGuard.acquire(key)) return
                            Log.w(Player.TAG, "Live DASH stalled after recovery; retrying with HLS")
                            videoResolver.preferHlsForLiveStream(info.url)
                            queue.unsetRecovery(queue.index)
                        } else {
                            if (!liveHlsRefreshGuard.acquire(key)) return
                            Log.w(Player.TAG, "Live HLS stalled while buffering; refreshing stream URLs once")
                            player.setRecovery()
                            videoResolver.preferHlsForLiveStream(info.url)
                            invalidateYouTubeMediaCaches(currentItem)
                        }
                        // Reloading may emit IDLE then BUFFERING without ever reaching READY.
                        // Keep this key so a stalled HLS fallback can still be recovered.
                        recoveringLiveKey = key
                        player.reloadPlayQueueManager()
                    }
                }
                pendingLiveStallRecovery = recovery
                recoveryHandler.postDelayed(recovery, LIVE_STALL_POLL_MILLIS)
            }

            else -> {
                cancelPendingLiveStallRecovery()
                lastPlayingLiveKey = null
                liveReadySince = null
            }
        }
    }

    fun onPositionDiscontinuity() {
        liveUrlRecoveryHealth.reset()
    }

    fun resetRecovery() {
        liveUrlRecoveryHealth.reset()
        livePreparationRecovery.reset()
        cancelPendingMediaUrlRecovery()
        cancelPendingLiveStallRecovery()
        cancelPendingLiveSegmentRecovery()
        lastPlayingLiveKey = null
        liveReadySince = null
        recoveringLiveKey = null
        recoveryGuard.reset()
        rejectedFormatRecoveryGuard.reset()
        videoResolver.clearRejectedVideoStream()
        decoderRecoveryGuard.reset()
        audioDecoderRecoveryItem = null
        audioDecoderRecoveryTracks.clear()
        player.clearAudioDecoderFallback()
        audioTrackRecoveryGuard.reset()
        surfaceRecoveryGuard.reset()
        liveStallRecoveryGuard.reset()
        liveHlsRefreshGuard.reset()
        liveDashRefreshGuard.reset()
        liveMedia403Policy.reset()
        videoResolver.clearRejectedVideoCodecFamily()
        videoResolver.clearLiveHlsFallback()
        videoResolver.clearLiveDvrRequirement()
    }

    private fun isRecoverableLiveManifest(item: PlayQueueItem): Boolean {
        return isYouTubeManifestLive(item) && player.videoPlayerSelected()
    }

    private fun isYouTubeManifestLive(item: PlayQueueItem): Boolean {
        val info = player.currentStreamInfo.orElse(null) ?: return false
        return info.url == item.url && info.serviceId == item.serviceId &&
            info.streamType == StreamType.LIVE_STREAM &&
            PlayerHttpErrorRecovery.isYouTubeService(item.serviceId) &&
            PlaybackResolver.isManifestOnlyYoutubeLive(info)
    }

    /** Refresh rejected media URLs before the existing buffer has drained. */
    internal fun onLiveMediaSegmentRejected(engine: ExoPlayer, trackType: Int, mediaStartMs: Long) {
        val item = player.playQueue?.item ?: return
        if (player.getExoPlayer() !== engine || !player.playWhenReady ||
            !isActivePlaybackState(engine) ||
            !isYouTubeManifestLive(item) || pendingLiveSegmentRecovery != null
        ) {
            return
        }
        liveUrlRecoveryHealth.reset()
        if (!liveMedia403Policy.onRejectedSegment(
                "${item.serviceId}:${item.url}",
                trackType,
                mediaStartMs,
                SystemClock.elapsedRealtime()
            )
        ) {
            return
        }

        val serviceId = item.serviceId
        val url = item.url
        val recovery = Runnable {
            pendingLiveSegmentRecovery = null
            val currentItem = player.playQueue?.item ?: return@Runnable
            if (player.getExoPlayer() !== engine || !player.playWhenReady ||
                !isActivePlaybackState(engine) ||
                currentItem.serviceId != serviceId || currentItem.url != url ||
                !isYouTubeManifestLive(currentItem)
            ) {
                return@Runnable
            }
            Log.w(Player.TAG, "Repeated live media HTTP 403; refreshing stream URLs")
            cancelPendingMediaUrlRecovery()
            cancelPendingLiveStallRecovery()
            val refreshing = liveManifestRefresher.refresh(engine, currentItem) { refreshed ->
                if (player.getExoPlayer() !== engine || player.playQueue?.item !== currentItem || !player.playWhenReady) return@refresh
                if (refreshed) {
                    if (engine.playbackState == Media3Player.STATE_BUFFERING) onPlaybackStateChanged(Media3Player.STATE_BUFFERING)
                } else {
                    reloadRejectedLiveSource(currentItem)
                }
            }
            if (!refreshing) reloadRejectedLiveSource(currentItem)
        }
        pendingLiveSegmentRecovery = recovery
        recoveryHandler.post(recovery)
    }

    internal fun onLiveMediaSegmentLoaded(engine: ExoPlayer, trackType: Int, mediaStartMs: Long) {
        val item = player.playQueue?.item ?: return
        if (player.getExoPlayer() !== engine || !player.playWhenReady ||
            !isYouTubeManifestLive(item)
        ) {
            return
        }
        // Sliding live windows can keep currentPosition nearly constant at the live edge.
        // Compare positions in the underlying period so normal window updates still count.
        val timeline = engine.currentTimeline
        val periodOffsetMs = if (timeline != null && engine.currentMediaItemIndex in 0 until timeline.windowCount) {
            timeline.getWindow(engine.currentMediaItemIndex, Timeline.Window()).positionInFirstPeriodUs
                .takeUnless { it == C.TIME_UNSET }?.div(1_000) ?: 0L
        } else {
            0L
        }
        if (liveUrlRecoveryHealth.onProgress(
                "${item.serviceId}:${item.url}",
                SystemClock.elapsedRealtime(),
                engine.currentPosition + periodOffsetMs,
                engine.isPlaying
            )
        ) {
            // A later interruption on a healthy broadcast gets its own bounded URL budget.
            // Manifest downloads or READY flickers without playback progress do not replenish it.
            recoveryGuard.reset()
        }
        liveMedia403Policy.onLoadedSegment(
            "${item.serviceId}:${item.url}",
            trackType,
            mediaStartMs,
            SystemClock.elapsedRealtime(),
            engine.playbackState == Media3Player.STATE_READY
        )
    }

    private fun isActivePlaybackState(engine: ExoPlayer): Boolean {
        return engine.playbackState == Media3Player.STATE_READY ||
            engine.playbackState == Media3Player.STATE_BUFFERING
    }

    private fun prepareFullLiveDashRecovery(item: PlayQueueItem, key: String): Boolean {
        if (liveDashRefreshGuard.acquireAttempt(key) == null) {
            // HLS can discard hours of DVR history. Stop after bounded DASH retries
            // instead of silently changing the user's Full live selection.
            player.setRecovery()
            player.exoPlayer.pause()
            player.changeState(Player.STATE_PAUSED)
            val error = PlaybackException(
                "Full live DVR recovery exhausted; retry playback or select Normal live",
                null,
                ERROR_CODE_TIMEOUT
            )
            createErrorNotification(error, "liveDvrRecovery=exhausted")
            eventDispatcher.notifyPlayerError(error, true)
            return false
        }
        Log.w(Player.TAG, "Full live DASH stalled; refreshing URLs while preserving rewind")
        player.setRecovery()
        invalidateYouTubeMediaCaches(item)
        recoveringLiveKey = key
        return true
    }

    private fun canFallBackToLiveHls(item: PlayQueueItem): Boolean {
        val info = player.currentStreamInfo.orElse(null) ?: return false
        return PlayerHttpErrorRecovery.isYouTubeService(item.serviceId) &&
            PlaybackResolver.isManifestOnlyYoutubeLive(info) &&
            info.dashMpdUrl.isNotEmpty() &&
            !videoResolver.isHlsPreferredForLiveStream(info.url) &&
            // The video resolver still owns the live source while its video renderer is
            // temporarily disabled in the background. True audio-player sources are excluded.
            player.videoPlayerSelected()
    }

    private fun cancelPendingLiveStallRecovery() {
        pendingLiveStallRecovery?.let(recoveryHandler::removeCallbacks)
        pendingLiveStallRecovery = null
    }

    private fun cancelPendingLiveSegmentRecovery() {
        liveManifestRefresher.cancel()
        pendingLiveSegmentRecovery?.let(recoveryHandler::removeCallbacks)
        pendingLiveSegmentRecovery = null
    }

    private fun reloadRejectedLiveSource(item: PlayQueueItem) {
        player.setRecovery()
        player.onBuffering()
        invalidateYouTubeMediaCaches(item)
        recoveringLiveKey = "${item.serviceId}:${item.url}"
        player.reloadPlayQueueManager()
    }

    internal fun tryRecoverFromVideoSurfaceFailure(error: PlaybackException): Boolean {
        val releasedCodec = error.errorCode == PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK &&
            generateSequence(error as Throwable) { it.cause }.any {
                it is android.media.MediaCodec.CodecException &&
                    it.message?.contains("setSurface()") == true &&
                    it.message?.contains("Released state") == true
            }
        val detachTimeout = error.errorCode == PlaybackException.ERROR_CODE_TIMEOUT &&
            generateSequence(error as Throwable) { it.cause }.any {
                it is androidx.media3.exoplayer.ExoTimeoutException &&
                    it.timeoutOperation ==
                    androidx.media3.exoplayer.ExoTimeoutException.TIMEOUT_OPERATION_DETACH_SURFACE
            }
        if ((!releasedCodec && !detachTimeout) || player.exoPlayerIsNull()) {
            return false
        }
        val item = player.playQueue?.item ?: return false
        if (!surfaceRecoveryGuard.acquire("${item.serviceId}:${item.url}")) return false

        Log.w(Player.TAG, "Rebuilding playback with safe surface switching after output failure")
        cancelPendingMediaUrlRecovery()
        player.onBuffering()
        player.restartForSurfaceRecovery()
        return true
    }

    internal fun tryRecoverFromAudioDecoderOutputFailure(error: PlaybackException): Boolean {
        val rendererType = (error as? ExoPlaybackException)
            ?.rendererFormat
            ?.sampleMimeType
            ?.let { MimeTypes.getTrackType(it) }
            ?: if (error.message?.contains("MediaCodecAudioRenderer") == true) {
                C.TRACK_TYPE_AUDIO
            } else {
                C.TRACK_TYPE_UNKNOWN
            }
        if (!PlayerHttpErrorRecovery.isRecoverableAudioDecoderOutputFailure(
                error.errorCode,
                rendererType,
                error
            ) || player.exoPlayerIsNull()
        ) {
            return false
        }

        val item = player.playQueue?.item ?: return false
        val recoveryItem = item.serviceId to item.url
        if (audioDecoderRecoveryItem != recoveryItem) {
            audioDecoderRecoveryItem = recoveryItem
            audioDecoderRecoveryTracks.clear()
        }
        // A language switch needs its own attempt; returning to an already failed track must
        // not replenish the budget or cause an endless AAC/Opus recovery cycle.
        val trackId = player.selectedAudioStream.orElse(null)?.audioTrackId
        if (!audioDecoderRecoveryTracks.add(trackId)) return false

        if ((error as? ExoPlaybackException)?.rendererFormat?.sampleMimeType == MimeTypes.AUDIO_AAC) {
            player.enableAudioDecoderFallback(item)
        }
        Log.w(Player.TAG, "Recreating playback engine after audio decoder output failure")
        player.setRecovery()
        player.onBuffering()
        cancelPendingMediaUrlRecovery()
        player.restartForAudioTrackRecovery()
        return true
    }

    private fun tryRecoverFromAudioTrackInitFailure(error: PlaybackException): Boolean {
        if (!PlayerHttpErrorRecovery.isRecoverableAudioTrackInitFailure(error.errorCode)) {
            return false
        }

        val item = player.playQueue?.item ?: return false
        val recoveryKey = "${item.serviceId}:${item.url}"
        if (!audioTrackRecoveryGuard.acquire(recoveryKey)) {
            return false
        }

        Log.w(
            Player.TAG,
            "Recreating playback engine after AudioTrack initialization failure"
        )
        player.setRecovery()
        player.onBuffering()
        cancelPendingMediaUrlRecovery()
        player.restartForAudioTrackRecovery()
        return true
    }
    private fun tryRecoverFromYouTubeAv1DecoderFailure(error: PlaybackException): Boolean {
        val item = player.playQueue?.item ?: return false
        if (!PlayerHttpErrorRecovery.isYouTubeService(item.serviceId)) {
            return false
        }

        val quality = player.currentMetadata?.maybeQuality?.orElse(null) ?: return false
        val stream = player.selectedVideoStream.orElse(null)
            ?: (error as? ExoPlaybackException)?.rendererFormat?.id?.let { formatId ->
                quality.sortedVideoStreams.firstOrNull { candidate ->
                    candidate.itag.toString() == formatId
                }
            }
            ?: return false
        if (!PlayerHttpErrorRecovery.isRecoverableAv1DecoderInitFailure(
                error.errorCode,
                stream
            )
        ) {
            return false
        }

        if (!VideoPlaybackResolver.hasAlternativeCodecFamily(
                quality.sortedVideoStreams,
                stream.codec
            )
        ) {
            return false
        }

        val recoveryKey = "${item.serviceId}:${item.url}"
        if (!decoderRecoveryGuard.acquire(recoveryKey)) {
            return false
        }

        Log.w(
            Player.TAG,
            "Retrying YouTube playback after AV1 decoder initialization failure " +
                "with a different codec family"
        )
        player.setRecovery()
        player.onBuffering()
        cancelPendingMediaUrlRecovery()
        videoResolver.rejectVideoCodecFamilyForStream(item.url, stream.codec)
        PlayerDataSource.invalidateYoutubeManifestCaches()
        player.reloadPlayQueueManager()
        return true
    }
    private fun tryRecoverFromYouTubeMediaUrlFailure(error: PlaybackException): Boolean {
        val item = player.playQueue?.item ?: return false
        if (!PlayerHttpErrorRecovery.isRecoverableYouTubeMediaUrlFailure(error, item)) {
            return false
        }

        val recoveryKey = "${item.serviceId}:${item.url}"
        val attempt = recoveryGuard.acquireAttempt(recoveryKey)
        if (attempt == null) {
            Log.w(
                Player.TAG,
                "YouTube media URL recovery exhausted after " +
                    "${PlayerHttpErrorRecovery.RecoveryGuard.MAX_ATTEMPTS} attempts"
            )
            cancelPendingMediaUrlRecovery()
            invalidateYouTubeMediaCaches(item)
            recoveryGuard.reset()
            if (tryRecoverFromLiveDashHttpFailure(error, item) ||
                tryRecoverFromRejectedYouTubeVideo(error, item)
            ) {
                return true
            }
            if (!player.exoPlayerIsNull()) {
                player.exoPlayer.pause()
            }
            player.changeState(Player.STATE_PAUSED)
            createErrorNotification(
                error,
                "recovery=exhausted, attempts=" +
                    "${PlayerHttpErrorRecovery.RecoveryGuard.MAX_ATTEMPTS}/" +
                    PlayerHttpErrorRecovery.RecoveryGuard.MAX_ATTEMPTS
            )
            eventDispatcher.notifyPlayerError(error, true)
            return true
        }

        player.setRecovery()
        player.onBuffering()
        cancelPendingMediaUrlRecovery()

        val recoveryItemServiceId = item.serviceId
        val recoveryItemUrl = item.url
        val recovery = Runnable {
            pendingMediaUrlRecovery = null
            val currentQueueItem = player.playQueue?.item ?: return@Runnable
            if (currentQueueItem.serviceId != recoveryItemServiceId ||
                currentQueueItem.url != recoveryItemUrl
            ) {
                return@Runnable
            }

            val responseCode = PlayerHttpErrorRecovery.findInvalidResponseCode(error)
            Log.w(
                Player.TAG,
                "Refreshing YouTube StreamInfo after recoverable media URL failure" +
                    " (status=${responseCode ?: "network"}, attempt=${attempt.number}/" +
                    "${PlayerHttpErrorRecovery.RecoveryGuard.MAX_ATTEMPTS})"
            )
            player.selectedVideoStream
                .filter { stream ->
                    PlayerHttpErrorRecovery.shouldAvoidAndroidVrAv1HfrStream(error, stream)
                }
                .ifPresent { stream ->
                    videoResolver.rejectVideoStreamOnce(recoveryItemUrl, stream.itag)
                }
            invalidateYouTubeMediaCaches(item)
            player.reloadPlayQueueManager()
        }
        pendingMediaUrlRecovery = recovery
        recoveryHandler.postDelayed(recovery, attempt.delayMillis)
        return true
    }

    private fun tryRecoverFromRejectedYouTubeVideo(
        error: PlaybackException,
        item: PlayQueueItem
    ): Boolean {
        val info = player.currentStreamInfo.orElse(null) ?: return false
        val selected = player.selectedVideoStream.orElse(null) ?: return false
        if (!PlayerHttpErrorRecovery.isYouTubeService(item.serviceId) ||
            !player.videoPlayerSelected() || info.streamType != StreamType.VIDEO_STREAM ||
            !PlayerHttpErrorRecovery.isRejectedVideoFormat(error, selected) ||
            (info.videoStreams + info.videoOnlyStreams).none {
                it.itag > 0 && it.itag != selected.itag
            } ||
            !rejectedFormatRecoveryGuard.acquire("${item.serviceId}:${item.url}")
        ) {
            return false
        }

        Log.w(Player.TAG, "Fresh YouTube URLs remain rejected; trying another video format once")
        player.setRecovery()
        player.onBuffering()
        videoResolver.rejectVideoStreamForCurrentItem(item.url, selected.itag)
        player.reloadPlayQueueManager()
        return true
    }

    internal fun tryRecoverFromLiveDashHttpFailure(
        error: PlaybackException,
        item: PlayQueueItem
    ): Boolean {
        if (!player.isNormalLive() ||
            PlayerHttpErrorRecovery.findInvalidResponseCode(error) != 403 ||
            !canFallBackToLiveHls(item) ||
            !liveStallRecoveryGuard.acquire("${item.serviceId}:${item.url}")
        ) {
            return false
        }

        val info = player.currentStreamInfo.orElse(null) ?: return false
        val queue = player.playQueue ?: return false
        Log.w(Player.TAG, "Live DASH HTTP 403 recovery exhausted; retrying with HLS")
        videoResolver.preferHlsForLiveStream(info.url)
        recoveringLiveKey = "${item.serviceId}:${item.url}"
        lastPlayingLiveKey = null
        queue.unsetRecovery(queue.index)
        player.onBuffering()
        player.reloadPlayQueueManager()
        return true
    }

    private fun cancelPendingMediaUrlRecovery() {
        pendingMediaUrlRecovery?.let(recoveryHandler::removeCallbacks)
        pendingMediaUrlRecovery = null
    }

    private fun invalidateYouTubeMediaCaches(item: PlayQueueItem) {
        PlayerDataSource.invalidateYoutubeManifestCaches()
        InfoCache.getInstance().removeInfo(item.serviceId, item.url, InfoCache.Type.STREAM)
    }

    private fun createErrorNotification(
        error: PlaybackException,
        recoveryDiagnostic: String? = null,
        toastMessageRes: Int = R.string.error_report_notification_toast
    ) {
        val diagnosticSuffix = buildString {
            PlayerHttpErrorRecovery.buildSafeErrorContext(error)?.let { safeErrorContext ->
                append(" [$safeErrorContext]")
            }
            recoveryDiagnostic?.let { diagnostic ->
                append(" [$diagnostic]")
            }
        }

        val metadata = player.currentMetadata
        val errorInfo = if (metadata == null) {
            ErrorInfo(
                error,
                UserAction.PLAY_STREAM,
                "Player error[type=${error.errorCodeName}] occurred, " +
                    "currentMetadata is null$diagnosticSuffix"
            )
        } else {
            ErrorInfo(
                error,
                UserAction.PLAY_STREAM,
                "Player error[type=${error.errorCodeName}] occurred while playing " +
                    "${metadata.streamUrl}$diagnosticSuffix",
                metadata.serviceId,
                metadata.streamUrl
            )
        }
        ErrorUtil.createNotification(player.context, errorInfo, toastMessageRes)
    }

    private companion object {
        const val LIVE_STALL_POLL_MILLIS = 1_000L
        const val LIVE_STALL_TIMEOUT_MILLIS = 10_000L
        const val LIVE_STARTUP_TIMEOUT_MILLIS = 30_000L
        const val LIVE_RECOVERY_STABLE_MILLIS = 30_000L
    }
}
