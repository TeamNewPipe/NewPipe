package org.schabi.newpipe.database

import androidx.room.TypeConverter
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.subscription.FeedGroupIcon
import java.time.Instant

class Converters {
    /**
     * Convert a long value to an [Instant].
     *
     * @param value the long value
     * @return the `Instant`
     */
    @TypeConverter
    fun timestampToInstant(value: Long?): Instant? {
        return value?.let { Instant.ofEpochMilli(it) }
    }

    /**
     * Convert an [Instant] to a long value.
     *
     * @param instant the `Instant`
     * @return the long value
     */
    @TypeConverter
    fun instantToTimestamp(instant: Instant?) = instant?.toEpochMilli()

    @TypeConverter
    fun streamTypeOf(value: String): StreamType {
        return StreamType.valueOf(value)
    }

    @TypeConverter
    fun stringOf(streamType: StreamType): String {
        return streamType.name
    }

    @TypeConverter
    fun integerOf(feedGroupIcon: FeedGroupIcon): Int {
        return feedGroupIcon.id
    }

    @TypeConverter
    fun feedGroupIconOf(id: Int): FeedGroupIcon {
        return FeedGroupIcon.entries.first { it.id == id }
    }
}
