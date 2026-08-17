package org.schabi.newpipe.util.external_communication

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.schabi.newpipe.util.text.TimestampExtractor

@RunWith(Parameterized::class)
class TimestampExtractorTest(
    @Parameterized.Parameter(0) @JvmField var expected: Duration?,
    @Parameterized.Parameter(1) @JvmField var stringToProcess: String
) {

    @Test
    fun testExtract() {
        val m = TimestampExtractor.TIMESTAMPS_PATTERN.matcher(stringToProcess)

        if (!m.find()) {
            if (expected == null) {
                return
            }
            fail("No match found but expected one")
        }

        val timestampMatchDTO = TimestampExtractor.getTimestampFromMatcher(m, stringToProcess)

        if (timestampMatchDTO == null) {
            if (expected == null) {
                return
            }
            fail("Result shouldn't be null")
        } else if (expected == null) {
            assertNull("Expected that the dto is null, but it isn't", timestampMatchDTO)
            return
        }

        val actualSeconds = timestampMatchDTO.seconds()

        assertEquals(expected!!.seconds, actualSeconds.toLong())
    }

    companion object {
        @Parameterized.Parameters(name = "Expecting {0} for \"{1}\"")
        @JvmStatic
        fun dataForTests(): List<Array<Any?>> {
            return listOf(
                // Simple valid values
                arrayOf(Duration.ofSeconds(1), "0:01"),
                arrayOf(Duration.ofSeconds(1), "00:01"),
                arrayOf(Duration.ofSeconds(1), "0:00:01"),
                arrayOf(Duration.ofSeconds(1), "00:00:01"),
                arrayOf(Duration.ofMinutes(1).plusSeconds(23), "1:23"),
                arrayOf(Duration.ofMinutes(1).plusSeconds(23), "01:23"),
                arrayOf(Duration.ofMinutes(1).plusSeconds(23), "0:01:23"),
                arrayOf(Duration.ofMinutes(1).plusSeconds(23), "00:01:23"),
                arrayOf(Duration.ofHours(1).plusMinutes(23).plusSeconds(45), "1:23:45"),
                arrayOf(Duration.ofHours(1).plusMinutes(23).plusSeconds(45), "01:23:45"),
                // Check with additional text
                arrayOf(Duration.ofSeconds(1), "Wow 0:01 words"),
                arrayOf(Duration.ofMinutes(1).plusSeconds(23), "Wow 1:23 words"),
                arrayOf(Duration.ofSeconds(1), "Wow 0:01 words! 33:"),
                arrayOf(null, "Wow0:01 abc"),
                arrayOf(null, "Wow 0:01abc"),
                arrayOf(null, "Wow0:01abc"),
                arrayOf(null, "Wow0:01"),
                arrayOf(null, "0:01abc"),
                // Boundary checks
                arrayOf(Duration.ofSeconds(0), "0:00"),
                arrayOf(Duration.ofHours(59).plusMinutes(59).plusSeconds(59), "59:59:59"),
                arrayOf(null, "60:59:59"),
                arrayOf(null, "60:59"),
                arrayOf(null, "0:60"),
                // Format checks
                arrayOf(null, "000:0"),
                arrayOf(null, "123:01"),
                arrayOf(null, "123:123"),
                arrayOf(null, "2:123"),
                arrayOf(null, "2:3"),
                arrayOf(null, "1:2:3"),
                arrayOf(null, ":3"),
                arrayOf(null, "01:"),
                arrayOf(null, ":01"),
                arrayOf(null, "a:b:c"),
                arrayOf(null, "abc:def:ghj"),
                arrayOf(null, "::"),
                arrayOf(null, ":"),
                arrayOf(null, "")
            )
        }
    }
}
