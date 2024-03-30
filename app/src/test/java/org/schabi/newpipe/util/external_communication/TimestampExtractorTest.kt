package org.schabi.newpipe.util.external_communication

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.schabi.newpipe.util.text.TimestampExtractor
import org.schabi.newpipe.util.text.TimestampExtractor.getTimestampFromMatcher
import java.time.Duration
import java.util.Arrays

@RunWith(Parameterized::class)
class TimestampExtractorTest {
    @Parameterized.Parameter(0)
    var expected: Duration? = null

    @Parameterized.Parameter(1)
    var stringToProcess: String? = null
    @Test
    fun testExtract() {
        val m = TimestampExtractor.TIMESTAMPS_PATTERN.matcher(stringToProcess)
        if (!m.find()) {
            if (expected == null) {
                return
            }
            Assert.fail("No match found but expected one")
        }
        val timestampMatchDTO = getTimestampFromMatcher(m, stringToProcess!!)
        if (timestampMatchDTO == null) {
            if (expected == null) {
                return
            }
            Assert.fail("Result shouldn't be null")
        } else if (expected == null) {
            Assert.assertNull("Expected that the dto is null, but it isn't", timestampMatchDTO)
            return
        }
        val actualSeconds = timestampMatchDTO!!.seconds()
        Assert.assertEquals(expected!!.seconds, actualSeconds.toLong())
    }

    companion object {
        @Parameterized.Parameters(name = "Expecting {0} for \"{1}\"")
        fun dataForTests(): List<Array<Any>> {
            return Arrays.asList<Array<Any>>(*arrayOf(arrayOf<Any?>(Duration.ofSeconds(1), "0:01"), arrayOf<Any?>(Duration.ofSeconds(1), "00:01"), arrayOf<Any?>(Duration.ofSeconds(1), "0:00:01"), arrayOf<Any?>(Duration.ofSeconds(1), "00:00:01"), arrayOf<Any?>(Duration.ofMinutes(1).plusSeconds(23), "1:23"), arrayOf<Any?>(Duration.ofMinutes(1).plusSeconds(23), "01:23"), arrayOf<Any?>(Duration.ofMinutes(1).plusSeconds(23), "0:01:23"), arrayOf<Any?>(Duration.ofMinutes(1).plusSeconds(23), "00:01:23"), arrayOf<Any?>(Duration.ofHours(1).plusMinutes(23).plusSeconds(45), "1:23:45"), arrayOf<Any?>(Duration.ofHours(1).plusMinutes(23).plusSeconds(45), "01:23:45"), arrayOf<Any?>(Duration.ofSeconds(1), "Wow 0:01 words"), arrayOf<Any?>(Duration.ofMinutes(1).plusSeconds(23), "Wow 1:23 words"), arrayOf<Any?>(Duration.ofSeconds(1), "Wow 0:01 words! 33:"), arrayOf<Any?>(null, "Wow0:01 abc"), arrayOf<Any?>(null, "Wow 0:01abc"), arrayOf<Any?>(null, "Wow0:01abc"), arrayOf<Any?>(null, "Wow0:01"), arrayOf<Any?>(null, "0:01abc"), arrayOf<Any?>(Duration.ofSeconds(0), "0:00"), arrayOf<Any?>(Duration.ofHours(59).plusMinutes(59).plusSeconds(59), "59:59:59"), arrayOf<Any?>(null, "60:59:59"), arrayOf<Any?>(null, "60:59"), arrayOf<Any?>(null, "0:60"), arrayOf<Any?>(null, "000:0"), arrayOf<Any?>(null, "123:01"), arrayOf<Any?>(null, "123:123"), arrayOf<Any?>(null, "2:123"), arrayOf<Any?>(null, "2:3"), arrayOf<Any?>(null, "1:2:3"), arrayOf<Any?>(null, ":3"), arrayOf<Any?>(null, "01:"), arrayOf<Any?>(null, ":01"), arrayOf<Any?>(null, "a:b:c"), arrayOf<Any?>(null, "abc:def:ghj"), arrayOf<Any?>(null, "::"), arrayOf<Any?>(null, ":"), arrayOf<Any?>(null, "")))
        }
    }
}
