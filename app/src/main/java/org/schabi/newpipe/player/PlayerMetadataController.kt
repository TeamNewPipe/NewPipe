/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player

import android.content.Context
import android.graphics.Bitmap
import androidx.media3.common.C
import androidx.media3.common.Player as Media3Player
import java.util.Optional
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.mediaitem.LocalMediaItemTag
import org.schabi.newpipe.player.mediaitem.MediaItemTag
import org.schabi.newpipe.recommendations.LocalRecommendations
import org.schabi.newpipe.recommendations.RecommendationWorkDispatcher
import org.schabi.newpipe.util.image.ExtractorImageCompat

/** Owns current media metadata and the work triggered when it changes. */
internal class PlayerMetadataController(
    private val player: Player,
    private val context: Context,
    private val historyController: PlayerHistoryController,
    private val playbackParametersController: PlaybackParametersController,
    private val sponsorBlockController: SponsorBlockPlaybackController,
    private val thumbnailController: PlayerThumbnailController,
    private val localMetadataController: PlayerLocalMetadataController
) {
    var currentMetadata: MediaItemTag? = null
        private set

    fun onEvents(media3Player: Media3Player) {
        MediaItemTag.from(media3Player.currentMediaItem).ifPresent { tag ->
            if (tag === currentMetadata) return@ifPresent
            val previousInfo = currentMetadata?.maybeStreamInfo?.orElse(null)
            val previousAudioTrack = currentMetadata?.maybeAudioTrack?.orElse(null)
            currentMetadata = tag

            if (tag.errors.isNotEmpty()) {
                ErrorUtil.createNotification(
                    context,
                    ErrorInfo(
                        tag.errors,
                        UserAction.PLAY_STREAM,
                        "Loading failed for [${tag.title}]: ${tag.streamUrl}",
                        tag.serviceId,
                        tag.streamUrl
                    )
                )
            }

            tag.maybeStreamInfo.ifPresent { info ->
                if (Player.DEBUG) {
                    android.util.Log.d(
                        Player.TAG,
                        "ExoPlayer - onEvents() update stream info: ${info.name}"
                    )
                }
                if (previousInfo == null || previousInfo.url != info.url) {
                    updateMetadataWith(info)
                } else if (previousAudioTrack == null ||
                    tag.maybeAudioTrack
                        .map {
                            it.selectedAudioStreamIndex !=
                                previousAudioTrack.selectedAudioStreamIndex
                        }
                        .orElse(false)
                ) {
                    player.notifyAudioTrackUpdateToListeners()
                }
            }
            if (tag is LocalMediaItemTag) {
                if (tag.item.isRadio || tag.item.isLiveTv) {
                    if (tag.item.isLiveTv) player.liveQualityController.onItemLoaded()
                    localMetadataController.cancel()
                    sponsorBlockController.updateSegments(emptyList())
                    thumbnailController.load(emptyList())
                    player.notifyMetadataUpdateToListeners()
                    player.UIs().call { ui -> ui.onMetadataChanged(tag) }
                } else {
                    updateMetadataForLocalMedia(tag.item)
                }
            }
        }
    }

    fun clear() {
        currentMetadata = null
    }

    fun currentStreamInfo(): Optional<StreamInfo> = Optional.ofNullable(currentMetadata)
        .flatMap(MediaItemTag::getMaybeStreamInfo)

    fun updateMetadataWith(info: StreamInfo) {
        if (Player.DEBUG) {
            android.util.Log.d(
                Player.TAG,
                "Playback - onMetadataChanged() called, playing: ${info.name}"
            )
        }
        if (player.exoPlayerIsNull()) return

        player.liveQualityController.onItemLoaded()
        localMetadataController.cancel()
        playbackParametersController.applySpeedProfile(info)
        sponsorBlockController.updateSegments(info)
        maybeAutoQueueNextStream(info)
        thumbnailController.load(ExtractorImageCompat.thumbnailImages(info))
        // The recommendation model is serialized to preferences; do not perform that work
        // on the playback event thread when a new video's metadata arrives.
        RecommendationWorkDispatcher.observe(context, info.relatedItems.orEmpty())
        historyController.registerViewed()

        player.notifyMetadataUpdateToListeners()
        player.notifyAudioTrackUpdateToListeners()
        player.UIs().call { ui -> ui.onMetadataChanged(info) }
    }

    fun updateMetadataForLocalMedia(
        item: org.schabi.newpipe.player.playqueue.PlayQueueItem
    ) {
        player.liveQualityController.onItemLoaded()
        sponsorBlockController.updateSegments(
            org.schabi.newpipe.download.OfflineSponsorBlockStore.load(context, item.url)
        )
        thumbnailController.loadLocal(item)
        localMetadataController.load(item)
        historyController.registerViewed(item)
        player.notifyMetadataUpdateToListeners()
        player.notifyAudioTrackUpdateToListeners()
        val metadata = checkNotNull(currentMetadata)
        player.UIs().call { ui -> ui.onMetadataChanged(metadata) }
    }

    fun videoUrl(): String = currentMetadata?.streamUrl
        ?: context.getString(R.string.unknown_content)

    fun videoUrlAtCurrentTime(): String {
        val timeSeconds = player.exoPlayer.currentPosition / 1000
        val url = videoUrl()
        return if (!player.isLive && timeSeconds >= 0 &&
            currentMetadata?.serviceId == YouTube.serviceId
        ) {
            "$url&t=$timeSeconds"
        } else {
            url
        }
    }

    fun videoTitle(): String = currentMetadata?.title
        ?: context.getString(R.string.unknown_content)

    fun uploaderName(): String = currentMetadata?.uploaderName
        ?: context.getString(R.string.unknown_content)

    fun thumbnail(): Bitmap? = thumbnailController.getCurrentThumbnail()

    fun selectedVideoStream(): Optional<VideoStream> {
        val quality = currentMetadata?.maybeQuality?.orElse(null) ?: return Optional.empty()
        if (!quality.isAdaptive) {
            return Optional.ofNullable(
                quality.sortedVideoStreams.getOrNull(quality.selectedVideoStreamIndex)
            )
        }

        val selectedFormatId = player.getExoPlayer()?.currentTracks?.groups
            ?.asSequence()
            ?.filter { group -> group.type == C.TRACK_TYPE_VIDEO && group.isSelected }
            ?.flatMap { group ->
                (0 until group.length).asSequence()
                    .filter(group::isTrackSelected)
                    .map { index -> group.mediaTrackGroup.getFormat(index).id }
            }
            ?.firstOrNull { id -> !id.isNullOrBlank() }

        return Optional.ofNullable(
            quality.sortedVideoStreams.firstOrNull { stream ->
                stream.itag.toString() == selectedFormatId
            }
        )
    }

    fun selectedAudioStream(): Optional<AudioStream> = Optional.ofNullable(currentMetadata)
        .flatMap(MediaItemTag::getMaybeAudioTrack)
        .map(MediaItemTag.AudioTrack::getSelectedAudioStream)

    private fun maybeAutoQueueNextStream(info: StreamInfo) {
        val playQueue = player.playQueue ?: return
        if (playQueue.index != playQueue.size() - 1 ||
            player.repeatMode != Media3Player.REPEAT_MODE_OFF ||
            !PlayerHelper.isAutoQueueEnabled(context)
        ) {
            return
        }
        val currentQueueItem = playQueue.item
        val preferShortFormContent =
            info.isShortFormContent || currentQueueItem?.isShortFormContent == true
        if (LocalRecommendations.enabled(context) &&
            LocalRecommendations.setting(context, "autoplay")
        ) {
            val candidate = LocalRecommendations.autoplay(
                context,
                info.relatedItems.orEmpty(),
                info.serviceId,
                preferShortFormContent,
                playQueue.streams.map { it.url }.toSet()
            ) ?: return
            val queue = org.schabi.newpipe.player.playqueue.SinglePlayQueue(candidate)
            queue.item?.isAutoQueued = true
            playQueue.append(queue.streams)
            return
        }
        PlayerHelper.autoQueueOf(info, playQueue.streams, preferShortFormContent)
            ?.let { playQueue.append(it.streams) }
    }
}
