package org.schabi.newpipe.player.event;

import com.google.android.exoplayer2.PlaybackException;

/** {@link org.schabi.newpipe.player.event.PlayerEventListener} that also gets called for
 * application-specific events like screen rotation or UI changes.
 */
public interface PlayerServiceEventListener extends PlayerEventListener {
    void onViewCreated();

    void onFullscreenStateChanged(boolean fullscreen);

    void onScreenRotationButtonClicked();

    void onMoreOptionsLongClicked();

    void onPlayerError(PlaybackException error, boolean isCatchableException);

    void hideSystemUiIfNeeded();
}
