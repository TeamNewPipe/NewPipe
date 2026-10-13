/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.tv.player;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.leanback.media.PlaybackTransportControlGlue;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.PlaybackControlsRow;

import org.schabi.newpipe.player.helper.PlayerHolder;

public class TvPlayerGlue extends PlaybackTransportControlGlue<NewPipePlayerAdapter> {

    private PlaybackControlsRow.PlayPauseAction playPauseAction;
    private PlaybackControlsRow.SkipPreviousAction skipPreviousAction;
    private PlaybackControlsRow.SkipNextAction skipNextAction;
    private PlaybackControlsRow.FastForwardAction fastForwardAction;
    private PlaybackControlsRow.RewindAction rewindAction;

    private PlaybackControlsRow.RepeatAction repeatAction;
    private PlaybackControlsRow.ShuffleAction shuffleAction;

    public TvPlayerGlue(@NonNull final Context context,
                        @NonNull final NewPipePlayerAdapter adapter) {
        super(context, adapter);
    }

    @Override
    protected void onCreatePrimaryActions(final ArrayObjectAdapter primaryActionsAdapter) {
        super.onCreatePrimaryActions(primaryActionsAdapter);

        skipPreviousAction = new PlaybackControlsRow.SkipPreviousAction(getContext());
        rewindAction = new PlaybackControlsRow.RewindAction(getContext());
        playPauseAction = new PlaybackControlsRow.PlayPauseAction(getContext());
        fastForwardAction = new PlaybackControlsRow.FastForwardAction(getContext());
        skipNextAction = new PlaybackControlsRow.SkipNextAction(getContext());

        primaryActionsAdapter.add(skipPreviousAction);
        primaryActionsAdapter.add(rewindAction);
        primaryActionsAdapter.add(playPauseAction);
        primaryActionsAdapter.add(fastForwardAction);
        primaryActionsAdapter.add(skipNextAction);
    }

    @Override
    protected void onCreateSecondaryActions(final ArrayObjectAdapter secondaryActionsAdapter) {
        super.onCreateSecondaryActions(secondaryActionsAdapter);

        repeatAction = new PlaybackControlsRow.RepeatAction(getContext());
        shuffleAction = new PlaybackControlsRow.ShuffleAction(getContext());

        secondaryActionsAdapter.add(repeatAction);
        secondaryActionsAdapter.add(shuffleAction);
    }

    @Override
    public void onActionClicked(final Action action) {
        if (action == playPauseAction) {
            if (getPlayerAdapter().isPlaying()) {
                getPlayerAdapter().pause();
            } else {
                getPlayerAdapter().play();
            }
        } else if (action == skipNextAction) {
            PlayerHolder.getInstance().playNext();
        } else if (action == skipPreviousAction) {
            PlayerHolder.getInstance().playPrevious();
        } else if (action == fastForwardAction) {
            getPlayerAdapter().seekTo(getPlayerAdapter().getCurrentPosition() + 10000);
        } else if (action == rewindAction) {
            getPlayerAdapter().seekTo(Math.max(0, getPlayerAdapter().getCurrentPosition() - 10000));
        } else {
            super.onActionClicked(action);
        }
    }
}
