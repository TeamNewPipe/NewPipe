package org.schabi.newpipe.player.helper;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlaybackSpeedLimitsTest {
    @Test
    public void playbackSpeedAllowsTenTimesWhilePitchRemainsConservative() {
        assertEquals(10.0, PlaybackParameterDialog.MAX_PLAYBACK_SPEED, 0.0);
        assertEquals(3.0, PlaybackParameterDialog.MAX_PITCH, 0.0);
    }
}
