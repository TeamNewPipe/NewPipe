package org.schabi.newpipe.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.ocpsoft.prettytime.PrettyTime
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.util.Locale

class LocalizationTest {
    @Test(expected = NullPointerException::class)
    fun `formatRelativeTime() must fail without initializing pretty time`() {
        Localization.resetPrettyTime()
        Localization.formatRelativeTime(Instant.now())
    }

    @Test
    fun `formatRelativeTime() with an Instant must work`() {
        val zoneId = ZoneId.systemDefault()
        val date = LocalDate.of(2021, Month.JANUARY, 1)
        val prettyTime = PrettyTime(date, zoneId)
        prettyTime.locale = Locale.ENGLISH
        Localization.initPrettyTime(prettyTime)

        val instant = date.plusDays(5).atStartOfDay(zoneId).toInstant()
        val actual = Localization.formatRelativeTime(instant)

        assertEquals("5 days from now", actual)
    }
}
