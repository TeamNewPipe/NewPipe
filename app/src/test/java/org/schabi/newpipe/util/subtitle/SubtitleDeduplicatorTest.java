package org.schabi.newpipe.extractor.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class SubtitleDeduplicatorTest {

    @Test
    public void deduplicate_exactDuplicateEntries_shouldRemoveDuplicate() {
        String input =
            "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">Hello</p>\n" +
            "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">Hello</p>";

        String output = SubtitleDeduplicator.deduplicateContent(input);

        String expected =
            "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">Hello</p>";

        // The `strip()` method is used here to remove the trailing
        // newline character (\n, outside of <p> tags) at the end of the `output`.
        // Removing this (\n) does not affect the TTML subtitle paragraphs,
        // as only the content within <p> tags is considered valid for subtitles.
        assertEquals(expected, output.strip());
    }

    @Test
    public void deduplicate_sameTimeDifferentText_shouldNotDeduplicate() {
        String input =
            "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">Hello</p>\n" +
            "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">World</p>";

        String output = SubtitleDeduplicator.deduplicateContent(input);

        String expected = input;

        assertEquals(expected, output);
    }

    @Test
    public void deduplicate_sameTextDifferentTime_shouldNotDeduplicate() {
        String input =
            "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">Hello</p>\n" +
            "<p begin=\"00:00:02.000\" end=\"00:00:03.000\">Hello</p>";

        String output = SubtitleDeduplicator.deduplicateContent(input);

        String expected = input;

        assertEquals(expected, output);
    }

    @Test
    public void containsDuplicatedEntries_exactDuplicate_shouldReturnTrue() {
        String input =
            "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">Hello</p>\n" +
            "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">Hello</p>";

        assertTrue(SubtitleDeduplicator.containsDuplicatedEntries(input));
    }

    @Test
    public void containsDuplicatedEntries_noDuplicate_shouldReturnFalse() {
        String input =
            "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">Hello</p>\n" +
            "<p begin=\"00:00:02.000\" end=\"00:00:03.000\">World</p>";

        assertFalse(SubtitleDeduplicator.containsDuplicatedEntries(input));
    }

    @Test
    public void containsDuplicatedEntries_normalizeLeadingAndTrailingWhitespace_shouldConsiderAsSame() {
        // Note:
        // This test verifies that the deduplication logic normalizes
        // leading and trailing whitespace, and considers the content
        // as the same after this normalization, without modifying
        // the original subtitle content.
        String input =
            "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">  Hello world  </p>\n" +
            "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">Hello world</p>";
        assertTrue(SubtitleDeduplicator.containsDuplicatedEntries(input));
    }

    @Test
    public void containsDuplicatedEntries_normalizeMultipleSpaces_shouldConsiderAsSingleSpace() {
        // Note:
        // This test verifies that the deduplication logic normalizes
        // multiple consecutive spaces into a single space,
        // considering the content as the same after this normalization,
        // without modifying the original subtitle content.
        String input =
            "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">Hello    world</p>\n" +
            "<p begin=\"00:00:01.000\" end=\"00:00:02.000\">Hello world</p>";
        assertTrue(SubtitleDeduplicator.containsDuplicatedEntries(input));
    }
}
