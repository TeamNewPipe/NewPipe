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
    public static final int SEMITONE_COUNT = 12;

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
        return Math.pow(2, ensureSemitonesInRange(semitones) / (double) SEMITONE_COUNT);
    }

    public static int percentToSemitones(final double percent) {
        return ensureSemitonesInRange(
                (int) Math.round(SEMITONE_COUNT * Math.log(percent) / Math.log(2)));
    }

    private static int ensureSemitonesInRange(final int semitones) {
        return Math.max(-SEMITONE_COUNT, Math.min(SEMITONE_COUNT, semitones));
    }
}
