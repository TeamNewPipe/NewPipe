package org.schabi.newpipe.util

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuadraticSliderStrategyTest {

    private val standard = SliderStrategy.Quadratic(0f, 100f, 50f, STEP)

    @Test
    fun testLeftBound() {
        assertEquals(0, standard.progressOf(0f))
        assertEquals(0f, standard.valueOf(0), DELTA)
    }

    @Test
    fun testCenter() {
        assertEquals(50, standard.progressOf(50f))
        assertEquals(50f, standard.valueOf(50), DELTA)
    }

    @Test
    fun testRightBound() {
        assertEquals(100, standard.progressOf(100f))
        assertEquals(100f, standard.valueOf(100), DELTA)
    }

    @Test
    fun testLeftRegion() {
        val leftProgress = standard.progressOf(25f)
        val leftValue = standard.valueOf(25)
        assertTrue(leftProgress > 0 && leftProgress < 50)
        assertTrue(leftValue > 0f && leftValue < 50)
    }

    @Test
    fun testRightRegion() {
        val leftProgress = standard.progressOf(75f)
        val leftValue = standard.valueOf(75)
        assertTrue(leftProgress > 50 && leftProgress < 100)
        assertTrue(leftValue > 50f && leftValue < 100)
    }

    @Test
    fun testConversion() {
        assertEquals(0, standard.progressOf(standard.valueOf(0)))
        assertEquals(25, standard.progressOf(standard.valueOf(25)))
        assertEquals(50, standard.progressOf(standard.valueOf(50)))
        assertEquals(75, standard.progressOf(standard.valueOf(75)))
        assertEquals(100, standard.progressOf(standard.valueOf(100)))
    }

    @Test
    fun testReverseConversion() {
        // Need a larger delta since step size / granularity is too small and causes
        // floating point round-off errors during conversion
        val largeDelta = 1f

        assertEquals(0f, standard.valueOf(standard.progressOf(0f)), largeDelta)
        assertEquals(25f, standard.valueOf(standard.progressOf(25f)), largeDelta)
        assertEquals(50f, standard.valueOf(standard.progressOf(50f)), largeDelta)
        assertEquals(75f, standard.valueOf(standard.progressOf(75f)), largeDelta)
        assertEquals(100f, standard.valueOf(standard.progressOf(100f)), largeDelta)
    }

    @Test
    fun testQuadraticPropertyLeftRegion() {
        val differenceCloserToCenter = abs(standard.valueOf(40) - standard.valueOf(45))
        val differenceFurtherFromCenter = abs(standard.valueOf(10) - standard.valueOf(15))
        assertTrue(differenceCloserToCenter < differenceFurtherFromCenter)
    }

    @Test
    fun testQuadraticPropertyRightRegion() {
        val differenceCloserToCenter = abs(standard.valueOf(75) - standard.valueOf(70))
        val differenceFurtherFromCenter = abs(standard.valueOf(95) - standard.valueOf(90))
        assertTrue(differenceCloserToCenter < differenceFurtherFromCenter)
    }

    companion object {
        private const val STEP = 100
        private const val DELTA = 1f / STEP.toFloat()
    }
}
