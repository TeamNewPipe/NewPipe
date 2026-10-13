package org.schabi.newpipe.cast.ui

import org.schabi.newpipe.cast.model.CastPlayerState
import org.schabi.newpipe.cast.session.CastSession
import org.schabi.newpipe.cast.session.CastSessionManager
import org.schabi.newpipe.cast.session.CastStateListener
import org.schabi.newpipe.cast.util.CastUtils
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.ui.PlayerUi

class CastPlayerUi(player: Player) : PlayerUi(player) {

    private val castManager = CastSessionManager.getInstance(context)
    private var isCastingActive = false
    private var lastCastPositionMs = 0L
    private var lastLoadedMediaKey: String? = null

    private val castListener = object : CastStateListener {
        override fun onSessionStarted(session: CastSession) {
            isCastingActive = true
            loadCurrentStreamToCast(session)
        }

        override fun onSessionEnded(session: CastSession, error: Throwable?) {
            if (isCastingActive) {
                isCastingActive = false
                lastLoadedMediaKey = null
                // Resume local playback from last cast position
                if (lastCastPositionMs > 0) {
                    player.seekTo(lastCastPositionMs)
                }
                player.play()
            }
        }

        override fun onPlaybackStateChanged(state: CastPlayerState) {
            when (state) {
                CastPlayerState.PLAYING -> player.UIs().call { it.onPlaying() }

                CastPlayerState.PAUSED -> player.UIs().call { it.onPaused() }

                CastPlayerState.BUFFERING -> player.UIs().call { it.onBuffering() }

                else -> {
                    // Idle state requires no player action
                }
            }
        }

        override fun onProgressUpdated(positionMs: Long, durationMs: Long) {
            lastCastPositionMs = positionMs
            val dur = if (durationMs > 0) {
                durationMs.toInt()
            } else if (!player.exoPlayerIsNull()) {
                player.exoPlayer.duration.toInt()
            } else {
                0
            }
            player.UIs().call { it.onUpdateProgress(positionMs.toInt(), dur, 100) }
        }

        override fun onMediaCompleted() {
            // Advance to next item in play queue
            player.playNext()
        }

        override fun onVolumeChanged(volume: Float) {
            // No-op: hardware volume buttons and session handle volume
        }
    }

    override fun initPlayer() {
        castManager.addListener(castListener)
    }

    override fun initPlayback() {
        if (castManager.isConnected) {
            val session = castManager.currentSession
            if (session != null) {
                loadCurrentStreamToCast(session)
            }
        }
    }

    override fun onMetadataChanged(info: StreamInfo) {
        super.onMetadataChanged(info)
        if (castManager.isConnected && isCastingActive) {
            val session = castManager.currentSession
            if (session != null) {
                loadCurrentStreamToCast(session)
            }
        }
    }

    override fun onPlaying() {
        super.onPlaying()
        if (castManager.isConnected && isCastingActive &&
            !player.exoPlayerIsNull() && player.exoPlayer.isPlaying
        ) {
            player.pause()
        }
    }

    override fun destroyPlayer() {
        castManager.removeListener(castListener)
    }

    override fun destroy() {
        castManager.removeListener(castListener)
    }

    private fun loadCurrentStreamToCast(session: CastSession) {
        val currentInfo = player.currentStreamInfo.orElse(null)
        val currentItem = player.currentItem
        val currentPos = if (!player.exoPlayerIsNull()) player.exoPlayer.currentPosition else 0L

        val mediaKey = currentInfo?.let {
            if (!it.url.isNullOrEmpty()) it.url else it.originalUrl
        } ?: currentItem?.url

        if (mediaKey != null && mediaKey == lastLoadedMediaKey &&
            session.getPlayerState() != CastPlayerState.IDLE
        ) {
            return
        }
        lastLoadedMediaKey = mediaKey

        if (currentInfo != null) {
            val streamUrl = selectCastStreamUrl(currentInfo)
            val title = currentInfo.name
            val artist = currentInfo.uploaderName
            val thumbnailUrl = currentInfo.thumbnails?.maxByOrNull { it.width * it.height }?.url

            player.pause()
            session.loadMedia(
                streamUrl = streamUrl,
                title = title,
                artist = artist,
                thumbnailUrl = thumbnailUrl,
                startPositionMs = currentPos
            )
        } else if (currentItem != null) {
            player.pause()
            session.loadMedia(
                streamUrl = currentItem.url,
                title = currentItem.title,
                artist = currentItem.uploader,
                thumbnailUrl = currentItem.thumbnails?.maxByOrNull { it.width * it.height }?.url,
                startPositionMs = currentPos
            )
        }
    }

    private fun selectCastStreamUrl(streamInfo: StreamInfo): String {
        // If it's YouTube, return canonical YouTube URL
        val ytVideoId = CastUtils.extractYouTubeVideoId(streamInfo.originalUrl)
            ?: CastUtils.extractYouTubeVideoId(streamInfo.url)
        if (ytVideoId != null) {
            return if (!streamInfo.originalUrl.isNullOrEmpty()) {
                streamInfo.originalUrl
            } else {
                streamInfo.url
            }
        }

        // For non-YouTube streams: prefer highest resolution progressive MP4 stream
        val videoStreams = streamInfo.videoStreams
        val progressiveMp4 = videoStreams?.filter { it.format?.suffix == "mp4" }
            ?.maxByOrNull { it.height }
        if (progressiveMp4 != null) {
            return progressiveMp4.content
        }

        val hlsUrl = streamInfo.hlsUrl
        if (!hlsUrl.isNullOrEmpty()) {
            return hlsUrl
        }

        val audioStream = streamInfo.audioStreams?.firstOrNull()
        if (audioStream != null) {
            return audioStream.content
        }

        return if (!streamInfo.url.isNullOrEmpty()) streamInfo.url else streamInfo.originalUrl
    }
}
