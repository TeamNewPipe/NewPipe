package org.schabi.newpipe.streams

import java.io.ByteArrayInputStream
import okio.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import com.fleeksoft.ksoup.parser.Parser
import org.schabi.newpipe.streams.io.SharpStream

/**
 * Converts TTML subtitles to SRT format.
 *
 * References:
 *  - TTML 2.0 (W3C): https://www.w3.org/TR/ttml2/
 *  - SRT format: https://en.wikipedia.org/wiki/SubRip
 */
class SrtFromTtmlWriter(private val out: SharpStream, private val ignoreEmptyFrames: Boolean) {
    private val charset: Charset = StandardCharsets.UTF_8

    // According to the SubRip (.srt) specification, subtitle
    // numbering must start from 1.
    // Some players accept 0 or even negative indices,
    // but to ensure compliance we start at 1.
    private var frameIndex = 1

    private fun getTimestamp(frame: Element, attr: String): String {
        return frame.attr(attr).replace('.', ',') // SRT subtitles uses comma as decimal separator
    }

    @Throws(IOException::class)
    private suspend fun writeFrame(begin: String, end: String, text: StringBuilder) {
        writeString(frameIndex.toString())
        frameIndex += 1
        writeString(NEW_LINE)
        writeString(begin)
        writeString(" --> ")
        writeString(end)
        writeString(NEW_LINE)
        writeString(text.toString())
        writeString(NEW_LINE)
        writeString(NEW_LINE)
    }

    @Throws(IOException::class)
    private suspend fun writeString(text: String) {
        out.write(text.toByteArray(charset))
    }

    private fun decodeXmlEntities(encodedEntities: String): String {
        return Parser.unescapeEntities(encodedEntities, true)
    }

    private fun normalizeLineBreakForSrt(text: String): String {
        return text.replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("\n", NEW_LINE)
    }

    private fun normalizeForSrt(actualText: String): String {
        var cleaned = actualText
            .replace('\u00A0', ' ') // Non-breaking space
            .replace('\u202F', ' ') // Narrow no-break space
            .replace('\u205F', ' ') // Medium mathematical space
            .replace('\u3000', ' ') // Ideographic space
            .replace(Regex("[\\u2000-\\u200A]"), " ") // Whitespace characters

        cleaned = cleaned.replace(Regex("[\\u200B-\\u200F]"), "") // Non-spacing characters

        cleaned = cleaned.replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")

        cleaned = cleaned.replace('\t', ' ')

        return normalizeLineBreakForSrt(cleaned)
    }

    private fun sanitizeFragment(raw: String?): String {
        if (null == raw) return ""
        val actualCharacters = decodeXmlEntities(raw)
        return normalizeForSrt(actualCharacters)
    }

    private fun traverseChildNodesForNestedTags(parent: Node, text: StringBuilder) {
        for (child in parent.childNodes()) {
            extractText(child, text)
        }
    }

    private fun extractText(node: Node, text: StringBuilder) {
        if (node is TextNode) {
            val srtContent = sanitizeFragment(node.getWholeText())
            text.append(srtContent)
        } else if (node is Element) {
            if (node.tagName().equals("br", ignoreCase = true)) {
                text.append(NEW_LINE)
            }
        }
        traverseChildNodesForNestedTags(node, text)
    }

    @Throws(IOException::class)
    suspend fun build(ttml: SharpStream) {
        // parse XML
        val bufferSize = ttml.available().toInt()
        val buffer = ByteArray(bufferSize)
        ttml.read(buffer)
        val doc: Document = Ksoup.parse(
            html = String(buffer, charset),
            parser = Parser.xmlParser(),
            baseUri = ""
        )

        val text = StringBuilder(128)
        val paragraphList = doc.select("body > div > p")

        if (paragraphList.isEmpty()) {
            return
        }

        for (paragraph in paragraphList) {
            text.setLength(0)
            extractText(paragraph, text)

            if (ignoreEmptyFrames && text.isEmpty()) {
                continue
            }

            val begin = getTimestamp(paragraph, "begin")
            val end = getTimestamp(paragraph, "end")
            writeFrame(begin, end, text)
        }
    }

    companion object {
        private const val NEW_LINE = "\r\n"
    }
}
