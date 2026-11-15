package org.schabi.newpipe.local.history;

import androidx.annotation.NonNull;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;

public final class HistoryDateNavigator {
    private HistoryDateNavigator() {
    }

    /**
     * Finds the closest date in a list sorted from newest to oldest.
     *
     * <p>When two entries are equally close, the newer entry is preferred.</p>
     *
     * @param dates descending chronological dates
     * @param target requested date
     * @return closest index, or {@code -1} for an empty list
     */
    public static int findClosestIndex(@NonNull final List<LocalDate> dates,
                                       @NonNull final LocalDate target) {
        return findClosestIndex(dates.size(), dates::get, target);
    }

    public static int findClosestIndex(final int size,
                                       @NonNull final IntFunction<LocalDate> dateAt,
                                       @NonNull final LocalDate target) {
        if (size == 0) {
            return -1;
        }

        int low = 0;
        int high = size - 1;
        while (low <= high) {
            final int middle = low + (high - low) / 2;
            final int comparison = dateAt.apply(middle).compareTo(target);
            if (comparison == 0) {
                return middle;
            } else if (comparison > 0) {
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }

        if (low >= size) {
            return size - 1;
        }
        if (high < 0) {
            return 0;
        }

        final long newerDistance = Math.abs(
                ChronoUnit.DAYS.between(dateAt.apply(high), target));
        final long olderDistance = Math.abs(
                ChronoUnit.DAYS.between(dateAt.apply(low), target));
        return newerDistance <= olderDistance ? high : low;
    }

    @NonNull
    public static String formatLabel(@NonNull final LocalDate date,
                                     @NonNull final Locale locale) {
        return DateTimeFormatter.ofPattern("MMM yyyy", locale).format(date);
    }
}
