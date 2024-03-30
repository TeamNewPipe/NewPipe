package org.schabi.newpipe.player.mediasource

import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.source.CompositeMediaSource
import com.google.android.exoplayer2.source.MediaPeriod
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.upstream.Allocator
import org.schabi.newpipe.player.mediaitem.PlaceholderTag
import org.schabi.newpipe.player.playqueue.PlayQueueItem

internal class PlaceholderMediaSource private constructor() : CompositeMediaSource<Void?>(), ManagedMediaSource {
    public override fun getMediaItem(): MediaItem {
        return MEDIA_ITEM
    }

    override fun onChildSourceInfoRefreshed(id: Void?,
                                            mediaSource: MediaSource,
                                            timeline: Timeline) {
        /* Do nothing, no timeline updates or error will stall playback */
    }

    public override fun createPeriod(id: MediaSource.MediaPeriodId, allocator: Allocator,
                                     startPositionUs: Long): MediaPeriod {
        return null
    }

    public override fun releasePeriod(mediaPeriod: MediaPeriod) {}
    public override fun shouldBeReplacedWith(newIdentity: PlayQueueItem,
                                             isInterruptable: Boolean): Boolean {
        return true
    }

    public override fun isStreamEqual(stream: PlayQueueItem): Boolean {
        return false
    }

    companion object {
        val COPY: PlaceholderMediaSource = PlaceholderMediaSource()
        private val MEDIA_ITEM: MediaItem = PlaceholderTag.Companion.EMPTY.withExtras<PlaceholderMediaSource>(COPY).asMediaItem()
    }
}
