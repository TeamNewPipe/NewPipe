package org.schabi.newpipe.database;

import android.arch.persistence.room.TypeConverter;

import org.schabi.newpipe.extractor.stream.StreamType;

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
}
