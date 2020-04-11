package org.schabi.newpipe.player.playback;

import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;

import org.schabi.newpipe.player.BasePlayer;
import org.schabi.newpipe.player.mediasession.MediaSessionCallback;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;

public class BasePlayerMediaSession implements MediaSessionCallback {
    private final BasePlayer player;

    public BasePlayerMediaSession(final BasePlayer player) {
        this.player = player;
    }

    @Override
    public void onSkipToPrevious() {
        player.onPlayPrevious();
    }

    @Override
    public void onSkipToNext() {
        player.onPlayNext();
    }

    @Override
    public void onSkipToIndex(final int index) {
        if (player.getPlayQueue() == null) {
            return;
        }
        player.onSelected(player.getPlayQueue().getItem(index));
    }

    @Override
    public int getCurrentPlayingIndex() {
        if (player.getPlayQueue() == null) {
            return -1;
        }
        return player.getPlayQueue().getIndex();
    }

    @Override
    public int getQueueSize() {
        if (player.getPlayQueue() == null) {
            return -1;
        }
        return player.getPlayQueue().size();
    }

    @Override
    public MediaDescriptionCompat getQueueMetadata(final int index) {
        if (player.getPlayQueue() == null || player.getPlayQueue().getItem(index) == null) {
            return null;
        }

        final PlayQueueItem item = player.getPlayQueue().getItem(index);
        MediaDescriptionCompat.Builder descriptionBuilder = new MediaDescriptionCompat.Builder()
                .setMediaId(String.valueOf(index))
                .setTitle(item.getTitle())
                .setSubtitle(item.getUploader());

        // set additional metadata for A2DP/AVRCP
        Bundle additionalMetadata = new Bundle();
        additionalMetadata.putString(MediaMetadataCompat.METADATA_KEY_TITLE, item.getTitle());
        additionalMetadata.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, item.getUploader());
        additionalMetadata
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, item.getDuration() * 1000);
        additionalMetadata.putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, index + 1);
        additionalMetadata
                .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, player.getPlayQueue().size());
        descriptionBuilder.setExtras(additionalMetadata);

        final Uri thumbnailUri = Uri.parse(item.getThumbnailUrl());
        if (thumbnailUri != null) {
            descriptionBuilder.setIconUri(thumbnailUri);
        }

        return descriptionBuilder.build();
    }

    @Override
    public void onPlay() {
        player.onPlay();
    }

    @Override
    public void onPause() {
        player.onPause();
    }
}
