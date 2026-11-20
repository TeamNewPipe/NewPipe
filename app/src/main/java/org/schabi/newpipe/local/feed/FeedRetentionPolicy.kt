package org.schabi.newpipe.local.feed

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** A finite date range for the active feed; archived feed items are bounded separately. */
object FeedRetentionPolicy {
    private const val DEFAULT_WEEKS = 13L
    private const val EXTENDED_WEEKS = 26L

    @JvmStatic
    fun cutoff(todayUtc: LocalDate, extendedHistory: Boolean): OffsetDateTime {
        return todayUtc.minusWeeks(if (extendedHistory) EXTENDED_WEEKS else DEFAULT_WEEKS)
            .atStartOfDay().atOffset(ZoneOffset.UTC)
    }
}
