package org.schabi.newpipe.player.ui;

import android.view.View;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.PlayerBinding;
import org.schabi.newpipe.player.Player;

/** Keeps optional seek controls consistent with queue navigation and accessibility labels. */
final class PlayerNavigationButtons {
    private PlayerNavigationButtons() {
    }

    static boolean useSeekButtons(final Player player) {
        return player.getPrefs().getBoolean(
                player.getContext().getString(R.string.player_seek_buttons_key), false);
    }

    static void previous(final Player player) {
        if (useSeekButtons(player)) {
            player.fastRewind();
        } else {
            player.playPrevious();
        }
    }

    static void next(final Player player) {
        if (useSeekButtons(player)) {
            player.fastForward();
        } else {
            player.playNext();
        }
    }

    static void updateIcons(final Player player, final PlayerBinding binding, final boolean seek) {
        binding.playPreviousButton.setImageResource(seek
                ? R.drawable.ic_seek_back : R.drawable.ic_previous);
        binding.playNextButton.setImageResource(seek
                ? R.drawable.ic_seek_forward : R.drawable.ic_next);
        binding.playPreviousButton.setContentDescription(player.getContext().getString(seek
                ? R.string.player_seek_back : R.string.previous_stream));
        binding.playNextButton.setContentDescription(player.getContext().getString(seek
                ? R.string.player_seek_forward : R.string.next_stream));
    }
    static void updateVisibility(final Player player, final PlayerBinding binding) {
        PreviousPlaybackUi.update(binding, player);
        final var playQueue = player.getPlayQueue();
        if (playQueue == null) {
            return;
        }

        final boolean seek = PlayerNavigationButtons.useSeekButtons(player);
        PlayerNavigationButtons.updateIcons(player, binding, seek);
        final boolean showPrev = seek || playQueue.getIndex() != 0;
        final boolean showNext = seek || playQueue.getIndex() + 1 != playQueue.getStreams().size();

        binding.playPreviousButton.setVisibility(showPrev ? View.VISIBLE : View.INVISIBLE);
        binding.playPreviousButton.setAlpha(showPrev ? 1.0f : 0.0f);
        binding.playNextButton.setVisibility(showNext ? View.VISIBLE : View.INVISIBLE);
        binding.playNextButton.setAlpha(showNext ? 1.0f : 0.0f);
    }

}
