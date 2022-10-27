package org.schabi.newpipe.player.helper;

import static com.google.android.exoplayer2.Player.REPEAT_MODE_OFF;
import static org.schabi.newpipe.player.helper.PlayerHelper.retrievePlaybackParametersFromPrefs;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player.RepeatMode;

import org.schabi.newpipe.R;
import org.schabi.newpipe.player.Player;

public final class PlayerState {
    @RepeatMode final int repeatMode;
    final PlaybackParameters playbackParameters;
    final boolean playbackSkipSilence;
    final boolean playWhenReady;
    final boolean isMuted;

    private PlayerState(final int repeatMode,
                        final PlaybackParameters playbackParameters,
                        final boolean playbackSkipSilence,
                        final boolean playWhenReady,
                        final boolean isMuted) {
        this.repeatMode = repeatMode;
        this.playbackParameters = playbackParameters;
        this.playbackSkipSilence = playbackSkipSilence;
        this.playWhenReady = playWhenReady;
        this.isMuted = isMuted;
    }

    public static PlayerState from(final Player player) {
        if (player.exoPlayerIsNull()) {
            return fromPreferences(player.getContext(), player.getPrefs());
        } else {
            return fromExoPlayer(player.getExoPlayer());
        }
    }

    private static PlayerState fromPreferences(final Context ctx, final SharedPreferences prefs) {
        return new PlayerState(
                REPEAT_MODE_OFF,
                retrievePlaybackParametersFromPrefs(ctx),
                prefs.getBoolean(ctx.getString(R.string.playback_skip_silence_key), false),
                true,
                false
        );
    }

    private static PlayerState fromExoPlayer(final ExoPlayer exoPlayer) {
        return new PlayerState(
                exoPlayer.getRepeatMode(),
                exoPlayer.getPlaybackParameters(),
                exoPlayer.getSkipSilenceEnabled(),
                exoPlayer.getPlayWhenReady(),
                exoPlayer.getVolume() == 0
        );
    }


    public void restore(final Player player) {
        final ExoPlayer exoPlayer = player.getExoPlayer();
        if (exoPlayer != null) {
            exoPlayer.setRepeatMode(repeatMode);
            exoPlayer.setPlaybackParameters(playbackParameters);
            exoPlayer.setSkipSilenceEnabled(playbackSkipSilence);
            exoPlayer.setPlayWhenReady(playWhenReady);
            exoPlayer.setVolume(isMuted ? 0 : 1);
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "PlayerState{repeatMode=" + repeatMode + ", playbackParameters=" + playbackParameters
                + ", playbackSkipSilence=" + playbackSkipSilence + ", playWhenReady="
                + playWhenReady + ", isMuted=" + isMuted + "}";
    }
}
