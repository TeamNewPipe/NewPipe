package org.schabi.newpipe.player.ui;

import androidx.annotation.NonNull;

import org.schabi.newpipe.player.Player;

/**
 * This is not a "graphical" UI for the background player, but it is used to disable fetching video
 * and text tracks with it.
 *
 * <p>
 * This allows reducing data usage for manifest sources with demuxed audio and video,
 * such as livestreams.
 * </p>
 */
public class BackgroundPlayerUi extends PlayerUi {

    public BackgroundPlayerUi(@NonNull final Player player) {
        super(player);
    }

    @Override
    public void initPlayback() {
        super.initPlayback();

        // Make sure to disable video and subtitles track types
        player.useVideoAndSubtitles(false);
    }
}
