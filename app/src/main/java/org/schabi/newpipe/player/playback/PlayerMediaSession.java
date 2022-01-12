package org.schabi.newpipe.player.playback;

import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;

import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.mediasession.MediaSessionCallback;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;

public class PlayerMediaSession implements MediaSessionCallback {
    private final Player player;

    public PlayerMediaSession(final Player player) {
        this.player = player;
    }

    @Override
    public void playPrevious() {
        player.playPrevious();
    }

    @Override
    public void playNext() {
        player.playNext();
    }

    @Override
    public void playItemAtIndex(final int index) {
        if (player.getPlayQueue() == null) {
            return;
        }
        player.selectQueueItem(player.getPlayQueue().getItem(index));
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
        if (player.getPlayQueue() == null) {
            return null;
        }
        final PlayQueueItem item = player.getPlayQueue().getItem(index);
        if (item == null) {
            return null;
        }

        final MediaDescriptionCompat.Builder descriptionBuilder
                = new MediaDescriptionCompat.Builder()
                .setMediaId(String.valueOf(index))
                .setTitle(item.getTitle())
                .setSubtitle(item.getUploader());

        // set additional metadata for A2DP/AVRCP
        final Bundle additionalMetadata = new Bundle();
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
    public void play() {
        player.play();
    }

    @Override
    public void pause() {
        player.pause();
    }
}
