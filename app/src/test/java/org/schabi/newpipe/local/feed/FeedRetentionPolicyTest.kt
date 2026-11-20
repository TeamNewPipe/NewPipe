package org.schabi.newpipe.local.feed

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedRetentionPolicyTest {
    private val today = LocalDate.of(2026, 11, 20)

    @Test
    fun defaultFeedRetentionRemainsThirteenWeeks() {
        assertEquals(
            today.minusWeeks(13).atStartOfDay().atOffset(ZoneOffset.UTC),
            FeedRetentionPolicy.cutoff(today, false)
        )
    }

    @Test
    fun optInFeedRetentionCanKeepSixMonthsOfDatedUploads() {
        assertEquals(
            today.minusWeeks(26).atStartOfDay().atOffset(ZoneOffset.UTC),
            FeedRetentionPolicy.cutoff(today, true)
        )
        assertTrue(
            FeedRetentionPolicy.cutoff(today, true)
                .isBefore(FeedRetentionPolicy.cutoff(today, false))
        )
    }

    @Test
    fun cutoffIsComputedFromTheSuppliedDayNotAnAppStartupSingleton() {
        val nextDay = today.plusDays(1)
        assertEquals(
            FeedRetentionPolicy.cutoff(today, true).plusDays(1),
            FeedRetentionPolicy.cutoff(nextDay, true)
        )
    }
}
