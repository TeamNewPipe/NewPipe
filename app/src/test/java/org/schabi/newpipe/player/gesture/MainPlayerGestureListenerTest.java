package org.schabi.newpipe.player.gesture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.schabi.newpipe.player.helper.PlaybackParameterPreferences;

public class MainPlayerGestureListenerTest {
    private static final float FLOAT_TOLERANCE = 0.001f;

    @Test
    public void embeddedDownwardSwipeDelegatesToBottomSheet() {
        assertTrue(MainPlayerGestureListener.shouldDelegateDownwardSwipeToBottomSheet(
                false, true, 5f, 80f));
    }

    @Test
    public void disabledSwipeDownSettingDoesNotDelegate() {
        assertFalse(MainPlayerGestureListener.shouldDelegateDownwardSwipeToBottomSheet(
                false, false, 5f, 80f));
    }

    @Test
    public void fullscreenDownwardSwipeRemainsPlayerGesture() {
        assertFalse(MainPlayerGestureListener.shouldDelegateDownwardSwipeToBottomSheet(
                true, true, 5f, 80f));
    }

    @Test
    public void embeddedUpwardSwipeRemainsFullscreenGesture() {
        assertFalse(MainPlayerGestureListener.shouldDelegateDownwardSwipeToBottomSheet(
                false, true, 5f, -80f));
    }

    @Test
    public void horizontalSwipeRemainsPlayerGesture() {
        assertFalse(MainPlayerGestureListener.shouldDelegateDownwardSwipeToBottomSheet(
                false, true, 80f, 50f));
    }

    @Test
    public void movementBelowThresholdDoesNotDelegate() {
        assertFalse(MainPlayerGestureListener.shouldDelegateDownwardSwipeToBottomSheet(
                false, true, 2f, 40f));
    }

    @Test
    public void verticalTwoFingerMovementLocksSpeedGesture() {
        assertEquals(MainPlayerGestureListener.TwoFingerGestureState.SPEED,
                MainPlayerGestureListener.classifyTwoFingerGesture(
                        60f, 5f, 8f, 12f, false));
    }

    @Test
    public void spanDominantMovementIsTreatedAsPinch() {
        assertEquals(MainPlayerGestureListener.TwoFingerGestureState.ZOOM,
                MainPlayerGestureListener.classifyTwoFingerGesture(
                        20f, 2f, 50f, 12f, false));
    }

    @Test
    public void twoFingerGestureWaitsForClearIntent() {
        assertEquals(MainPlayerGestureListener.TwoFingerGestureState.PENDING,
                MainPlayerGestureListener.classifyTwoFingerGesture(
                        8f, 4f, 3f, 12f, false));
    }

    @Test
    public void twoFingerMovementPansWhileAlreadyZoomed() {
        assertEquals(MainPlayerGestureListener.TwoFingerGestureState.ZOOM,
                MainPlayerGestureListener.classifyTwoFingerGesture(
                        60f, 4f, 2f, 12f, true));
    }

    @Test
    public void disabledPinchSettingIgnoresZoomGesture() {
        assertEquals(MainPlayerGestureListener.TwoFingerGestureState.IGNORED,
                MainPlayerGestureListener.applyGestureSettings(
                        MainPlayerGestureListener.TwoFingerGestureState.ZOOM,
                        false,
                        true));
    }

    @Test
    public void disabledPinchSettingKeepsEnabledSpeedGesture() {
        assertEquals(MainPlayerGestureListener.TwoFingerGestureState.SPEED,
                MainPlayerGestureListener.applyGestureSettings(
                        MainPlayerGestureListener.TwoFingerGestureState.SPEED,
                        false,
                        true));
    }

    @Test
    public void enabledPinchSettingKeepsZoomGesture() {
        assertEquals(MainPlayerGestureListener.TwoFingerGestureState.ZOOM,
                MainPlayerGestureListener.applyGestureSettings(
                        MainPlayerGestureListener.TwoFingerGestureState.ZOOM,
                        true,
                        false));
    }

    @Test
    public void pinchZoomIsScaledAndClamped() {
        assertEquals(2f, MainPlayerGestureListener.calculateZoomScale(1f, 200f, 100f),
                FLOAT_TOLERANCE);
        assertEquals(4f, MainPlayerGestureListener.calculateZoomScale(3f, 200f, 100f),
                FLOAT_TOLERANCE);
        assertEquals(1f, MainPlayerGestureListener.calculateZoomScale(2f, 20f, 100f),
                FLOAT_TOLERANCE);
    }

    @Test
    public void twoFingerMovementUsesEverySupportedAdjustmentStep() {
        final float[] adjustmentSteps = {
                PlaybackParameterPreferences.STEP_1_PERCENT,
                PlaybackParameterPreferences.STEP_5_PERCENT,
                PlaybackParameterPreferences.STEP_10_PERCENT,
                PlaybackParameterPreferences.STEP_25_PERCENT
        };

        for (final float adjustmentStep : adjustmentSteps) {
            assertEquals(1.0f + adjustmentStep,
                    MainPlayerGestureListener.calculatePlaybackSpeed(
                            1.0f, 24f, 24f, adjustmentStep),
                    FLOAT_TOLERANCE);
        }
    }

    @Test
    public void downwardTwoFingerMovementCanLandExactlyOnNormalSpeed() {
        assertEquals(1.0f,
                MainPlayerGestureListener.calculatePlaybackSpeed(
                        1.13f, -72f, 24f, 0.05f),
                FLOAT_TOLERANCE);
    }

    @Test
    public void twoFingerMovementRoundsToSelectedStep() {
        assertEquals(1.25f,
                MainPlayerGestureListener.calculatePlaybackSpeed(
                        1.0f, 20f, 24f, 0.25f),
                FLOAT_TOLERANCE);
    }

    @Test
    public void twoFingerSpeedIsClampedToPlayerLimits() {
        assertEquals(10.0f,
                MainPlayerGestureListener.calculatePlaybackSpeed(
                        9.95f, 240f, 24f, 0.25f),
                FLOAT_TOLERANCE);
        assertEquals(0.10f,
                MainPlayerGestureListener.calculatePlaybackSpeed(
                        0.15f, -240f, 24f, 0.25f),
                FLOAT_TOLERANCE);
    }
}
