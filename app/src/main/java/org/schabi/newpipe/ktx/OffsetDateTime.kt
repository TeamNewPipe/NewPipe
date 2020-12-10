package org.schabi.newpipe.ktx

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoField
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.TimeZone

// This method is a modified version of GregorianCalendar.from(ZonedDateTime).
// Math.addExact() and Math.multiplyExact() are desugared even though lint displays a warning.
@SuppressWarnings("NewApi")
fun OffsetDateTime.toCalendar(): Calendar {
    val cal = GregorianCalendar(TimeZone.getTimeZone("UTC"))
    val offsetDateTimeUTC = withOffsetSameInstant(ZoneOffset.UTC)
    cal.gregorianChange = Date(Long.MIN_VALUE)
    cal.firstDayOfWeek = Calendar.MONDAY
    cal.minimalDaysInFirstWeek = 4
    try {
        cal.timeInMillis = Math.addExact(
            Math.multiplyExact(offsetDateTimeUTC.toEpochSecond(), 1000),
            offsetDateTimeUTC[ChronoField.MILLI_OF_SECOND].toLong()
        )
    } catch (ex: ArithmeticException) {
        throw IllegalArgumentException(ex)
    }
    return cal
}
