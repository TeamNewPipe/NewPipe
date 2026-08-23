package org.schabi.newpipe.player;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.player.playqueue.PlayQueue;

import java.io.Serializable;

public class PlayerState implements Serializable {

    @NonNull
    private final PlayQueue playQueue;
    private final int repeatMode;
    private final float playbackSpeed;
    private final float playbackPitch;
    @Nullable
    private final String playbackQuality;
    private final boolean playbackSkipSilence;
    private final boolean wasPlaying;

    PlayerState(@NonNull final PlayQueue playQueue, final int repeatMode,
                final float playbackSpeed, final float playbackPitch,
                final boolean playbackSkipSilence, final boolean wasPlaying) {
        this(playQueue, repeatMode, playbackSpeed, playbackPitch, null,
                playbackSkipSilence, wasPlaying);
    }

    PlayerState(@NonNull final PlayQueue playQueue, final int repeatMode,
                final float playbackSpeed, final float playbackPitch,
                @Nullable final String playbackQuality, final boolean playbackSkipSilence,
                final boolean wasPlaying) {
        this.playQueue = playQueue;
        this.repeatMode = repeatMode;
        this.playbackSpeed = playbackSpeed;
        this.playbackPitch = playbackPitch;
        this.playbackQuality = playbackQuality;
        this.playbackSkipSilence = playbackSkipSilence;
        this.wasPlaying = wasPlaying;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Serdes
    //////////////////////////////////////////////////////////////////////////*/

    /*//////////////////////////////////////////////////////////////////////////
    // Getters
    //////////////////////////////////////////////////////////////////////////*/

    @NonNull
    public PlayQueue getPlayQueue() {
        return playQueue;
    }

    public int getRepeatMode() {
        return repeatMode;
    }

    public float getPlaybackSpeed() {
        return playbackSpeed;
    }

    public float getPlaybackPitch() {
        return playbackPitch;
    }

    @Nullable
    public String getPlaybackQuality() {
        return playbackQuality;
    }

    public boolean isPlaybackSkipSilence() {
        return playbackSkipSilence;
    }

    public boolean wasPlaying() {
        return wasPlaying;
    }
}
