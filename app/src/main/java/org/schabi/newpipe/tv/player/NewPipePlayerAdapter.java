/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.tv.player;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.leanback.media.PlayerAdapter;

import org.schabi.newpipe.player.helper.PlayerHolder;

public class NewPipePlayerAdapter extends PlayerAdapter {

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private boolean isAttached = false;

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAttached && isPlaying()) {
                getCallback().onCurrentPositionChanged(NewPipePlayerAdapter.this);
                getCallback().onBufferedPositionChanged(NewPipePlayerAdapter.this);
            }
            if (isAttached) {
                progressHandler.postDelayed(this, 1000);
            }
        }
    };

    public NewPipePlayerAdapter(@NonNull final Context context) {
    }

    @Override
    public boolean isPlaying() {
        return PlayerHolder.getInstance().isPlaying();
    }

    @Override
    public boolean isPrepared() {
        return PlayerHolder.getInstance().isPlayerOpen();
    }

    @Override
    public void play() {
        PlayerHolder.getInstance().play();
        getCallback().onPlayStateChanged(this);
    }

    @Override
    public void pause() {
        PlayerHolder.getInstance().pause();
        getCallback().onPlayStateChanged(this);
    }

    @Override
    public void seekTo(final long positionInMs) {
        PlayerHolder.getInstance().seekTo(positionInMs);
        getCallback().onCurrentPositionChanged(this);
    }

    @Override
    public long getCurrentPosition() {
        return PlayerHolder.getInstance().getCurrentPosition();
    }

    @Override
    public long getDuration() {
        return PlayerHolder.getInstance().getDuration();
    }

    @Override
    public long getBufferedPosition() {
        return PlayerHolder.getInstance().getBufferedPosition();
    }

    @Override
    public void onAttachedToHost(final androidx.leanback.media.PlaybackGlueHost host) {
        super.onAttachedToHost(host);
        isAttached = true;
        progressHandler.post(progressRunnable);
    }

    @Override
    public void onDetachedFromHost() {
        super.onDetachedFromHost();
        isAttached = false;
        progressHandler.removeCallbacks(progressRunnable);
    }

    public void notifyPlaybackStateChanged() {
        getCallback().onPlayStateChanged(this);
        getCallback().onCurrentPositionChanged(this);
        getCallback().onDurationChanged(this);
    }
}
