package org.schabi.newpipe.ktx

import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Calendar
import java.util.GregorianCalendar

fun OffsetDateTime.toCalendar(zoneId: ZoneId = ZoneId.systemDefault()): Calendar {
    return GregorianCalendar.from(if (zoneId != offset) atZoneSameInstant(zoneId) else toZonedDateTime())
}
