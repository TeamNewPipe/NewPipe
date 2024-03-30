package org.schabi.newpipe.util

import kotlin.math.abs
import kotlin.math.sqrt

open interface SliderStrategy {
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
    class Quadratic(minimum: Double, maximum: Double, center: Double,
                    maxProgress: Int) : SliderStrategy {
        private val leftGap: Double
        private val rightGap: Double
        private val center: Double
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
            if (center < minimum || center > maximum) {
                throw IllegalArgumentException("Center must be in between minimum and maximum")
            }
            leftGap = minimum - center
            rightGap = maximum - center
            this.center = center
            centerProgress = maxProgress / 2
        }

        public override fun progressOf(value: Double): Int {
            val difference: Double = value - center
            val root: Double = if (difference >= 0) sqrt(difference / rightGap) else -sqrt(abs(difference / leftGap))
            val offset: Double = Math.round(root * centerProgress).toDouble()
            return (centerProgress + offset).toInt()
        }

        public override fun valueOf(progress: Int): Double {
            val offset: Int = progress - centerProgress
            val square: Double = ((offset.toDouble()) / (centerProgress.toDouble())).pow(2.0)
            val difference: Double = square * (if (offset >= 0) rightGap else leftGap)
            return difference + center
        }
    }
}
