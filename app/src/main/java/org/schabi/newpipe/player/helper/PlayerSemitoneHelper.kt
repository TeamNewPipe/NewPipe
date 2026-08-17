package org.schabi.newpipe.player.helper

import androidx.core.math.MathUtils
import kotlin.math.log
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Converts between percent and 12-tone equal temperament semitones.
 * <br/>
 * @see
 * <a href="https://en.wikipedia.org/wiki/Equal_temperament#Twelve-tone_equal_temperament">
 *     Wikipedia: Equal temperament#Twelve-tone equal temperament
 * </a>
 */
object PlayerSemitoneHelper {
    const val SEMITONE_COUNT = 12

    @JvmStatic
    fun formatPitchSemitones(percent: Double): String {
        return formatPitchSemitones(percentToSemitones(percent))
    }

    @JvmStatic
    fun formatPitchSemitones(semitones: Int): String {
        return if (semitones > 0) "+$semitones" else "$semitones"
    }

    @JvmStatic
    fun semitonesToPercent(semitones: Int): Double {
        return 2.0.pow(ensureSemitonesInRange(semitones).toDouble() / SEMITONE_COUNT)
    }

    @JvmStatic
    fun percentToSemitones(percent: Double): Int {
        return ensureSemitonesInRange(
            (SEMITONE_COUNT * log(percent, 2.0)).roundToInt()
        )
    }

    private fun ensureSemitonesInRange(semitones: Int): Int {
        return MathUtils.clamp(semitones, -SEMITONE_COUNT, SEMITONE_COUNT)
    }
}
