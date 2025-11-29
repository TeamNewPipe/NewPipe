package org.schabi.newpipe.streams;

import org.junit.Test;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.parser.Parser;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.assertEquals;

/**
 * Unit tests for {@link SrtFromTtmlWriter}.
 *
 * Tests focus on {@code extractText()} and its handling of TTML <p> elements.
 * Note:
 * - Uses reflection to call the private {@code extractText()} method.
 * - Update {@code EXTRACT_TEXT_METHOD} if renamed.
 *
 * ---
 * NOTE ABOUT ENTITIES VS UNICODE ESCAPES
 *
 * - In short:
 *   * UNICODE ESCAPES → used in Java source (e.g. SrtFromTtmlWriter.java)
 *   * ENTITIES → used in TTML strings (this test file)
 *
 * - TTML is an XML-based format. Real TTML subtitles often encode special
 *   characters as XML entities (named or numeric), e.g.:
 *       &amp;    → '&' (\u0026)
 *       &lt;     → '<' (\u003C)
 *       &#x9;    → tab (\u0009)
 *       &#xA;    → line feed (\u000A)
 *       &#xD;    → carriage return (\u000D)
 *
 * - Java source code uses **Unicode escapes** (e.g. "\u00A0") which are resolved
 *   at compile time, so they do not represent real XML entities.
 *
 * - Purpose of these tests:
 *   We simulate *real TTML input* as NewPipe receives it — i.e., strings that
 *   still contain encoded XML entities (&#x9;, &#xA;, &#xD;, etc.).
 *   The production code (`decodeXmlEntities()`) must convert these into their
 *   actual Unicode characters before normalization.
 */
public class SrtFromTtmlWriterTest {
    private static final String TTML_WRAPPER_START = "<tt><body><div>";
    private static final String TTML_WRAPPER_END = "</div></body></tt>";
    private static final String EXTRACT_TEXT_METHOD = "extractText";
    // Please keep the same definition from `SrtFromTtmlWriter` class.
    private static final String NEW_LINE = "\r\n";

    /*
     * TTML example for simple paragraph <p> without nested tags.
     * <p begin="00:00:01.000" end="00:00:03.000" style="s2">Hello World!</p>
     */
    private static final String SIMPLE_TTML = "<p begin=\"00:00:01.000\" end=\"00:00:03.000\" "
            + "style=\"s2\">Hello World!</p>";
    /**
     * TTML example with nested tags with <br>.
     * <p begin="00:00:01.000" end="00:00:03.000"><span style="s4">Hello</span><br>World!</p>
     */
    private static final String NESTED_TTML = "<p begin=\"00:00:01.000\" end=\"00:00:03.000\">"
            + "<span style=\"s4\">Hello</span><br>World!</p>";

    /**
     * TTML example with HTML entities.
     * &lt; → <, &gt; → >, &amp; → &, &quot; → ", &apos; → '
     * &#39; → '
     * &#xA0; → ' '
     */
    private static final String ENTITY_TTML = "<p begin=\"00:00:05.000\" "
            + "end=\"00:00:07.000\">"
            + "&lt;tag&gt; &amp; &quot;text&quot;&apos;&apos;&#39;&#39;"
            + "&#xA0;&#xA0;"
            + "</p>";
    /**
     * TTML example with special characters:
     * - Spaces appear at the beginning and end of the text.
     * - Spaces are also present within the text (not just at the edges).
     * - The text includes various HTML entities such as &nbsp;,
     *   &amp;, &lt;, &gt;, etc.
     * &nbsp; → non-breaking space (Unicode: '\u00A0', Entity: '&#xA0;')
     */
    private static final String SPECIAL_TTML = "<p begin=\"00:00:05.000\" end=\"00:00:07.000\">"
            + "   ～~-Hello&nbsp;&nbsp;&amp;&amp;&lt;&lt;&gt;&gt;World!!   "
            + "</p>";

    /**
     * TTML example with characters: tab.
     * &#x9; → \t
     * They are separated by '+' for clarity.
     */
    private static final String TAB_TTML = "<p begin=\"00:00:05.000\" "
            + "end=\"00:00:07.000\">"
            + "&#x9;&#x9;+&#x9;&#x9;+&#x9;&#x9;"
            + "</p>";

