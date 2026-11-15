package org.schabi.newpipe.local.history;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class HistoryDateNavigatorTest {
    private final List<LocalDate> dates = Arrays.asList(
            LocalDate.of(2026, 8, 20),
            LocalDate.of(2025, 12, 10),
            LocalDate.of(2024, 1, 1));

    @Test
    public void findsExactAndClosestDatesInDescendingHistory() {
        assertEquals(1, HistoryDateNavigator.findClosestIndex(
                dates, LocalDate.of(2025, 12, 10)));
        assertEquals(0, HistoryDateNavigator.findClosestIndex(
                dates, LocalDate.of(2027, 1, 1)));
        assertEquals(2, HistoryDateNavigator.findClosestIndex(
                dates, LocalDate.of(2020, 1, 1)));
        assertEquals(1, HistoryDateNavigator.findClosestIndex(
                dates, LocalDate.of(2025, 11, 1)));
        assertEquals(-1, HistoryDateNavigator.findClosestIndex(
                Collections.emptyList(), LocalDate.of(2026, 1, 1)));
    }

    @Test
    public void alwaysFormatsMonthAndYear() {
        assertEquals("Aug 2026", HistoryDateNavigator.formatLabel(
                LocalDate.of(2026, 8, 1), Locale.US));
    }

    @Test
    public void findsDateInTenThousandEntryHistory() {
        final List<LocalDate> largeHistory = new java.util.ArrayList<>(10_000);
        final LocalDate newest = LocalDate.of(2026, 8, 23);
        for (int index = 0; index < 10_000; index++) {
            largeHistory.add(newest.minusDays(index));
        }

        assertEquals(7_654, HistoryDateNavigator.findClosestIndex(
                largeHistory, newest.minusDays(7_654)));
    }

    @Test
    public void findsDateThroughLazyAccessor() {
        final LocalDate newest = LocalDate.of(2026, 11, 15);
        assertEquals(10, HistoryDateNavigator.findClosestIndex(
                100, index -> newest.minusDays(index), newest.minusDays(10)));
    }
}
