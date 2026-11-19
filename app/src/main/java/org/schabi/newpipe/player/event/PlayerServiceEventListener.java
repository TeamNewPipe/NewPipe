package org.schabi.newpipe.player.event;

import androidx.annotation.NonNull;
import androidx.media3.common.PlaybackException;

import org.schabi.newpipe.player.PlaybackPresentationMode;

public interface PlayerServiceEventListener extends PlayerEventListener {
    void onViewCreated();

    void onFullscreenStateChanged(boolean fullscreen);

    /**
     * Called when the player switches between video and audio-only presentations.
     *
     * @param mode the new presentation mode
     */
    default void onPlaybackPresentationModeChanged(@NonNull final PlaybackPresentationMode mode) {
    }

    /** Called after the main player has re-enabled video on return from the background. */
    void onVideoPlaybackResumed();

    void onScreenRotationButtonClicked(boolean fullscreen);

    /** Called when actual video dimensions become available during a fullscreen transition. */
    default void onVideoContentOrientationChanged() {
    }

    default void onManualFullscreenButtonClicked(final boolean fullscreen) {
        onScreenRotationButtonClicked(fullscreen);
    }

    void onMoreOptionsLongClicked();

    void onPlayerError(PlaybackException error, boolean isCatchableException);

    void hideSystemUiIfNeeded();
}
