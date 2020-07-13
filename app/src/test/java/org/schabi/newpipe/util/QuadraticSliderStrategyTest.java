package org.schabi.newpipe.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class QuadraticSliderStrategyTest {
    private static final int STEP = 100;
    private static final float DELTA = 1f / (float) STEP;

    private final SliderStrategy.Quadratic standard =
            new SliderStrategy.Quadratic(0f, 100f, 50f, STEP);

    @Test
    public void testLeftBound() {
        assertEquals(standard.progressOf(0), 0);
        assertEquals(standard.valueOf(0), 0f, DELTA);
    }

    @Test
    public void testCenter() {
        assertEquals(standard.progressOf(50), 50);
        assertEquals(standard.valueOf(50), 50f, DELTA);
    }

    @Test
    public void testRightBound() {
        assertEquals(standard.progressOf(100), 100);
        assertEquals(standard.valueOf(100), 100f, DELTA);
    }

    @Test
    public void testLeftRegion() {
        final int leftProgress = standard.progressOf(25);
        final double leftValue = standard.valueOf(25);
        assertTrue(leftProgress > 0 && leftProgress < 50);
        assertTrue(leftValue > 0f && leftValue < 50);
    }

    @Test
    public void testRightRegion() {
        final int leftProgress = standard.progressOf(75);
        final double leftValue = standard.valueOf(75);
        assertTrue(leftProgress > 50 && leftProgress < 100);
        assertTrue(leftValue > 50f && leftValue < 100);
    }

    @Test
    public void testConversion() {
        assertEquals(standard.progressOf(standard.valueOf(0)), 0);
        assertEquals(standard.progressOf(standard.valueOf(25)), 25);
        assertEquals(standard.progressOf(standard.valueOf(50)), 50);
        assertEquals(standard.progressOf(standard.valueOf(75)), 75);
        assertEquals(standard.progressOf(standard.valueOf(100)), 100);
    }

    @Test
    public void testReverseConversion() {
        // Need a larger delta since step size / granularity is too small and causes
        // floating point round-off errors during conversion
        final float largeDelta = 1f;

        assertEquals(standard.valueOf(standard.progressOf(0)), 0f, largeDelta);
        assertEquals(standard.valueOf(standard.progressOf(25)), 25f, largeDelta);
        assertEquals(standard.valueOf(standard.progressOf(50)), 50f, largeDelta);
        assertEquals(standard.valueOf(standard.progressOf(75)), 75f, largeDelta);
        assertEquals(standard.valueOf(standard.progressOf(100)), 100f, largeDelta);
    }

    @Test
    public void testQuadraticPropertyLeftRegion() {
        final double differenceCloserToCenter =
                Math.abs(standard.valueOf(40) - standard.valueOf(45));
        final double differenceFurtherFromCenter =
                Math.abs(standard.valueOf(10) - standard.valueOf(15));
        assertTrue(differenceCloserToCenter < differenceFurtherFromCenter);
    }

    @Test
    public void testQuadraticPropertyRightRegion() {
        final double differenceCloserToCenter =
                Math.abs(standard.valueOf(75) - standard.valueOf(70));
        final double differenceFurtherFromCenter =
                Math.abs(standard.valueOf(95) - standard.valueOf(90));
        assertTrue(differenceCloserToCenter < differenceFurtherFromCenter);
    }
}
