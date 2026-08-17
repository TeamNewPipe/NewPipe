package org.schabi.newpipe.util.text

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Extracts timestamps.
 */
object TimestampExtractor {
    @JvmField
    val TIMESTAMPS_PATTERN: Pattern = Pattern.compile(
        "(?:^|(?!:)\\W)(?:([0-5]?[0-9]):)?([0-5]?[0-9]):([0-5][0-9])(?=$|(?!:)\\W)"
    )

    /**
     * Gets a single timestamp from a matcher.
     *
     * @param timestampMatches the matcher which was created using [TIMESTAMPS_PATTERN]
     * @param baseText         the text where the pattern was applied to / where the matcher is
     * based upon
     * @return if a match occurred, a [TimestampMatchDTO] filled with information, otherwise
     * `null`.
     */
    @JvmStatic
    fun getTimestampFromMatcher(
        timestampMatches: Matcher,
        baseText: String
    ): TimestampMatchDTO? {
        var timestampStart = timestampMatches.start(1)
        if (timestampStart == -1) {
            timestampStart = timestampMatches.start(2)
        }
        val timestampEnd = timestampMatches.end(3)

        val parsedTimestamp = baseText.substring(timestampStart, timestampEnd)
        val timestampParts = parsedTimestamp.split(":")

        val seconds: Int = when (timestampParts.size) {
            3 -> { // timestamp format: XX:XX:XX
                timestampParts[0].toInt() * 3600 + // hours
                    timestampParts[1].toInt() * 60 + // minutes
                    timestampParts[2].toInt() // seconds
            }
            2 -> { // timestamp format: XX:XX
                timestampParts[0].toInt() * 60 + // minutes
                    timestampParts[1].toInt() // seconds
            }
            else -> return null
        }

        return TimestampMatchDTO(timestampStart, timestampEnd, seconds)
    }

    data class TimestampMatchDTO(val timestampStart: Int, val timestampEnd: Int, val seconds: Int)
}
