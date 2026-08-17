package org.schabi.newpipe.util

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

interface SliderStrategy {
    /**
     * Converts from zeroed double with a minimum offset to the nearest rounded slider
     * equivalent integer.
     *
     * @param value the value to convert
     * @return the converted value
     */
    fun progressOf(value: Double): Int

    /**
     * Converts from slider integer value to an equivalent double value with a given
     * minimum offset.
     *
     * @param progress the value to convert
     * @return the converted value
     */
    fun valueOf(progress: Int): Double

    // TODO: also implement linear strategy when needed

    class Quadratic(minimum: Double, maximum: Double, private val center: Double, maxProgress: Int) : SliderStrategy {
        private val leftGap: Double
        private val rightGap: Double
        private val centerProgress: Int

        /**
         * Quadratic slider strategy that scales the value of a slider given how far the slider
         * progress is from the center of the slider. The further away from the center,
         * the faster the interpreted value changes, and vice versa.
         *
         * @param minimum     the minimum value of the interpreted value of the slider.
         * @param maximum     the maximum value of the interpreted value of the slider.
         * @param center      center of the interpreted value between the minimum and maximum, which
         * will be used as the center value on the slider progress. Doesn't need
         * to be the average of the minimum and maximum values, but must be in
         * between the two.
         * @param maxProgress the maximum possible progress of the slider, this is the
         * value that is shown for the UI and controls the granularity of
         * the slider. Should be as large as possible to avoid floating
         * point round-off error. Using odd number is recommended.
         */
        init {
            require(!(center < minimum || center > maximum)) { "Center must be in between minimum and maximum" }

            this.leftGap = minimum - center
            this.rightGap = maximum - center
            this.centerProgress = maxProgress / 2
        }

        override fun progressOf(value: Double): Int {
            val difference = value - center
            val root = if (difference >= 0) sqrt(difference / rightGap) else -sqrt(abs(difference / leftGap))
            val offset = (root * centerProgress).roundToInt()

            return centerProgress + offset
        }

        override fun valueOf(progress: Int): Double {
            val offset = progress - centerProgress
            val square = (offset.toDouble() / centerProgress.toDouble()).pow(2.0)
            val difference = square * (if (offset >= 0) rightGap else leftGap)

            return difference + center
        }
    }
}
