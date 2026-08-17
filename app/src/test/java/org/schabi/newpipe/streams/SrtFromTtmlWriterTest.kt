package org.schabi.newpipe.streams

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.parser.Parser
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [SrtFromTtmlWriter].
 *
 * Tests focus on `extractText()` and its handling of TTML <p> elements.
 * Note:
 * - Uses reflection to call the private `extractText()` method.
 * - Update `EXTRACT_TEXT_METHOD` if renamed.
 */
class SrtFromTtmlWriterTest {

    @Throws(Exception::class)
    private fun parseTtmlParagraph(ttmlContent: String): Element {
        val ttml = TTML_WRAPPER_START + ttmlContent + TTML_WRAPPER_END
        val doc = Ksoup.parse(
            html = ttml,
            parser = Parser.xmlParser(),
            baseUri = ""
        )
        return doc.select("body > div > p").first()!!
    }

    @Throws(Exception::class)
    private fun invokeExtractText(
        writer: SrtFromTtmlWriter,
        paragraph: Element,
        text: StringBuilder
    ) {
        val method = writer.javaClass
            .getDeclaredMethod(EXTRACT_TEXT_METHOD, Node::class.java, StringBuilder::class.java)
        method.isAccessible = true
        method.invoke(writer, paragraph, text)
    }

    @Throws(Exception::class)
    private fun extractTextFromTtml(ttmlInput: String): String {
        val paragraph = parseTtmlParagraph(ttmlInput)
        val text = StringBuilder()
        val writer = SrtFromTtmlWriter(null, false)
        invokeExtractText(writer, paragraph, text)

        return text.toString()
    }

    @Test
    @Throws(Exception::class)
    fun testExtractTextSimpleParagraph() {
        val expected = "Hello World!"
        val actual = extractTextFromTtml(SIMPLE_TTML)
        assertEquals(expected, actual)
    }

    @Test
    @Throws(Exception::class)
    fun testExtractTextNestedTags() {
        val expected = "Hello\r\nWorld!"
        val actual = extractTextFromTtml(NESTED_TTML)
        assertEquals(expected, actual)
    }

    @Test
    @Throws(Exception::class)
    fun testExtractTextWithEntity() {
        val expected = "<tag> & \"text\"''''  "
        val actual = extractTextFromTtml(ENTITY_TTML)
        assertEquals(expected, actual)
    }

    @Test
    @Throws(Exception::class)
    fun testExtractTextWithSpecialCharacters() {
        val expected = "   ～~-Hello  &&<<>>World!!   "
        val actual = extractTextFromTtml(SPECIAL_TTML)
        assertEquals(expected, actual)
    }

    @Test
    @Throws(Exception::class)
    fun testExtractTextWithTab() {
        val expected = "  +  +  "
        val actual = extractTextFromTtml(TAB_TTML)
        assertEquals(expected, actual)
    }

    @Test
    @Throws(Exception::class)
    fun testExtractTextWithLineEnding0() {
        val expected = (
            NEW_LINE + NEW_LINE + "+" +
                NEW_LINE + NEW_LINE + "+" +
                NEW_LINE + NEW_LINE
            )
        val actual = extractTextFromTtml(LINE_ENDING_0_TTML)
        assertEquals(expected, actual)
    }

    @Test
    @Throws(Exception::class)
    fun testExtractTextWithLineEnding1() {
        val expected = (
            NEW_LINE + NEW_LINE + "+" +
                NEW_LINE + NEW_LINE + "+" +
                NEW_LINE + NEW_LINE
            )
        val actual = extractTextFromTtml(LINE_ENDING_1_TTML)
        assertEquals(expected, actual)
    }

    @Test
    @Throws(Exception::class)
    fun testExtractTextWithLineEnding2() {
        val expected = (
            NEW_LINE + "+" +
                NEW_LINE + "+" +
                NEW_LINE
            )
        val actual = extractTextFromTtml(LINE_ENDING_2_TTML)
        assertEquals(expected, actual)
    }

    @Test
    @Throws(Exception::class)
    fun testExtractTextWithControlCharacters() {
        val expected = "+++++"
        val actual = extractTextFromTtml(CONTROL_CHAR_TTML)
        assertEquals(expected, actual)
    }

