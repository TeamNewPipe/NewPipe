package org.schabi.newpipe.player.mediasession;

import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM;

import android.net.Uri;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.util.Util;

import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.util.image.ImageStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class PlayQueueNavigator implements MediaSessionConnector.QueueNavigator {
    private static final int MAX_QUEUE_SIZE = 10;

    private final MediaSessionCompat mediaSession;
    private final Player player;

    private long activeQueueItemId;

    public PlayQueueNavigator(@NonNull final MediaSessionCompat mediaSession,
                              @NonNull final Player player) {
        this.mediaSession = mediaSession;
        this.player = player;

        this.activeQueueItemId = MediaSessionCompat.QueueItem.UNKNOWN_ID;
    }

    @Override
    public long getSupportedQueueNavigatorActions(
            @Nullable final com.google.android.exoplayer2.Player exoPlayer) {
        return ACTION_SKIP_TO_NEXT | ACTION_SKIP_TO_PREVIOUS | ACTION_SKIP_TO_QUEUE_ITEM;
    }

    @Override
    public void onTimelineChanged(@NonNull final com.google.android.exoplayer2.Player exoPlayer) {
        publishFloatingQueueWindow();
    }

    @Override
    public void onCurrentMediaItemIndexChanged(
            @NonNull final com.google.android.exoplayer2.Player exoPlayer) {
        if (activeQueueItemId == MediaSessionCompat.QueueItem.UNKNOWN_ID
                || exoPlayer.getCurrentTimeline().getWindowCount() > MAX_QUEUE_SIZE) {
            publishFloatingQueueWindow();
        } else if (!exoPlayer.getCurrentTimeline().isEmpty()) {
            activeQueueItemId = exoPlayer.getCurrentMediaItemIndex();
        }
    }

    @Override
    public long getActiveQueueItemId(
            @Nullable final com.google.android.exoplayer2.Player exoPlayer) {
        return Optional.ofNullable(player.getPlayQueue()).map(PlayQueue::getIndex).orElse(-1);
    }

    @Override
    public void onSkipToPrevious(@NonNull final com.google.android.exoplayer2.Player exoPlayer) {
        player.playPrevious();
    }

    @Override
    public void onSkipToQueueItem(@NonNull final com.google.android.exoplayer2.Player exoPlayer,
                                  final long id) {
        if (player.getPlayQueue() != null) {
            player.selectQueueItem(player.getPlayQueue().getItem((int) id));
        }
    }

    @Override
    public void onSkipToNext(@NonNull final com.google.android.exoplayer2.Player exoPlayer) {
        player.playNext();
    }

    private void publishFloatingQueueWindow() {
        final int windowCount = Optional.ofNullable(player.getPlayQueue())
                .map(PlayQueue::size)
                .orElse(0);
        if (windowCount == 0) {
            mediaSession.setQueue(Collections.emptyList());
            activeQueueItemId = MediaSessionCompat.QueueItem.UNKNOWN_ID;
            return;
        }

        // Yes this is almost a copypasta, got a problem with that? =\
        final int currentWindowIndex = player.getPlayQueue().getIndex();
        final int queueSize = Math.min(MAX_QUEUE_SIZE, windowCount);
        final int startIndex = Util.constrainValue(currentWindowIndex - ((queueSize - 1) / 2), 0,
                windowCount - queueSize);

        final List<MediaSessionCompat.QueueItem> queue = new ArrayList<>();
        for (int i = startIndex; i < startIndex + queueSize; i++) {
            queue.add(new MediaSessionCompat.QueueItem(getQueueMetadata(i), i));
        }
        mediaSession.setQueue(queue);
        activeQueueItemId = currentWindowIndex;
    }

    public MediaDescriptionCompat getQueueMetadata(final int index) {
        if (player.getPlayQueue() == null) {
            return null;
        }
        final PlayQueueItem item = player.getPlayQueue().getItem(index);
        if (item == null) {
            return null;
        }

        final MediaDescriptionCompat.Builder descBuilder = new MediaDescriptionCompat.Builder()
                .setMediaId(String.valueOf(index))
                .setTitle(item.getTitle())
                .setSubtitle(item.getUploader());

        // set additional metadata for A2DP/AVRCP (Audio/Video Bluetooth profiles)
        final Bundle additionalMetadata = new Bundle();
        additionalMetadata.putString(MediaMetadataCompat.METADATA_KEY_TITLE, item.getTitle());
        additionalMetadata.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, item.getUploader());
        additionalMetadata
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, item.getDuration() * 1000);
        additionalMetadata.putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, index + 1L);
        additionalMetadata
                .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, player.getPlayQueue().size());
        descBuilder.setExtras(additionalMetadata);

        try {
            descBuilder.setIconUri(Uri.parse(
                    ImageStrategy.choosePreferredImage(item.getThumbnails())));
        } catch (final Throwable e) {
            // no thumbnail available at all, or the user disabled image loading,
            // or the obtained url is not a valid `Uri`
        }

        return descBuilder.build();
    }

    @Override
    public boolean onCommand(@NonNull final com.google.android.exoplayer2.Player exoPlayer,
                             @NonNull final String command,
                             @Nullable final Bundle extras,
                             @Nullable final ResultReceiver cb) {
        return false;
    }
}
