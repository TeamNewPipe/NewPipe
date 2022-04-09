package org.schabi.newpipe.player.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player.RepeatMode;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.video.VideoSize;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.Player;

import java.util.List;

public abstract class PlayerUi {

    @NonNull protected Context context;
    @NonNull protected Player player;

    public PlayerUi(@NonNull final Player player) {
        this.context = player.getContext();
        this.player = player;
    }

    @NonNull
    public Player getPlayer() {
        return player;
    }


    public void setupAfterIntent() {
    }

    public void initPlayer() {
    }

    public void initPlayback() {
    }

    public void destroyPlayer() {
    }

    public void destroy() {
    }

    public void smoothStopForImmediateReusing() {
    }

    public void onFragmentListenerSet() {
    }

    public void onBroadcastReceived(final Intent intent) {
    }

    public void onUpdateProgress(final int currentProgress,
                                 final int duration,
                                 final int bufferPercent) {
    }

    public void onPrepared() {
    }

    public void onBlocked() {
    }

    public void onPlaying() {
    }

    public void onBuffering() {
    }

    public void onPaused() {
    }

    public void onPausedSeek() {
    }

    public void onCompleted() {
    }

    public void onRepeatModeChanged(@RepeatMode final int repeatMode) {
    }

    public void onShuffleModeEnabledChanged(final boolean shuffleModeEnabled) {
    }

    public void onMuteUnmuteChanged(final boolean isMuted) {
    }

    public void onTextTracksChanged(@NonNull final Tracks currentTracks) {
    }

    public void onPlaybackParametersChanged(@NonNull final PlaybackParameters playbackParameters) {
    }

    public void onRenderedFirstFrame() {
    }

    public void onCues(@NonNull final List<Cue> cues) {
    }

    public void onMetadataChanged(@NonNull final StreamInfo info) {
    }

    public void onThumbnailLoaded(@Nullable final Bitmap bitmap) {
    }

    public void onPlayQueueEdited() {
    }

    public void onVideoSizeChanged(@NonNull final VideoSize videoSize) {
    }
}
