@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package org.schabi.newpipe.player.mediasource

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.source.BaseMediaSource
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.datasource.TransferListener
import okio.IOException
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.player.mediaitem.ExceptionTag
import org.schabi.newpipe.player.playqueue.PlayQueueItem

class FailedMediaSource(
    private val playQueueItem: PlayQueueItem,
    val error: Exception,
    private val retryTimestamp: Long
) : BaseMediaSource(), ManagedMediaSource {

    private val tag = "FailedMediaSource@${Integer.toHexString(hashCode())}"
    private val mediaItem: MediaItem = ExceptionTag.of(playQueueItem, listOf(error)).withExtras(this)
        .asMediaItem()
    private val silenceMediaSource = SilenceMediaSource.Factory()
        .setDurationUs(SILENCE_DURATION_US)
        .createMediaSource()

    val stream: PlayQueueItem
        get() = playQueueItem

    private fun canRetry(): Boolean = System.currentTimeMillis() >= retryTimestamp

    override fun getMediaItem(): MediaItem = mediaItem

    override fun prepareSourceInternal(mediaTransferListener: TransferListener?) {
        Log.e(tag, "Loading failed source: ", error)
        if (error is FailedMediaSourceException) {
            refreshSourceInfo(makeSilentMediaTimeline(SILENCE_DURATION_US, mediaItem))
        }
    }

    @Throws(IOException::class)
    override fun maybeThrowSourceInfoRefreshError() {
        if (error !is FailedMediaSourceException) {
            throw IOException(error)
        }
    }

    override fun createPeriod(id: MediaSource.MediaPeriodId, allocator: Allocator, startPositionUs: Long): MediaPeriod {
        return silenceMediaSource.createPeriod(id, allocator, startPositionUs)
    }

    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        silenceMediaSource.releasePeriod(mediaPeriod)
    }

    override fun releaseSourceInternal() {
        /* Do Nothing, no clean-up for processing/extra thread is needed by this MediaSource */
    }

    override fun shouldBeReplacedWith(newIdentity: PlayQueueItem, isInterruptable: Boolean): Boolean {
        return newIdentity != playQueueItem || canRetry()
    }

    override fun isStreamEqual(stream: PlayQueueItem): Boolean {
        return playQueueItem == stream
    }

    open class FailedMediaSourceException : Exception {
        constructor(message: String) : super(message)
        constructor(cause: Throwable) : super(cause)
    }

    class MediaSourceResolutionException(message: String) : FailedMediaSourceException(message)

    class StreamInfoLoadException(cause: Throwable) : FailedMediaSourceException(cause)

    companion object {
        val SILENCE_DURATION_US = TimeUnit.SECONDS.toMicros(2)

        @JvmStatic
        fun of(playQueueItem: PlayQueueItem, error: FailedMediaSourceException): FailedMediaSource {
            return FailedMediaSource(playQueueItem, error, Long.MAX_VALUE)
        }

        @JvmStatic
        fun of(playQueueItem: PlayQueueItem, error: Exception, retryWaitMillis: Long): FailedMediaSource {
            return FailedMediaSource(
                playQueueItem,
                error,
                System.currentTimeMillis() + retryWaitMillis
            )
        }

        private fun makeSilentMediaTimeline(durationUs: Long, mediaItem: MediaItem): Timeline {
            return SinglePeriodTimeline(
                durationUs,
                /* isSeekable= */
                true,
                /* isDynamic= */
                false,
                /* useLiveConfiguration= */
                false,
                /* manifest= */
                null,
                mediaItem
            )
        }
    }
}
