package org.schabi.newpipe

import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class NewVersionManager {

    fun isExpired(expiry: Long): Boolean {
        return Instant.ofEpochSecond(expiry).isBefore(Instant.now())
    }

    /**
     * Coerce expiry date time in between 6 hours and 72 hours from now
     *
     * @return Epoch second of expiry date time
     */
    fun coerceExpiry(expiryString: String?): Long {
        val now = ZonedDateTime.now()
        return expiryString?.let {

            var expiry = ZonedDateTime.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(expiryString))
            expiry = maxOf(expiry, now.plusHours(6))
            expiry = minOf(expiry, now.plusHours(72))
            expiry.toEpochSecond()
        } ?: now.plusHours(6).toEpochSecond()
    }
}
