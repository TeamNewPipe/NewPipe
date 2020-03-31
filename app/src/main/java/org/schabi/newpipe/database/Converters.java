package org.schabi.newpipe.database;

import androidx.room.TypeConverter;

import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.local.subscription.FeedGroupIcon;

import java.util.Date;

public final class Converters {
    private Converters() { }

    /**
     * Convert a long value to a date.
     *
     * @param value the long value
     * @return the date
     */
    @TypeConverter
    public static Date fromTimestamp(final Long value) {
        return value == null ? null : new Date(value);
    }

    /**
     * Convert a date to a long value.
     *
     * @param date the date
     * @return the long value
     */
    @TypeConverter
    public static Long dateToTimestamp(final Date date) {
        return date == null ? null : date.getTime();
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
        for (FeedGroupIcon icon : FeedGroupIcon.values()) {
            if (icon.getId() == id) {
                return icon;
            }
        }

        throw new IllegalArgumentException("There's no feed group icon with the id \"" + id + "\"");
    }
}