    /**
     * TTML example with line endings.
     * &#xD; → \r
     */
    private static final String LINE_ENDING_0_TTML = "<p begin=\"00:00:05.000\" "
            + "end=\"00:00:07.000\">"
            + "&#xD;&#xD;+&#xD;&#xD;+&#xD;&#xD;"
            + "</p>";
    // &#xA; → \n
    private static final String LINE_ENDING_1_TTML = "<p begin=\"00:00:05.000\" "
            + "end=\"00:00:07.000\">"
            + "&#xA;&#xA;+&#xA;&#xA;+&#xA;&#xA;"
            + "</p>";
    private static final String LINE_ENDING_2_TTML =
            "<p begin=\"00:00:05.000\" end=\"00:00:07.000\">"
            + "&#xD;&#xA;+&#xD;&#xA;+&#xD;&#xA;"
            + "</p>";

    /**
     * TTML example with control characters.
     * For example:
     * &#x0001; → \u0001
     * &#x001F; → \u001F
     *
     * These control characters, if included as raw Unicode(e.g. '\u0001'),
     * are either invalid in XML or rendered as '?' when processed.
     * To avoid issues, they should be encoded(e.g. '&#x0001;') in TTML file.
     *
     * - Reference:
     *   Unicode Basic Latin (https://unicode.org/charts/PDF/U0000.pdf),
     *   ASCII Control (https://en.wikipedia.org/wiki/ASCII#Control_characters).
     *   and the defination of these characters can be known.
     */
    private static final String CONTROL_CHAR_TTML = "<p begin=\"00:00:05.000\" "
            + "end=\"00:00:07.000\">"
            + "&#x0001;+&#x0008;+&#x000B;+&#x000C;+&#x000E;+&#x001F;"
            + "</p>";



    private static final String EMPTY_TTML = "<p begin=\"00:00:01.000\" "
            + "end=\"00:00:03.000\">"
            + ""
            + "</p>";

    /**
     * TTML example with Unicode space characters.
     * These characters are encoded using character references
     * (&#xXXXX;).
     *
     * Includes:
     * (&#x202F;) '\u202F' → Narrow no-break space
     * (&#x205F;) '\u205F' → Medium mathematical space
     * (&#x3000;) '\u3000' → Ideographic space
     * '\u2000' ~ '\u200A' are whitespace characters:
     * (&#x2000;) '\u2000' → En quad
     * (&#x2002;) '\u2002' → En space
     * (&#x200A;) '\u200A' → Hair space
     *
     * Each character is separated by '+' for clarity.
     */
    private static final String UNICODE_SPACE_TTML = "<p begin=\"00:00:05.000\" "
            + "end=\"00:00:07.000\">"
            + "&#x202F;+&#x205F;+&#x3000;+&#x2000;+&#x2002;+&#x200A;"
            + "</p>";

    /**
     * TTML example with non-spacing (invisible) characters.
     * These are encoded using character references (&#xXXXX;).
     *
     * Includes:
     * (&#x200B;)'\u200B' → Zero-width space (ZWSP)
     * (&#x200E;)'\u200E' → Left-to-right mark (LRM)
     * (&#x200F;)'\u200F' → Right-to-left mark (RLM)
     *
     * They don't display any characters to the human eye.
     * '+' is used between them for clarity in test output.
     */
    private static final String NON_SPACING_TTML = "<p begin=\"00:00:05.000\" "
            + "end=\"00:00:07.000\">"
            + "&#x200B;+&#x200E;+&#x200F;"
            + "</p>";

    /**
     * Parses TTML string into a JSoup Document and selects the first <p> element.
     *
     * @param ttmlContent TTML content (e.g., <p>...</p>)
     * @return the first <p> element
     * @throws Exception if parsing or reflection fails
     */
    private Element parseTtmlParagraph(final String ttmlContent) throws Exception {
        final String ttml = TTML_WRAPPER_START + ttmlContent + TTML_WRAPPER_END;
        final Document doc = Jsoup.parse(
                new ByteArrayInputStream(ttml.getBytes(StandardCharsets.UTF_8)),
                "UTF-8", "", Parser.xmlParser());
        return doc.select("body > div > p").first();
    }

