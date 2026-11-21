package org.schabi.newpipe.player.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.schabi.newpipe.player.ui.FullscreenOrientationPolicy.VideoContentOrientation.LANDSCAPE;
import static org.schabi.newpipe.player.ui.FullscreenOrientationPolicy.VideoContentOrientation.PORTRAIT;
import static org.schabi.newpipe.player.ui.FullscreenOrientationPolicy.VideoContentOrientation.SQUARE;
import static org.schabi.newpipe.player.ui.FullscreenOrientationPolicy.VideoContentOrientation.UNKNOWN;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;

import org.junit.Test;
import org.schabi.newpipe.player.helper.PlayerRotationMode;

public class FullscreenOrientationPolicyTest {
    @Test
    public void stalePortraitFullscreenExitIsAppliedImmediately() {
        assertEquals(Configuration.ORIENTATION_PORTRAIT,
                FullscreenOrientationPolicy.targetConfigurationOrientation(false, LANDSCAPE));
        assertTrue(FullscreenOrientationPolicy.isTargetOrientation(
                Configuration.ORIENTATION_PORTRAIT,
                Configuration.ORIENTATION_PORTRAIT));
    }

    @Test
    public void unknownFullscreenEntryFallsBackToLandscapeUntilMetadataArrives() {
        assertEquals(Configuration.ORIENTATION_LANDSCAPE,
                FullscreenOrientationPolicy.targetConfigurationOrientation(true, UNKNOWN));
        assertEquals(Configuration.ORIENTATION_PORTRAIT,
                FullscreenOrientationPolicy.targetConfigurationOrientation(true, PORTRAIT));
        assertEquals(Configuration.ORIENTATION_PORTRAIT,
                FullscreenOrientationPolicy.targetConfigurationOrientation(true, SQUARE));
        assertEquals(Configuration.ORIENTATION_LANDSCAPE,
                FullscreenOrientationPolicy.targetConfigurationOrientation(true, LANDSCAPE));
    }

    @Test
    public void manualFullscreenUsesContentOrientationAndExitReturnsPortrait() {
        assertEquals(Configuration.ORIENTATION_LANDSCAPE,
                FullscreenOrientationPolicy.targetConfigurationOrientation(true, LANDSCAPE));
        assertEquals(Configuration.ORIENTATION_PORTRAIT,
                FullscreenOrientationPolicy.targetConfigurationOrientation(false, PORTRAIT));
    }

    @Test
    public void pendingExitOverridesPortraitVideoPreservation() {
        assertEquals(FullscreenOrientationPolicy.EXIT_FULLSCREEN,
                FullscreenOrientationPolicy.resolveFullscreenState(
                        Configuration.ORIENTATION_PORTRAIT,
                        true,
                        PORTRAIT,
                        true,
                        Configuration.ORIENTATION_PORTRAIT,
                        FullscreenOrientationPolicy.EXIT_FULLSCREEN));
    }

    @Test
    public void pendingPortraitFullscreenEntryCanCompleteInPortrait() {
        assertEquals(FullscreenOrientationPolicy.ENTER_FULLSCREEN,
                FullscreenOrientationPolicy.resolveFullscreenState(
                        Configuration.ORIENTATION_PORTRAIT,
                        false,
                        PORTRAIT,
                        true,
                        Configuration.ORIENTATION_PORTRAIT,
                        FullscreenOrientationPolicy.ENTER_FULLSCREEN));
    }

    @Test
    public void automaticPortraitSquareAndUnknownContentPreserveFullscreenState() {
        final FullscreenOrientationPolicy.VideoContentOrientation[] preservedOrientations = {
                PORTRAIT, SQUARE, UNKNOWN
        };
        for (final var contentOrientation : preservedOrientations) {
            assertEquals(FullscreenOrientationPolicy.KEEP_FULLSCREEN_STATE,
                    FullscreenOrientationPolicy.resolveFullscreenState(
                            Configuration.ORIENTATION_LANDSCAPE,
                            false,
                            contentOrientation,
                            true,
                            Configuration.ORIENTATION_UNDEFINED,
                            FullscreenOrientationPolicy.KEEP_FULLSCREEN_STATE));
            assertEquals(FullscreenOrientationPolicy.KEEP_FULLSCREEN_STATE,
                    FullscreenOrientationPolicy.resolveFullscreenState(
                            Configuration.ORIENTATION_PORTRAIT,
                            true,
                            contentOrientation,
                            true,
                            Configuration.ORIENTATION_UNDEFINED,
                            FullscreenOrientationPolicy.KEEP_FULLSCREEN_STATE));
        }
    }

    @Test
    public void manualFullscreenExitCanRemainNonFullscreenInLandscape() {
        assertTrue(FullscreenOrientationPolicy.shouldSuppressFullscreenForManualExit(
                true, Configuration.ORIENTATION_LANDSCAPE));
        assertFalse(FullscreenOrientationPolicy.shouldSuppressFullscreenForManualExit(
                true, Configuration.ORIENTATION_PORTRAIT));
        assertFalse(FullscreenOrientationPolicy.shouldSuppressFullscreenForManualExit(
                false, Configuration.ORIENTATION_LANDSCAPE));
    }

