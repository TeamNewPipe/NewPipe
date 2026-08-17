@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.player.mediasource

import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.source.CompositeMediaSource
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.media3.exoplayer.upstream.Allocator
import org.schabi.newpipe.player.mediaitem.PlaceholderTag
import org.schabi.newpipe.player.playqueue.PlayQueueItem

internal class PlaceholderMediaSource private constructor() :
    CompositeMediaSource<Void?>(), ManagedMediaSource {

    private val silenceMediaSource = SilenceMediaSource.Factory()
        .setDurationUs(1)
        .createMediaSource()

    override fun getMediaItem(): MediaItem = MEDIA_ITEM

    override fun onChildSourceInfoRefreshed(
        id: Void?,
        mediaSource: MediaSource,
        timeline: Timeline
    ) {
        /* Do nothing, no timeline updates or error will stall playback */
    }

    override fun createPeriod(
        id: MediaSource.MediaPeriodId,
        allocator: Allocator,
        startPositionUs: Long
    ): MediaPeriod {
        return silenceMediaSource.createPeriod(id, allocator, startPositionUs)
    }

    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        silenceMediaSource.releasePeriod(mediaPeriod)
    }

    override fun shouldBeReplacedWith(newIdentity: PlayQueueItem, isInterruptable: Boolean): Boolean = true

    override fun isStreamEqual(stream: PlayQueueItem): Boolean = false

    companion object {
        val COPY = PlaceholderMediaSource()
        private val MEDIA_ITEM: MediaItem = PlaceholderTag.EMPTY.withExtras(COPY).asMediaItem()
    }
}
