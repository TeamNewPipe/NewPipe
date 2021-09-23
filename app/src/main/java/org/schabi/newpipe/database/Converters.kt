package org.schabi.newpipe.database

import androidx.room.TypeConverter
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.local.subscription.FeedGroupIcon
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

object Converters {
    /**
     * Convert a long value to a [OffsetDateTime].
     *
     * @param value the long value
     * @return the `OffsetDateTime`
     */
    @TypeConverter
    fun offsetDateTimeFromTimestamp(value: Long?): OffsetDateTime? {
        return value?.let { OffsetDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC) }
    }

    /**
     * Convert a [OffsetDateTime] to a long value.
     *
     * @param offsetDateTime the `OffsetDateTime`
     * @return the long value
     */
    @TypeConverter
    fun offsetDateTimeToTimestamp(offsetDateTime: OffsetDateTime?): Long? {
        return offsetDateTime?.withOffsetSameInstant(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    }

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
        return FeedGroupIcon.values().first { it.id == id }
    }
}
