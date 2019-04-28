package org.schabi.newpipe.database;

import androidx.room.TypeConverter;

import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.local.subscription.FeedGroupIcon;

import java.util.Date;

public class Converters {

    /**
     * Convert a long value to a date
     * @param value the long value
     * @return the date
     */
    @TypeConverter
    public static Date fromTimestamp(Long value) {
        return value == null ? null : new Date(value);
    }

    /**
     * Convert a date to a long value
     * @param date the date
     * @return the long value
     */
    @TypeConverter
    public static Long dateToTimestamp(Date date) {
        return date == null ? null : date.getTime();
    }

    @TypeConverter
    public static StreamType streamTypeOf(String value) {
        return StreamType.valueOf(value);
    }

    @TypeConverter
    public static String stringOf(StreamType streamType) {
        return streamType.name();
    }

    @TypeConverter
    public static Integer integerOf(FeedGroupIcon feedGroupIcon) {
        return feedGroupIcon.getId();
    }

    @TypeConverter
    public static FeedGroupIcon feedGroupIconOf(Integer id) {
        for (FeedGroupIcon icon : FeedGroupIcon.values()) {
            if (icon.getId() == id) return icon;
        }

        throw new IllegalArgumentException("There's no feed group icon with the id \"" + id + "\"");
    }
}
