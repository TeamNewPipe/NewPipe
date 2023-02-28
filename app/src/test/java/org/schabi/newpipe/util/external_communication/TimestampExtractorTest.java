package org.schabi.newpipe.util.external_communication;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.schabi.newpipe.util.text.TimestampExtractor;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class TimestampExtractorTest {

    @Parameterized.Parameter(0)
    public Duration expected;

    @Parameterized.Parameter(1)
    public String stringToProcess;

    @Parameterized.Parameters(name = "Expecting {0} for \"{1}\"")
    public static List<Object[]> dataForTests() {
        return Arrays.asList(new Object[][]{
                // Simple valid values
                {Duration.ofSeconds(1), "0:01"},
                {Duration.ofSeconds(1), "00:01"},
                {Duration.ofSeconds(1), "0:00:01"},
                {Duration.ofSeconds(1), "00:00:01"},
                {Duration.ofMinutes(1).plusSeconds(23), "1:23"},
                {Duration.ofMinutes(1).plusSeconds(23), "01:23"},
                {Duration.ofMinutes(1).plusSeconds(23), "0:01:23"},
                {Duration.ofMinutes(1).plusSeconds(23), "00:01:23"},
                {Duration.ofHours(1).plusMinutes(23).plusSeconds(45), "1:23:45"},
                {Duration.ofHours(1).plusMinutes(23).plusSeconds(45), "01:23:45"},
                // Check with additional text
                {Duration.ofSeconds(1), "Wow 0:01 words"},
                {Duration.ofMinutes(1).plusSeconds(23), "Wow 1:23 words"},
                {Duration.ofSeconds(1), "Wow 0:01 words! 33:"},
                {null, "Wow0:01 abc"},
                {null, "Wow 0:01abc"},
                {null, "Wow0:01abc"},
                {null, "Wow0:01"},
                {null, "0:01abc"},
                // Boundary checks
                {Duration.ofSeconds(0), "0:00"},
                {Duration.ofHours(59).plusMinutes(59).plusSeconds(59), "59:59:59"},
                {null, "60:59:59"},
                {null, "60:59"},
                {null, "0:60"},
                // Format checks
                {null, "000:0"},
                {null, "123:01"},
                {null, "123:123"},
                {null, "2:123"},
                {null, "2:3"},
                {null, "1:2:3"},
                {null, ":3"},
                {null, "01:"},
                {null, ":01"},
                {null, "a:b:c"},
                {null, "abc:def:ghj"},
                {null, "::"},
                {null, ":"},
                {null, ""}
        });
    }

    @Test
    public void testExtract() {
        final Matcher m = TimestampExtractor.TIMESTAMPS_PATTERN.matcher(this.stringToProcess);

        if (!m.find()) {
            if (expected == null) {
                return;
            }
            fail("No match found but expected one");
        }

        final TimestampExtractor.TimestampMatchDTO timestampMatchDTO =
                TimestampExtractor
                        .getTimestampFromMatcher(m, this.stringToProcess);

        if (timestampMatchDTO == null) {
            if (expected == null) {
                return;
            }
            fail("Result shouldn't be null");
        } else if (expected == null) {
            assertNull("Expected that the dto is null, but it isn't", timestampMatchDTO);
            return;
        }

        final int actualSeconds = timestampMatchDTO.seconds();

        assertEquals(expected.getSeconds(), actualSeconds);
    }
}
