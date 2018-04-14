package org.schabi.newpipe.player;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import org.schabi.newpipe.playlist.PlayQueue;

import java.io.Serializable;

public class PlayerState implements Serializable {
    private final static String TAG = "PlayerState";

    @NonNull private final PlayQueue playQueue;
    private final int repeatMode;
    private final float playbackSpeed;
    private final float playbackPitch;
    @Nullable private final String playbackQuality;
    private final boolean wasPlaying;

    PlayerState(@NonNull final PlayQueue playQueue, final int repeatMode,
                 final float playbackSpeed, final float playbackPitch, final boolean wasPlaying) {
        this(playQueue, repeatMode, playbackSpeed, playbackPitch, null, wasPlaying);
    }

    PlayerState(@NonNull final PlayQueue playQueue, final int repeatMode,
                final float playbackSpeed, final float playbackPitch,
                @Nullable final String playbackQuality, final boolean wasPlaying) {
        this.playQueue = playQueue;
        this.repeatMode = repeatMode;
        this.playbackSpeed = playbackSpeed;
        this.playbackPitch = playbackPitch;
        this.playbackQuality = playbackQuality;
        this.wasPlaying = wasPlaying;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Serdes
    //////////////////////////////////////////////////////////////////////////*/

    @Nullable
    public static PlayerState fromJson(@NonNull final String json) {
        try {
            return new Gson().fromJson(json, PlayerState.class);
        } catch (JsonSyntaxException error) {
            Log.e(TAG, "Failed to deserialize PlayerState from json=[" + json + "]", error);
            return null;
        }
    }

    @NonNull
    public String toJson() {
        return new Gson().toJson(this);
    }

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

    public boolean wasPlaying() {
        return wasPlaying;
    }
}