    /**
     * Invokes private extractText method via reflection.
     *
     * @param writer SrtFromTtmlWriter instance
     * @param paragraph <p> element to extract text from
     * @param text StringBuilder to store extracted text
     * @throws Exception if reflection fails
     */
    private void invokeExtractText(final SrtFromTtmlWriter writer, final Element paragraph,
                                  final StringBuilder text) throws Exception {
        final Method method = writer.getClass()
                .getDeclaredMethod(EXTRACT_TEXT_METHOD, Node.class, StringBuilder.class);
        method.setAccessible(true);
        method.invoke(writer, paragraph, text);
    }

    private String extractTextFromTtml(final String ttmlInput) throws Exception {
        final Element paragraph = parseTtmlParagraph(ttmlInput);
        final StringBuilder text = new StringBuilder();
        final SrtFromTtmlWriter writer = new SrtFromTtmlWriter(null, false);
        invokeExtractText(writer, paragraph, text);

        final String actualText = text.toString();
        return actualText;
    }

    @Test
    public void testExtractTextSimpleParagraph() throws Exception {
        final String expected = "Hello World!";
        final String actual = extractTextFromTtml(SIMPLE_TTML);
        assertEquals(expected, actual);
    }

    @Test
    public void testExtractTextNestedTags() throws Exception {
        final String expected = "Hello\r\nWorld!";
        final String actual = extractTextFromTtml(NESTED_TTML);
        assertEquals(expected, actual);
    }

    @Test
    public void testExtractTextWithEntity() throws Exception {
        final String expected = "<tag> & \"text\"''''  ";
        final String actual = extractTextFromTtml(ENTITY_TTML);
        assertEquals(expected, actual);
    }

    @Test
    public void testExtractTextWithSpecialCharacters() throws Exception {
        final String expected = "   ～~-Hello  &&<<>>World!!   ";
        final String actual = extractTextFromTtml(SPECIAL_TTML);
        assertEquals(expected, actual);
    }

    @Test
    public void testExtractTextWithTab() throws Exception {
        final String expected = "  +  +  ";
        final String actual = extractTextFromTtml(TAB_TTML);
        assertEquals(expected, actual);
    }

    @Test
    public void testExtractTextWithLineEnding0() throws Exception {
        final String expected = NEW_LINE + NEW_LINE + "+"
                                + NEW_LINE + NEW_LINE + "+"
                                + NEW_LINE + NEW_LINE;
        final String actual = extractTextFromTtml(LINE_ENDING_0_TTML);
        assertEquals(expected, actual);
    }

    @Test
    public void testExtractTextWithLineEnding1() throws Exception {
        final String expected = NEW_LINE + NEW_LINE + "+"
                                + NEW_LINE + NEW_LINE + "+"
                                + NEW_LINE + NEW_LINE;
        final String actual = extractTextFromTtml(LINE_ENDING_1_TTML);
        assertEquals(expected, actual);
    }

    @Test
    public void testExtractTextWithLineEnding2() throws Exception {
        final String expected = NEW_LINE + "+"
                                + NEW_LINE + "+"
                                + NEW_LINE;
        final String actual = extractTextFromTtml(LINE_ENDING_2_TTML);
        assertEquals(expected, actual);
    }

    @Test
    public void testExtractTextWithControlCharacters() throws Exception {
        final String expected = "+++++";
        final String actual = extractTextFromTtml(CONTROL_CHAR_TTML);
        assertEquals(expected, actual);
    }

    /**
    * Test case to ensure that extractText() does not throw an exception
    * when there are no text in the TTML paragraph (i.e., the paragraph
    * is empty).
    *
    * Note:
    *   In the NewPipe, *.srt files will contain empty text lines by default.
    */
    @Test
    public void testExtractTextWithEmpty() throws Exception {
        final String expected = "";
        final String actual = extractTextFromTtml(EMPTY_TTML);
        assertEquals(expected, actual);
    }

    @Test
    public void testExtractTextWithUnicodeSpaces() throws Exception {
        final String expected = " + + + + + ";
        final String actual = extractTextFromTtml(UNICODE_SPACE_TTML);
        assertEquals(expected, actual);
    }

    @Test
    public void testExtractTextWithNonSpacingCharacters() throws Exception {
        final String expected = "++";
        final String actual = extractTextFromTtml(NON_SPACING_TTML);
        assertEquals(expected, actual);
    }
}
