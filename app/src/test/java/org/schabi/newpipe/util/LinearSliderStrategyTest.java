package org.schabi.newpipe.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LinearSliderStrategyTest {
    private static final int STEP = 100;
    private static final float DELTA = 1f / (float) STEP;

    private final SliderStrategy.Linear standard =
            new SliderStrategy.Linear(1f, 3f, STEP);

    @Test
    public void testLeftBound() {
        assertEquals(0, standard.progressOf(1f));
        assertEquals(1f, standard.valueOf(0), DELTA);
    }

    @Test
    public void testCenter() {
        assertEquals(50, standard.progressOf(2f));
        assertEquals(2f, standard.valueOf(50), DELTA);
    }

    @Test
    public void testRightBound() {
        assertEquals(100, standard.progressOf(3f));
        assertEquals(3f, standard.valueOf(100), DELTA);
    }

    @Test
    public void testLinearProperty() {
        final double lowerDifference = Math.abs(standard.valueOf(10) - standard.valueOf(20));
        final double upperDifference = Math.abs(standard.valueOf(80) - standard.valueOf(90));
        assertEquals(lowerDifference, upperDifference, DELTA);
    }

    @Test
    public void testConversion() {
        assertEquals(0, standard.progressOf(standard.valueOf(0)));
        assertEquals(25, standard.progressOf(standard.valueOf(25)));
        assertEquals(50, standard.progressOf(standard.valueOf(50)));
        assertEquals(75, standard.progressOf(standard.valueOf(75)));
        assertEquals(100, standard.progressOf(standard.valueOf(100)));
    }

    @Test
    public void testReverseConversion() {
        assertEquals(1.00f, standard.valueOf(standard.progressOf(1.00f)), DELTA);
        assertEquals(1.50f, standard.valueOf(standard.progressOf(1.50f)), DELTA);
        assertEquals(2.00f, standard.valueOf(standard.progressOf(2.00f)), DELTA);
        assertEquals(2.50f, standard.valueOf(standard.progressOf(2.50f)), DELTA);
        assertEquals(3.00f, standard.valueOf(standard.progressOf(3.00f)), DELTA);
    }
}
