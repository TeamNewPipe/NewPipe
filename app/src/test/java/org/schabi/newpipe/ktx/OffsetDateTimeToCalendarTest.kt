package org.schabi.newpipe.ktx

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Calendar
import java.util.TimeZone

class OffsetDateTimeToCalendarTest {
    @Test
    fun testRelativeTimeWithCurrentOffsetDateTime() {
        val calendar = LocalDate.of(2020, 1, 1).atStartOfDay().atOffset(ZoneOffset.UTC).toCalendar()

        assertEquals(2020, calendar[Calendar.YEAR])
        assertEquals(0, calendar[Calendar.MONTH])
        assertEquals(1, calendar[Calendar.DAY_OF_MONTH])
        assertEquals(0, calendar[Calendar.HOUR])
        assertEquals(0, calendar[Calendar.MINUTE])
        assertEquals(0, calendar[Calendar.SECOND])
        assertEquals(TimeZone.getTimeZone("UTC"), calendar.timeZone)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRelativeTimeWithFarOffOffsetDateTime() {
        OffsetDateTime.MAX.minusYears(1).toCalendar()
    }
}
