package org.schabi.newpipe.player.helper;

/**
 * Converts between percent and 12-tone equal temperament semitones.
 * <br/>
 * @see
 * <a href="https://en.wikipedia.org/wiki/Equal_temperament#Twelve-tone_equal_temperament">
 *     Wikipedia: Equal temperament#Twelve-tone equal temperament
 * </a>
 */
public final class PlayerSemitoneHelper {
    public static final int TONES = 12;

    private PlayerSemitoneHelper() {
        // No impl
    }

    public static String formatPitchSemitones(final double percent) {
        return formatPitchSemitones(percentToSemitones(percent));
    }

    public static String formatPitchSemitones(final int semitones) {
        return semitones > 0 ? "+" + semitones : "" + semitones;
    }

    public static double semitonesToPercent(final int semitones) {
        return Math.pow(2, ensureSemitonesInRange(semitones) / (double) TONES);
    }

    public static int percentToSemitones(final double percent) {
        return ensureSemitonesInRange((int) Math.round(TONES * Math.log(percent) / Math.log(2)));
    }

    private static int ensureSemitonesInRange(final int semitones) {
        return Math.max(-TONES, Math.min(TONES, semitones));
    }
}
