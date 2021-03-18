package org.schabi.newpipe.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.ocpsoft.prettytime.PrettyTime
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Locale

class LocalizationTest {
    @Test(expected = NullPointerException::class)
    fun `relativeTime() must fail without initializing pretty time`() {
        Localization.relativeTime(OffsetDateTime.of(2021, 1, 6, 0, 0, 0, 0, ZoneOffset.UTC))
    }

    @Test
    fun `relativeTime() with a OffsetDateTime must work`() {
        val prettyTime = PrettyTime(LocalDate.of(2021, 1, 1), ZoneOffset.UTC)
        prettyTime.locale = Locale.ENGLISH
        Localization.initPrettyTime(prettyTime)

        val offset = OffsetDateTime.of(2021, 1, 6, 0, 0, 0, 0, ZoneOffset.UTC)
        val actual = Localization.relativeTime(offset)

        assertEquals("5 days from now", actual)
    }
}
