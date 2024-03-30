package org.schabi.newpipe.util

import org.junit.Assert
import org.junit.Test
import org.schabi.newpipe.util.SliderStrategy.Quadratic
import kotlin.math.abs

class QuadraticSliderStrategyTest {
    private val standard = Quadratic(0.0, 100.0, 50.0, STEP)
    @Test
    fun testLeftBound() {
        Assert.assertEquals(standard.progressOf(0.0).toLong(), 0)
        Assert.assertEquals(standard.valueOf(0), 0.0, DELTA.toDouble())
    }

    @Test
    fun testCenter() {
        Assert.assertEquals(standard.progressOf(50.0).toLong(), 50)
        Assert.assertEquals(standard.valueOf(50), 50.0, DELTA.toDouble())
    }

    @Test
    fun testRightBound() {
        Assert.assertEquals(standard.progressOf(100.0).toLong(), 100)
        Assert.assertEquals(standard.valueOf(100), 100.0, DELTA.toDouble())
    }

    @Test
    fun testLeftRegion() {
        val leftProgress = standard.progressOf(25.0)
        val leftValue = standard.valueOf(25)
        Assert.assertTrue(leftProgress > 0 && leftProgress < 50)
        Assert.assertTrue(leftValue > 0f && leftValue < 50)
    }

    @Test
    fun testRightRegion() {
        val leftProgress = standard.progressOf(75.0)
        val leftValue = standard.valueOf(75)
        Assert.assertTrue(leftProgress > 50 && leftProgress < 100)
        Assert.assertTrue(leftValue > 50f && leftValue < 100)
    }

    @Test
    fun testConversion() {
        Assert.assertEquals(standard.progressOf(standard.valueOf(0)).toLong(), 0)
        Assert.assertEquals(standard.progressOf(standard.valueOf(25)).toLong(), 25)
        Assert.assertEquals(standard.progressOf(standard.valueOf(50)).toLong(), 50)
        Assert.assertEquals(standard.progressOf(standard.valueOf(75)).toLong(), 75)
        Assert.assertEquals(standard.progressOf(standard.valueOf(100)).toLong(), 100)
    }

    @Test
    fun testReverseConversion() {
        // Need a larger delta since step size / granularity is too small and causes
        // floating point round-off errors during conversion
        val largeDelta = 1f
        Assert.assertEquals(standard.valueOf(standard.progressOf(0.0)), 0.0, largeDelta.toDouble())
        Assert.assertEquals(standard.valueOf(standard.progressOf(25.0)), 25.0, largeDelta.toDouble())
        Assert.assertEquals(standard.valueOf(standard.progressOf(50.0)), 50.0, largeDelta.toDouble())
        Assert.assertEquals(standard.valueOf(standard.progressOf(75.0)), 75.0, largeDelta.toDouble())
        Assert.assertEquals(standard.valueOf(standard.progressOf(100.0)), 100.0, largeDelta.toDouble())
    }

    @Test
    fun testQuadraticPropertyLeftRegion() {
        val differenceCloserToCenter = abs(standard.valueOf(40) - standard.valueOf(45))
        val differenceFurtherFromCenter = abs(standard.valueOf(10) - standard.valueOf(15))
        Assert.assertTrue(differenceCloserToCenter < differenceFurtherFromCenter)
    }

    @Test
    fun testQuadraticPropertyRightRegion() {
        val differenceCloserToCenter = abs(standard.valueOf(75) - standard.valueOf(70))
        val differenceFurtherFromCenter = abs(standard.valueOf(95) - standard.valueOf(90))
        Assert.assertTrue(differenceCloserToCenter < differenceFurtherFromCenter)
    }

    companion object {
        private const val STEP = 100
        private const val DELTA = 1f / STEP.toFloat()
    }
}
