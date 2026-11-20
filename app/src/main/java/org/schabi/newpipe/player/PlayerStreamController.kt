/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.content.Context
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import java.io.IOException
import org.schabi.newpipe.DownloaderImpl
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.radio.secureRadioUrl
import org.schabi.newpipe.player.helper.ChannelPlaybackProfileManager
import org.schabi.newpipe.player.helper.PlayerDataSource
import org.schabi.newpipe.player.mediaitem.LocalMediaItemTag
import org.schabi.newpipe.player.mediaitem.MediaItemTag.AudioTrack
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.resolver.AudioPlaybackResolver
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver.SourceType
import org.schabi.newpipe.util.ListHelper

/** Owns remote/local source resolution and stream-selection changes. */
internal class PlayerStreamController(
    private val player: Player,
    private val context: Context,
    private val audioResolver: AudioPlaybackResolver,
    private val videoResolver: VideoPlaybackResolver,
    private val dataSource: PlayerDataSource
) {
    private var audioDecoderFallbackUrl: String? = null
    private val audioDecoderFallbackTracks = mutableSetOf<String?>()
    private val popupQualityTransition = PopupQualityTransition(player)

    fun enableAudioDecoderFallback(item: PlayQueueItem) {
        val info = player.currentStreamInfo.orElse(null) ?: return
        val audio = player.selectedAudioStream.orElse(null) ?: return
        if (item.serviceId == ServiceList.YouTube.serviceId &&
            info.serviceId == item.serviceId && info.url == item.url &&
            info.streamType == StreamType.VIDEO_STREAM && audio.format == MediaFormat.M4A
        ) {
            if (audioDecoderFallbackUrl != item.url) {
                audioDecoderFallbackTracks.clear()
                audioDecoderFallbackUrl = item.url
            }
            audioDecoderFallbackTracks.add(audio.audioTrackId)
        }
    }

    fun clearAudioDecoderFallback() {
        audioDecoderFallbackUrl = null
        audioDecoderFallbackTracks.clear()
    }

    private fun fallbackAudioTrack(info: StreamInfo): AudioTrack? {
        val url = audioDecoderFallbackUrl ?: return null
        if (info.url != url || info.serviceId != ServiceList.YouTube.serviceId ||
            info.streamType != StreamType.VIDEO_STREAM
        ) {
            return null
        }

        val streams = ListHelper.getFilteredAudioStreams(context, info.audioStreams)
        val index = ListHelper.getAudioFormatIndex(context, streams, audioResolver.audioTrack)
        val selected = streams.getOrNull(index) ?: return null
        val trackId = selected.audioTrackId
        if (trackId !in audioDecoderFallbackTracks || selected.format != MediaFormat.M4A) return null

        val alternatives = info.audioStreams.filter {
            it.audioTrackId == trackId && it.deliveryMethod != DeliveryMethod.HLS &&
                (
                    it.format == MediaFormat.WEBMA_OPUS || it.format == MediaFormat.OPUS ||
                        (it.format == MediaFormat.WEBMA && it.codec.equals("opus", ignoreCase = true))
                    )
        }
        val replacement = ListHelper.getFilteredAudioStreams(context, alternatives).firstOrNull()
            ?: return null
        val updated = streams.toMutableList()
        updated[index] = replacement
        return AudioTrack.of(updated, index)
    }

    fun sourceOf(info: StreamInfo): MediaSource? {
        if (info is org.schabi.newpipe.download.DownloadedStreamInfo) {
            return downloadedSource(info)
        }
        val audioTrack = fallbackAudioTrack(info)
        if (player.audioPlayerSelected()) {
            return audioResolver.resolve(info, audioTrack)
        }
        if (player.isAudioOnly &&
            videoResolver.streamSourceType.orElse(
                SourceType.VIDEO_WITH_AUDIO_OR_AUDIO_ONLY
            ) == SourceType.VIDEO_WITH_AUDIO_OR_AUDIO_ONLY
        ) {
            return audioResolver.resolve(info, audioTrack)
        }
        return if (ChannelPlaybackProfileManager.isAvailable(context, info)) {
            videoResolver.resolve(
                info,
                ChannelPlaybackProfileManager.getQuality(context, info),
                audioTrack
            )
        } else {
            videoResolver.resolve(info, videoResolver.playbackQuality, audioTrack)
        }
    }

    fun sourceOfLocal(item: PlayQueueItem): MediaSource? {
        if (item.isLiveTv) return org.schabi.newpipe.local.tv.LiveTvPlaybackSource.create(item)
        if (item.isRadio) {
            secureRadioUrl(item.url) ?: return null
            val http = DefaultHttpDataSource.Factory().setUserAgent(DownloaderImpl.USER_AGENT)
                .setDefaultRequestProperties(mapOf("Icy-MetaData" to "1"))
            val https = ResolvingDataSource.Factory(http) { request ->
                if (secureRadioUrl(request.uri.toString()) == null) {
                    throw IOException("Radio requires an HTTPS stream")
                }
                request
            }
            val mediaItem = LocalMediaItemTag.of(item).asMediaItem()
            return if (item.mimeType == MimeTypes.APPLICATION_M3U8) {
                HlsMediaSource.Factory(https).createMediaSource(mediaItem)
            } else {
                ProgressiveMediaSource.Factory(https).createMediaSource(mediaItem)
            }
        }
        if (!item.isLocalMedia) return null
        return dataSource.progressiveMediaSourceFactory
            .createMediaSource(LocalMediaItemTag.of(item).asMediaItem())
    }

    fun sourceOfDownloaded(item: PlayQueueItem): MediaSource? {
        val info = org.schabi.newpipe.download.DownloadedCopyRepository.find(
            context,
            item.serviceId,
            item.url,
            item.title,
            player.audioPlayerSelected() || player.isAudioOnly,
            item
        ) ?: return null
        return downloadedSource(info)
    }

    private fun downloadedSource(info: org.schabi.newpipe.download.DownloadedStreamInfo): MediaSource = dataSource.progressiveMediaSourceFactory
        .createMediaSource(org.schabi.newpipe.player.mediaitem.StreamInfoTag.of(info).asMediaItem().buildUpon().setUri(info.copyUri).build())

    fun disablePreloadingOfCurrentTrack() {
        player.getLoadControllerForLifecycle()?.disablePreloadingOfCurrentTrack()
    }

    fun setPlaybackQuality(quality: String?) {
        popupQualityTransition.beforeQualityReload()
        player.saveStreamProgressState()
        player.setRecovery()
        if (quality != null) {
            ChannelPlaybackProfileManager.saveQuality(
                context,
                player.currentStreamInfo.orElse(null),
                player.currentItem,
                quality
            )
        }
        videoResolver.setPlaybackQuality(quality)
        player.reloadPlayQueueManager()
    }

    fun onPlaybackStateChanged(state: Int) = popupQualityTransition.onPlaybackStateChanged(state)

    fun onPlayerReleasing() = popupQualityTransition.cancel()

    fun setAudioTrack(audioTrackId: String?) {
        player.saveStreamProgressState()
        player.setRecovery()
        videoResolver.setAudioTrack(audioTrackId)
        audioResolver.setAudioTrack(audioTrackId)
        player.reloadPlayQueueManager()
    }
}
