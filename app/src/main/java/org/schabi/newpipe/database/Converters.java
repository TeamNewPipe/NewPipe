package org.schabi.newpipe.database;

import androidx.room.TypeConverter;

import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.local.subscription.FeedGroupIcon;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class Converters {
    private Converters() { }

    /**
     * Convert a long value to a {@link OffsetDateTime}.
     *
     * @param value the long value
     * @return the {@code OffsetDateTime}
     */
    @TypeConverter
    public static OffsetDateTime offsetDateTimeFromTimestamp(final Long value) {
        return value == null ? null : OffsetDateTime.ofInstant(Instant.ofEpochMilli(value),
                ZoneOffset.UTC);
    }

    /**
     * Convert a {@link OffsetDateTime} to a long value.
     *
     * @param offsetDateTime the {@code OffsetDateTime}
     * @return the long value
     */
    @TypeConverter
    public static Long offsetDateTimeToTimestamp(final OffsetDateTime offsetDateTime) {
        return offsetDateTime == null ? null : offsetDateTime.withOffsetSameInstant(ZoneOffset.UTC)
                .toInstant().toEpochMilli();
    }

    @TypeConverter
    public static StreamType streamTypeOf(final String value) {
        return StreamType.valueOf(value);
    }

    @TypeConverter
    public static String stringOf(final StreamType streamType) {
        return streamType.name();
    }

    @TypeConverter
    public static Integer integerOf(final FeedGroupIcon feedGroupIcon) {
        return feedGroupIcon.getId();
    }

    @TypeConverter
    public static FeedGroupIcon feedGroupIconOf(final Integer id) {
        for (final FeedGroupIcon icon : FeedGroupIcon.values()) {
            if (icon.getId() == id) {
                return icon;
            }
        }

        throw new IllegalArgumentException("There's no feed group icon with the id \"" + id + "\"");
    }
}
