/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.tv.player;

import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.app.VideoSupportFragment;
import androidx.leanback.app.VideoSupportFragmentGlueHost;

import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.PlayerService;
import org.schabi.newpipe.player.event.PlayerServiceExtendedEventListener;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.player.playqueue.PlayQueue;

public class TvPlaybackSupportFragment extends VideoSupportFragment
        implements SurfaceHolder.Callback, PlayerServiceExtendedEventListener {

    private NewPipePlayerAdapter playerAdapter;
    private TvPlayerGlue playerGlue;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        playerAdapter = new NewPipePlayerAdapter(requireContext());
        playerGlue = new TvPlayerGlue(requireContext(), playerAdapter);
        playerGlue.setHost(new VideoSupportFragmentGlueHost(this));
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getSurfaceView() != null) {
            getSurfaceView().getHolder().addCallback(this);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        PlayerHolder.getInstance().setListener(this);
        playerAdapter.notifyPlaybackStateChanged();
    }

    @Override
    public void onStop() {
        super.onStop();
        PlayerHolder.getInstance().setListener(null);
    }

    @Override
    public void surfaceCreated(@NonNull final SurfaceHolder holder) {
        PlayerHolder.getInstance().getPlayerInstance().ifPresent(player -> {
            if (player.getExoPlayer() != null) {
                player.getExoPlayer().setVideoSurfaceHolder(holder);
            }
        });
    }

    @Override
    public void surfaceChanged(@NonNull final SurfaceHolder holder, final int format,
                               final int width, final int height) {
        // No-op for TV playback fragment
    }

    @Override
    public void surfaceDestroyed(@NonNull final SurfaceHolder holder) {
        PlayerHolder.getInstance().getPlayerInstance().ifPresent(player -> {
            if (player.getExoPlayer() != null) {
                player.getExoPlayer().setVideoSurfaceHolder(null);
            }
        });
    }

    /* PlayerServiceExtendedEventListener callbacks */
    @Override
    public void onViewCreated() {
        // No-op: handled in player glue
    }

    @Override
    public void onFullscreenStateChanged(final boolean fullscreen) {
        // No-op: TV interface is always fullscreen landscape
    }

    @Override
    public void onScreenRotationButtonClicked() {
        // No-op: orientation is locked to landscape on TV
    }

    @Override
    public void onMoreOptionsLongClicked() {
        // No-op: not triggered on TV remote
    }

    @Override
    public void onPlayerError(final PlaybackException error, final boolean isCatchableException) {
        // No-op: error handled by player glue adapter
    }

    @Override
    public void hideSystemUiIfNeeded() {
        // No-op: system UI is managed by Theme.NewPipe.Tv.Player
    }

    @Override
    public void onQueueUpdate(final PlayQueue queue) {
        if (playerGlue != null) {
            playerAdapter.notifyPlaybackStateChanged();
        }
    }

    @Override
    public void onPlaybackUpdate(final int state, final int repeatMode,
                                 final boolean shuffled, final PlaybackParameters parameters) {
        if (playerGlue != null) {
            playerAdapter.notifyPlaybackStateChanged();
        }
    }

    @Override
    public void onProgressUpdate(final int currentProgress, final int duration,
                                 final int bufferPercent) {
        if (playerGlue != null) {
            playerAdapter.notifyPlaybackStateChanged();
        }
    }

    @Override
    public void onMetadataUpdate(final StreamInfo info, final PlayQueue queue) {
        if (playerGlue != null && info != null) {
            playerGlue.setTitle(info.getName());
            playerGlue.setSubtitle(info.getUploaderName());
            playerAdapter.notifyPlaybackStateChanged();
        }
    }

    @Override
    public void onServiceConnected(@NonNull final PlayerService playerService) {
        // No-op: state synchronized via PlayerHolder
    }

    @Override
    public void onPlayerConnected(@NonNull final Player player,
                                  final boolean playAfterConnect) {
        if (getSurfaceView() != null && player.getExoPlayer() != null) {
            player.getExoPlayer().setVideoSurfaceHolder(getSurfaceView().getHolder());
        }
    }

    @Override
    public void onPlayerDisconnected() {
        // No-op: handled on session cleanup
    }

    @Override
    public void onServiceDisconnected() {
        // No-op: unbind cleans up adapter
    }

    @Override
    public void onServiceStopped() {
        // No-op: activity handles teardown
    }
}
