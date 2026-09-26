package org.schabi.newpipe.util.subtitle;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class SubtitleDeduplicatorTest {

    @Test
    public void deduplicateExactDuplicateEntriesShouldRemoveDuplicate() {
        final String input =
            "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">Hello</p>\n"
            + "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">Hello</p>";

        final String output = SubtitleDeduplicator.deduplicateContent(input);

        final String expected =
            "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">Hello</p>";

        // The `strip()` method is used here to remove the trailing
        // newline character (\n, outside of <p> tags) at the end of the `output`.
        // Removing this (\n) does not affect the TTML subtitle paragraphs,
        // as only the content within <p> tags is considered valid for subtitles.
        assertEquals(expected, output.strip());
    }

    @Test
    public void deduplicateSameTimeDifferentTextShouldNotDeduplicate() {
        final String input =
            "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">Hello</p>\n"
            + "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">World</p>";

        final String output = SubtitleDeduplicator.deduplicateContent(input);

        final String expected = input;

        assertEquals(expected, output);
    }

    @Test
    public void deduplicateSameTextDifferentTimeShouldNotDeduplicate() {
        final String input =
            "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">Hello</p>\n"
            + "<p begin=\"00:00:02.000\" end=\"00:00:03.000\">Hello</p>";

        final String output = SubtitleDeduplicator.deduplicateContent(input);

        final String expected = input;

        assertEquals(expected, output);
    }

    @Test
    public void containsDuplicatedEntriesExactDuplicateShouldReturnTrue() {
        final String input =
            "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">Hello</p>\n"
            + "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">Hello</p>";

        assertTrue(SubtitleDeduplicator.containsDuplicatedEntries(input));
    }

    @Test
    public void containsDuplicatedEntriesNoDuplicateShouldReturnFalse() {
        final String input =
            "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">Hello</p>\n"
            + "<p begin=\"00:00:02.000\" end=\"00:00:03.000\">World</p>";

        assertFalse(SubtitleDeduplicator.containsDuplicatedEntries(input));
    }

    @Test
    public void containsDuplicatesNormalizeLeadingAndTrailingWhitespaceShouldConsiderAsSame() {
        // Note:
        // This test verifies that the deduplication logic normalizes
        // leading and trailing whitespace, and considers the content
        // as the same after this normalization, without modifying
        // the original subtitle content.
        final String input =
            "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">  Hello world  </p>\n"
            + "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">Hello world</p>";
        assertTrue(SubtitleDeduplicator.containsDuplicatedEntries(input));
    }

    @Test
    public void containsDuplicatedEntriesNormalizeMultipleSpacesShouldConsiderAsSingleSpace() {
        // Note:
        // This test verifies that the deduplication logic normalizes
        // multiple consecutive spaces into a single space,
        // considering the content as the same after this normalization,
        // without modifying the original subtitle content.
        final String input =
            "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">Hello    world</p>\n"
            + "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">Hello world</p>";
        assertTrue(SubtitleDeduplicator.containsDuplicatedEntries(input));
    }
}