    @Test
    public void automaticLandscapePhoneTracksConfiguration() {
        assertEquals(FullscreenOrientationPolicy.ENTER_FULLSCREEN,
                FullscreenOrientationPolicy.resolveFullscreenState(
                        Configuration.ORIENTATION_LANDSCAPE,
                        false,
                        LANDSCAPE,
                        true,
                        Configuration.ORIENTATION_UNDEFINED,
                        FullscreenOrientationPolicy.KEEP_FULLSCREEN_STATE));
        assertEquals(FullscreenOrientationPolicy.EXIT_FULLSCREEN,
                FullscreenOrientationPolicy.resolveFullscreenState(
                        Configuration.ORIENTATION_PORTRAIT,
                        true,
                        LANDSCAPE,
                        true,
                        Configuration.ORIENTATION_UNDEFINED,
                        FullscreenOrientationPolicy.KEEP_FULLSCREEN_STATE));
    }

    @Test
    public void pendingTransitionWaitsForItsRequestedOrientation() {
        assertEquals(FullscreenOrientationPolicy.KEEP_FULLSCREEN_STATE,
                FullscreenOrientationPolicy.resolveFullscreenState(
                        Configuration.ORIENTATION_PORTRAIT,
                        true,
                        LANDSCAPE,
                        true,
                        Configuration.ORIENTATION_LANDSCAPE,
                        FullscreenOrientationPolicy.ENTER_FULLSCREEN));
    }

    @Test
    public void videoContentOrientationUsesDisplayedAspectRatio() {
        assertEquals(PORTRAIT,
                FullscreenOrientationPolicy.classifyVideoContentOrientation(1080, 1920, 1.0f));
        assertEquals(LANDSCAPE,
                FullscreenOrientationPolicy.classifyVideoContentOrientation(1920, 1080, 1.0f));
        assertEquals(SQUARE,
                FullscreenOrientationPolicy.classifyVideoContentOrientation(1080, 1080, 1.0f));
        assertEquals(SQUARE,
                FullscreenOrientationPolicy.classifyVideoContentOrientation(1080, 1000, 1.0f));
        assertEquals(UNKNOWN,
                FullscreenOrientationPolicy.classifyVideoContentOrientation(0, 1920, 1.0f));

        // Anamorphic/sample-aspect metadata must be considered instead of raw pixel dimensions.
        assertEquals(LANDSCAPE,
                FullscreenOrientationPolicy.classifyVideoContentOrientation(720, 1080, 2.0f));
    }

    @Test
    public void onlyConfirmedLandscapeContentUsesAutomaticFullscreen() {
        assertTrue(FullscreenOrientationPolicy.supportsAutomaticFullscreen(LANDSCAPE));
        assertFalse(FullscreenOrientationPolicy.supportsAutomaticFullscreen(PORTRAIT));
        assertFalse(FullscreenOrientationPolicy.supportsAutomaticFullscreen(SQUARE));
        assertFalse(FullscreenOrientationPolicy.supportsAutomaticFullscreen(UNKNOWN));

        assertTrue(FullscreenOrientationPolicy.shouldAlignFullscreenToKnownContent(
                true, UNKNOWN, PORTRAIT));
        assertTrue(FullscreenOrientationPolicy.shouldAlignFullscreenToKnownContent(
                true, UNKNOWN, LANDSCAPE));
        assertTrue(FullscreenOrientationPolicy.shouldAlignFullscreenToKnownContent(
                true, UNKNOWN, SQUARE));
        assertFalse(FullscreenOrientationPolicy.shouldAlignFullscreenToKnownContent(
                false, UNKNOWN, PORTRAIT));
        assertFalse(FullscreenOrientationPolicy.shouldAlignFullscreenToKnownContent(
                true, PORTRAIT, PORTRAIT));

        assertTrue(FullscreenOrientationPolicy.shouldAlignFullscreenToKnownContent(
                true, UNKNOWN, PORTRAIT));
        assertTrue(FullscreenOrientationPolicy.shouldAlignFullscreenToKnownContent(
                true, UNKNOWN, SQUARE));
        assertTrue(FullscreenOrientationPolicy.shouldAlignFullscreenToKnownContent(
                true, UNKNOWN, LANDSCAPE));
    }

    @Test
    public void fixedAndSensorLandscapeRequestsRemainDistinct() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                FullscreenOrientationPolicy.requestedLandscapeOrientation(
                        PlayerRotationMode.FIXED));
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                FullscreenOrientationPolicy.requestedLandscapeOrientation(
                        PlayerRotationMode.SYSTEM));
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                FullscreenOrientationPolicy.requestedLandscapeOrientation(
                        PlayerRotationMode.SENSORS));
    }

    @Test
    public void expandedPhoneVideoProtectsPhoneLayoutDuringLandscapeFullscreen() {
        assertTrue(FullscreenOrientationPolicy.shouldKeepPhonePlayerLayoutForLandscape(
                Configuration.ORIENTATION_LANDSCAPE,
                true, true, false, true, false));
        assertFalse(FullscreenOrientationPolicy.shouldKeepPhonePlayerLayoutForLandscape(
                Configuration.ORIENTATION_LANDSCAPE,
                true, true, false, true, true));
    }

    @Test
    public void staleWidePortraitLayoutRequiresRecreation() {
        assertTrue(FullscreenOrientationPolicy.shouldRecreateDetailLayout(
                true, Configuration.ORIENTATION_PORTRAIT, 900));
        assertFalse(FullscreenOrientationPolicy.shouldRecreateDetailLayout(
                false, Configuration.ORIENTATION_PORTRAIT, 900));
    }
}
