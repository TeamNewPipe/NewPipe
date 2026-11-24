package org.schabi.newpipe.util

import android.content.Context
import android.text.format.DateUtils
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** Uses provider-supplied scheduled times, never estimates a start from upload age. */
object UpcomingStreamCountdown {
    @JvmStatic
    @JvmOverloads
    fun label(
        context: Context,
        stream: StreamInfoItem,
        nowMillis: Long = System.currentTimeMillis()
    ): String? {
        val futureStart = futureStartMillis(stream, nowMillis) ?: return null
        val relative = DateUtils.getRelativeTimeSpanString(
            futureStart,
            nowMillis,
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
        return context.getString(R.string.upcoming_stream_starts, relative)
    }

    internal fun futureStartMillis(stream: StreamInfoItem, nowMillis: Long): Long? {
        if (stream.upcomingStatus == false) return null
        val start = stream.startAt.takeIf { it > 0 }?.let {
            if (it < 10_000_000_000L) it * 1_000L else it
        } ?: stream.uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli()
        return start?.takeIf { it > nowMillis }
    }
}