    @Test
    @Throws(Exception::class)
    fun testExtractTextWithEmpty() {
        val expected = ""
        val actual = extractTextFromTtml(EMPTY_TTML)
        assertEquals(expected, actual)
    }

    @Test
    @Throws(Exception::class)
    fun testExtractTextWithUnicodeSpaces() {
        val expected = " + + + + + "
        val actual = extractTextFromTtml(UNICODE_SPACE_TTML)
        assertEquals(expected, actual)
    }

    @Test
    @Throws(Exception::class)
    fun testExtractTextWithNonSpacingCharacters() {
        val expected = "++"
        val actual = extractTextFromTtml(NON_SPACING_TTML)
        assertEquals(expected, actual)
    }

    companion object {
        private const val TTML_WRAPPER_START = "<tt><body><div>"
        private const val TTML_WRAPPER_END = "</div></body></tt>"
        private const val EXTRACT_TEXT_METHOD = "extractText"
        private const val NEW_LINE = "\r\n"

        private const val SIMPLE_TTML = (
            "<p begin=\"00:00:01.000\" end=\"00:00:03.000\" " +
                "style=\"s2\">Hello World!</p>"
            )

        private const val NESTED_TTML = (
            "<p begin=\"00:00:01.000\" end=\"00:00:03.000\">" +
                "<span style=\"s4\">Hello</span><br>World!</p>"
            )

        private const val ENTITY_TTML = (
            "<p begin=\"00:00:05.000\" " +
                "end=\"00:00:07.000\">" +
                "&lt;tag&gt; &amp; &quot;text&quot;&apos;&apos;&#39;&#39;" +
                "&#xA0;&#xA0;" +
                "</p>"
            )

        private const val SPECIAL_TTML = (
            "<p begin=\"00:00:05.000\" end=\"00:00:07.000\">" +
                "   ～~-Hello&nbsp;&nbsp;&amp;&amp;&lt;&lt;&gt;&gt;World!!   " +
                "</p>"
            )

        private const val TAB_TTML = (
            "<p begin=\"00:00:05.000\" " +
                "end=\"00:00:07.000\">" +
                "&#x9;&#x9;+&#x9;&#x9;+&#x9;&#x9;" +
                "</p>"
            )

        private const val LINE_ENDING_0_TTML = (
            "<p begin=\"00:00:05.000\" " +
                "end=\"00:00:07.000\">" +
                "&#xD;&#xD;+&#xD;&#xD;+&#xD;&#xD;" +
                "</p>"
            )

        private const val LINE_ENDING_1_TTML = (
            "<p begin=\"00:00:05.000\" " +
                "end=\"00:00:07.000\">" +
                "&#xA;&#xA;+&#xA;&#xA;+&#xA;&#xA;" +
                "</p>"
            )

        private const val LINE_ENDING_2_TTML = (
            "<p begin=\"00:00:05.000\" end=\"00:00:07.000\">" +
                "&#xD;&#xA;+&#xD;&#xA;+&#xD;&#xA;" +
                "</p>"
            )

        private const val CONTROL_CHAR_TTML = (
            "<p begin=\"00:00:05.000\" " +
                "end=\"00:00:07.000\">" +
                "&#x0001;+&#x0008;+&#x000B;+&#x000C;+&#x000E;+&#x001F;" +
                "</p>"
            )

        private const val EMPTY_TTML = (
            "<p begin=\"00:00:01.000\" " +
                "end=\"00:00:03.000\">" +
                "" +
                "</p>"
            )

        private const val UNICODE_SPACE_TTML = (
            "<p begin=\"00:00:05.000\" " +
                "end=\"00:00:07.000\">" +
                "&#x202F;+&#x205F;+&#x3000;+&#x2000;+&#x2002;+&#x200A;" +
                "</p>"
            )

        private const val NON_SPACING_TTML = (
            "<p begin=\"00:00:05.000\" " +
                "end=\"00:00:07.000\">" +
                "&#x200B;+&#x200E;+&#x200F;" +
                "</p>"
            )
    }
}
