package org.schabi.newpipe.util.text;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts timestamps.
 */
public final class TimestampExtractor {
    public static final Pattern TIMESTAMPS_PATTERN = Pattern.compile(
            "(?:^|(?!:)\\W)(?:([0-5]?[0-9]):)?([0-5]?[0-9]):([0-5][0-9])(?=$|(?!:)\\W)");

    private TimestampExtractor() {
        // No impl pls
    }

    /**
     * Gets a single timestamp from a matcher.
     *
     * @param timestampMatches the matcher which was created using {@link #TIMESTAMPS_PATTERN}
     * @param baseText         the text where the pattern was applied to / where the matcher is
     *                         based upon
     * @return if a match occurred, a {@link TimestampMatchDTO} filled with information, otherwise
     * {@code null}.
     */
    @Nullable
    public static TimestampMatchDTO getTimestampFromMatcher(
            @NonNull final Matcher timestampMatches,
            @NonNull final String baseText) {
        int timestampStart = timestampMatches.start(1);
        if (timestampStart == -1) {
            timestampStart = timestampMatches.start(2);
        }
        final int timestampEnd = timestampMatches.end(3);

        final String parsedTimestamp = baseText.substring(timestampStart, timestampEnd);
        final String[] timestampParts = parsedTimestamp.split(":");

        final int seconds;
        if (timestampParts.length == 3) { // timestamp format: XX:XX:XX
            seconds = Integer.parseInt(timestampParts[0]) * 3600 // hours
                    + Integer.parseInt(timestampParts[1]) * 60 // minutes
                    + Integer.parseInt(timestampParts[2]); // seconds
        } else if (timestampParts.length == 2) { // timestamp format: XX:XX
            seconds = Integer.parseInt(timestampParts[0]) * 60 // minutes
                    + Integer.parseInt(timestampParts[1]); // seconds
        } else {
            return null;
        }

        return new TimestampMatchDTO(timestampStart, timestampEnd, seconds);
    }

    public static class TimestampMatchDTO {
        private final int timestampStart;
        private final int timestampEnd;
        private final int seconds;

        public TimestampMatchDTO(
                final int timestampStart,
                final int timestampEnd,
                final int seconds) {
            this.timestampStart = timestampStart;
            this.timestampEnd = timestampEnd;
            this.seconds = seconds;
        }

        public int timestampStart() {
            return timestampStart;
        }

        public int timestampEnd() {
            return timestampEnd;
        }

        public int seconds() {
            return seconds;
        }
    }
}
