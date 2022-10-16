package org.schabi.newpipe.util;

import java.util.Locale;

public final class TimeUtils {
    private TimeUtils() {
    }

    public static String millisecondsToString(final double milliseconds) {
        final int seconds = (int) (milliseconds / 1000) % 60;
        final int minutes = (int) ((milliseconds / (1000 * 60)) % 60);
        final int hours = (int) ((milliseconds / (1000 * 60 * 60)) % 24);

        return String.format(Locale.getDefault(),
                "%02d:%02d:%02d", hours, minutes, seconds);
    }
}
