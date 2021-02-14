package org.schabi.newpipe

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

class NewVersionManagerTest {

    private lateinit var manager: NewVersionManager

    @Before
    fun setup() {
        manager = NewVersionManager()
    }

    @Test
    fun `Expiry is reached`() {
        val oneHourEarlier = Instant.now().atZone(ZoneId.of("GMT")).minusHours(1)

        val expired = manager.isExpired(oneHourEarlier.toEpochSecond())

        assertTrue(expired)
    }

    @Test
    fun `Expiry is not reached`() {
        val oneHourLater = Instant.now().atZone(ZoneId.of("GMT")).plusHours(1)

        val expired = manager.isExpired(oneHourLater.toEpochSecond())

        assertFalse(expired)
    }

    /**
     * Equal within a range of 5 seconds
     */
    private fun assertNearlyEqual(a: Long, b: Long) {
        assertTrue(abs(a - b) < 5)
    }

    @Test
    fun `Expiry must be returned as is because it is inside the acceptable range of 6-72 hours`() {
        val sixHoursLater = Instant.now().atZone(ZoneId.of("GMT")).plusHours(6)

        val coerced = manager.coerceExpiry(DateTimeFormatter.RFC_1123_DATE_TIME.format(sixHoursLater))

        assertNearlyEqual(sixHoursLater.toEpochSecond(), coerced)
    }

    @Test
    fun `Expiry must be increased to 6 hours if below`() {
        val tooLow = Instant.now().atZone(ZoneId.of("GMT")).plusHours(5)

        val coerced = manager.coerceExpiry(DateTimeFormatter.RFC_1123_DATE_TIME.format(tooLow))

        assertNearlyEqual(tooLow.plusHours(1).toEpochSecond(), coerced)
    }

    @Test
    fun `Expiry must be decreased to 72 hours if above`() {
        val tooHigh = Instant.now().atZone(ZoneId.of("GMT")).plusHours(73)

        val coerced = manager.coerceExpiry(DateTimeFormatter.RFC_1123_DATE_TIME.format(tooHigh))

        assertNearlyEqual(tooHigh.minusHours(1).toEpochSecond(), coerced)
    }
}
