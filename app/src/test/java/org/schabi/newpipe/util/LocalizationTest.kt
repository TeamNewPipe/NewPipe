package org.schabi.newpipe.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.ocpsoft.prettytime.PrettyTime
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.GregorianCalendar
import java.util.Locale

class LocalizationTest {

    @Test
    fun `After initializing pretty time relativeTime() with a Calendar must work`() {
        val reference = SimpleDateFormat("yyyy/MM/dd").parse("2021/1/1")
        Localization.initPrettyTime(PrettyTime(reference, Locale.ENGLISH))

        val actual = Localization.relativeTime(GregorianCalendar(2021, 1, 6))

        assertEquals("1 month from now", actual)
    }

    @Test(expected = NullPointerException::class)
    fun `relativeTime() must fail without initializing pretty time`() {
        Localization.relativeTime(GregorianCalendar(2021, 1, 6))
    }

    @Test
    fun `relativeTime() with a OffsetDateTime must work`() {
        val reference = SimpleDateFormat("yyyy/MM/dd").parse("2021/1/1")
        Localization.initPrettyTime(PrettyTime(reference, Locale.ENGLISH))

        val offset = OffsetDateTime.of(2021, 1, 6, 0, 0, 0, 0, ZoneOffset.UTC)
        val actual = Localization.relativeTime(offset)

        assertEquals("5 days from now", actual)
    }
}
