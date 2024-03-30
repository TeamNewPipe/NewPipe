package org.schabi.newpipe.player.mediasession

import android.net.Uri
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector.QueueNavigator
import com.google.android.exoplayer2.util.Util
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.util.image.ImageStrategy
import java.util.Optional
import java.util.function.Function
import kotlin.math.min

class PlayQueueNavigator(private val mediaSession: MediaSessionCompat,
                         private val player: Player) : QueueNavigator {
    private var activeQueueItemId: Long

    init {
        activeQueueItemId = MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong()
    }

    public override fun getSupportedQueueNavigatorActions(
            exoPlayer: com.google.android.exoplayer2.Player?): Long {
        return PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM
    }

    public override fun onTimelineChanged(exoPlayer: com.google.android.exoplayer2.Player) {
        publishFloatingQueueWindow()
    }

    public override fun onCurrentMediaItemIndexChanged(
            exoPlayer: com.google.android.exoplayer2.Player) {
        if ((activeQueueItemId == MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong()
                        || exoPlayer.getCurrentTimeline().getWindowCount() > MAX_QUEUE_SIZE)) {
            publishFloatingQueueWindow()
        } else if (!exoPlayer.getCurrentTimeline().isEmpty()) {
            activeQueueItemId = exoPlayer.getCurrentMediaItemIndex().toLong()
        }
    }

    public override fun getActiveQueueItemId(
            exoPlayer: com.google.android.exoplayer2.Player?): Long {
        return Optional.ofNullable(player.getPlayQueue()).map(Function({ obj: PlayQueue -> obj.getIndex() })).orElse(-1)?.toLong()
    }

    public override fun onSkipToPrevious(exoPlayer: com.google.android.exoplayer2.Player) {
        player.playPrevious()
    }

    public override fun onSkipToQueueItem(exoPlayer: com.google.android.exoplayer2.Player,
                                          id: Long) {
        if (player.getPlayQueue() != null) {
            player.selectQueueItem(player.getPlayQueue()!!.getItem(id.toInt()))
        }
    }

    public override fun onSkipToNext(exoPlayer: com.google.android.exoplayer2.Player) {
        player.playNext()
    }

    private fun publishFloatingQueueWindow() {
        val windowCount: Int = Optional.ofNullable(player.getPlayQueue())
                .map(Function({ obj: PlayQueue -> obj.size() }))
                .orElse(0)
        if (windowCount == 0) {
            mediaSession.setQueue(emptyList())
            activeQueueItemId = MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong()
            return
        }

        // Yes this is almost a copypasta, got a problem with that? =\
        val currentWindowIndex: Int = player.getPlayQueue().getIndex()
        val queueSize: Int = min(MAX_QUEUE_SIZE.toDouble(), windowCount.toDouble()).toInt()
        val startIndex: Int = Util.constrainValue(currentWindowIndex - ((queueSize - 1) / 2), 0,
                windowCount - queueSize)
        val queue: MutableList<MediaSessionCompat.QueueItem> = ArrayList()
        for (i in startIndex until (startIndex + queueSize)) {
            queue.add(MediaSessionCompat.QueueItem(getQueueMetadata(i), i.toLong()))
        }
        mediaSession.setQueue(queue)
        activeQueueItemId = currentWindowIndex.toLong()
    }

    fun getQueueMetadata(index: Int): MediaDescriptionCompat? {
        if (player.getPlayQueue() == null) {
            return null
        }
        val item: PlayQueueItem? = player.getPlayQueue()!!.getItem(index)
        if (item == null) {
            return null
        }
        val descBuilder: MediaDescriptionCompat.Builder = MediaDescriptionCompat.Builder()
                .setMediaId(index.toString())
                .setTitle(item.getTitle())
                .setSubtitle(item.getUploader())

        // set additional metadata for A2DP/AVRCP (Audio/Video Bluetooth profiles)
        val additionalMetadata: Bundle = Bundle()
        additionalMetadata.putString(MediaMetadataCompat.METADATA_KEY_TITLE, item.getTitle())
        additionalMetadata.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, item.getUploader())
        additionalMetadata
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, item.getDuration() * 1000)
        additionalMetadata.putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, index + 1L)
        additionalMetadata
                .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, player.getPlayQueue()!!.size().toLong())
        descBuilder.setExtras(additionalMetadata)
        try {
            descBuilder.setIconUri(Uri.parse(
                    ImageStrategy.choosePreferredImage(item.getThumbnails())))
        } catch (e: Throwable) {
            // no thumbnail available at all, or the user disabled image loading,
            // or the obtained url is not a valid `Uri`
        }
        return descBuilder.build()
    }

    public override fun onCommand(exoPlayer: com.google.android.exoplayer2.Player,
                                  command: String,
                                  extras: Bundle?,
                                  cb: ResultReceiver?): Boolean {
        return false
    }

    companion object {
        private val MAX_QUEUE_SIZE: Int = 10
    }
}
