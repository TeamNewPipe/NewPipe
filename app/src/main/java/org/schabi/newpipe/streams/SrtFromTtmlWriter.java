package org.schabi.newpipe.streams;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.schabi.newpipe.streams.io.SharpStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Converts TTML subtitles to SRT format.
 *
 * References:
 *  - TTML 2.0 (W3C): https://www.w3.org/TR/ttml2/
 *  - SRT format: https://en.wikipedia.org/wiki/SubRip
 */
public class SrtFromTtmlWriter {
    private static final String NEW_LINE = "\r\n";

    private final SharpStream out;
    private final boolean ignoreEmptyFrames;
    private final Charset charset = StandardCharsets.UTF_8;

    private int frameIndex = 0;

    public SrtFromTtmlWriter(final SharpStream out, final boolean ignoreEmptyFrames) {
        this.out = out;
        this.ignoreEmptyFrames = ignoreEmptyFrames;
    }

    private static String getTimestamp(final Element frame, final String attr) {
        return frame
                .attr(attr)
                .replace('.', ','); // SRT subtitles uses comma as decimal separator
    }

    private void writeFrame(final String begin, final String end, final StringBuilder text)
            throws IOException {
        writeString(String.valueOf(frameIndex++));
        writeString(NEW_LINE);
        writeString(begin);
        writeString(" --> ");
        writeString(end);
        writeString(NEW_LINE);
        writeString(text.toString());
        writeString(NEW_LINE);
        writeString(NEW_LINE);
    }

    private void writeString(final String text) throws IOException {
        out.write(text.getBytes(charset));
    }

    /**
     * Decode XML or HTML entities into their actual (literal) characters.
     *
     * TTML is XML-based, so text nodes may contain escaped entities
     * instead of direct characters. For example:
     *
     *   "&amp;"          → "&"
     *   "&lt;"           → "<"
     *   "&gt;"           → ">"
     *   "&#x9;"          → "\t" (TAB)
     *   "&#xA;" (&#10;)  → "\n" (LINE FEED)
     *
     * XML files cannot contain characters like "<", ">", "&" directly,
     * so they must be represented using their entity-encoded forms.
     *
     * Jsoup sometimes leaves nested or encoded entities unresolved
     * (e.g. inside <p> text nodes in TTML files), so this function
     * acts as a final “safety net” to ensure all entities are decoded
     * before further normalization.
     *
     * Character representation layers for reference:
     *   - Literal characters: <, >, &
     *       → appear in runtime/output text (e.g. final SRT output)
     *   - Escaped entities: &lt;, &gt;, &amp;
     *       → appear in XML/HTML/TTML source files
     *   - Numeric entities: &#xA0;, &#x9;, &#xD;
     *       → appear mainly in XML/TTML files (also valid in HTML)
     *         for non-printable or special characters
     *   - Unicode escapes: \u00A0 (Java/Unicode internal form)
     *       → appear only in Java source code (NOT valid in XML)
     *
     * XML entities include both named (&amp;, &lt;) and numeric
     * (&#xA0;, &#160;) forms.
     *
     * @param encodedEntities The raw text fragment possibly containing
     *                        encoded XML entities.
     * @return A decoded string where all entities are replaced by their
     *         actual (literal) characters.
     */
    private String decodeXmlEntities(final String encodedEntities) {
        final String decoded = Parser.unescapeEntities(encodedEntities, true);
        return decoded;
    }

    /**
     * Handle rare XML entity characters like LF: &#xA;(`\n`),
     * CR: &#xD;(`\r`) and CRLF: (`\r\n`).
     *
     * These are technically valid in TTML (XML allows them)
     * but unusual in practice, since most TTML line breaks
     * are represented as <br/> tags instead.
     * As a defensive approach, we normalize them:
     *
     * - Windows (\r\n), macOS (\r), and Unix (\n) → unified SRT NEW_LINE (\r\n)
     *
     * Although well-formed TTML normally encodes line breaks
     * as <br/> tags, some auto-generated or malformed TTML files
     * may embed literal newline entities (&#xA;, &#xD;). This
     * normalization ensures these cases render properly in SRT
     * players instead of breaking the subtitle structure.
     *
     * @param text To be normalized text with actual characters.
     * @return Unified SRT NEW_LINE converted from all kinds of line breaks.
     */
    private String normalizeLineBreakForSrt(final String text) {
        String cleaned = text;

        // NOTE:
        // The order of newline replacements must NOT change,
        // or duplicated line breaks (e.g. \r\n → \n\n) will occur.
        cleaned = cleaned.replace("\r\n", "\n")
                         .replace("\r", "\n");

        cleaned = cleaned.replace("\n", NEW_LINE);

        return cleaned;
    }

    private String normalizeForSrt(final String actualText) {
        String cleaned = actualText;

        // Replace NBSP "non-breaking space" (\u00A0) with regular space ' '(\u0020).
        //
        // Why:
        // - Some viewers render NBSP(\u00A0) incorrectly:
        //   * MPlayer 1.5: shown as “??”
        //   * Linux command `cat -A`: displayed as control-like markers
        //     (M-BM-)
        //   * Acode (Android editor): displayed as visible replacement
        //     glyphs (red dots)
        // - Other viewers show it as a normal space (e.g., VS Code 1.104.0,
        //   vlc 3.0.20, mpv 0.37.0, Totem 43.0)
        // → Mixed rendering creates inconsistency and may confuse users.
        //
        // Details:
        // - YouTube TTML subtitles use both regular spaces (\u0020)
        //   and non-breaking spaces (\u00A0).
        // - SRT subtitles only support regular spaces (\u0020),
        //   so \u00A0 may cause display issues.
        // - \u00A0 and \u0020 are visually identical (i.e., they both
        //   appear as spaces ' '), but they differ in Unicode encoding,
        //   and NBSP (\u00A0) renders differently in different viewers.
        // - SRT is a plain-text format and does not interpret
        //   "non-breaking" behavior.
        //
        // Conclusion:
        // - Ensure uniform behavior, so replace it to a regular space
        //   without "non-breaking" behavior.
        //
        // References:
        //   - Unicode U+00A0 NBSP (Latin-1 Supplement):
        //     https://unicode.org/charts/PDF/U0080.pdf
        cleaned = cleaned.replace('\u00A0', ' ') // Non-breaking space
                 .replace('\u202F', ' ') // Narrow no-break space
                 .replace('\u205F', ' ') // Medium mathematical space
                 .replace('\u3000', ' ') // Ideographic space
                 // \u2000 ~ \u200A are whitespace characters (e.g.,
                 // en space, em space), replaced with regular space (\u0020).
                 .replaceAll("[\\u2000-\\u200A]", " "); // Whitespace characters

        // \u200B ~ \u200F are a range of non-spacing characters
        // (e.g., zero-width space, zero-width non-joiner, etc.),
        // which have no effect in *.SRT files and may cause
        // display issues.
        // These characters are invisible to the human eye, and
        // they still exist in the encoding, so they need to be
        // removed.
        // After removal, the actual content becomes completely
        // empty "", meaning there are no characters left, just
        // an empty space, which helps avoid formatting issues
        // in subtitles.
        cleaned = cleaned.replaceAll("[\\u200B-\\u200F]", ""); // Non-spacing characters

        // Remove control characters (\u0000 ~ \u001F, except
        // \n, \r, \t).
        // - These are ASCII C0 control codes (e.g. \u0001 SOH,
        //   \u0008 BS, \u001F US), invisible and irrelevant in
        //   subtitles, may cause square boxes (?) in players.
        // - Reference:
        //   Unicode Basic Latin (https://unicode.org/charts/PDF/U0000.pdf)
        //   ASCII Control (https://en.wikipedia.org/wiki/ASCII#Control_characters)
        cleaned = cleaned.replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]", "");

        // Reasoning:
        // - subtitle files generally don't require tabs for alignment.
        // - Tabs can be displayed with varying widths across different
        //   editors or platforms, which may cause display issues.
        // - Replace it with a single space for consistent display
        //   across different editors or platforms.
        cleaned = cleaned.replace('\t', ' ');

        cleaned = normalizeLineBreakForSrt(cleaned);

        return cleaned;
    }

    private String sanitizeFragment(final String raw) {
        if (null == raw) {
            return "";
        }

        final String actualCharacters = decodeXmlEntities(raw);

        final String srtSafeText = normalizeForSrt(actualCharacters);

        return srtSafeText;
    }

    // Recursively process all child nodes to ensure text inside
    // nested tags (e.g., <span>) is also extracted.
    private void traverseChildNodesForNestedTags(final Node parent,
                                                 final StringBuilder text) {
        for (final Node child : parent.childNodes()) {
            extractText(child, text);
        }
    }

    // CHECKSTYLE:OFF checkstyle:JavadocStyle
    // checkstyle does not understand that span tags are inside a code block
    /**
     * <p>Recursive method to extract text from all nodes.</p>
     * <p>
     *   This method processes {@link TextNode}s and {@code <br>} tags,
     *   recursively extracting text from nested tags
     *   (e.g. extracting text from nested {@code <span>} tags).
     *   Newlines are added for {@code <br>} tags.
     * </p>
     * @param node the current node to process
     * @param text the {@link StringBuilder} to append the extracted text to
     */
    // --------------------------------------------------------------------
    // [INTERNAL NOTE] TTML text layer explanation
    //
    // TTML parsing involves multiple text "layers":
    //   1. Raw XML entities (e.g., &lt;, &#xA0;) are decoded by Jsoup.
    //   2. extractText() works on DOM TextNodes (already parsed strings).
    //   3. sanitizeFragment() decodes remaining entities and fixes
    //      Unicode quirks.
    //   4. normalizeForSrt() ensures literal text is safe for SRT output.
    //
    // In short:
    //   Jsoup handles XML-level syntax,
    //   our code handles text-level normalization for subtitles.
    // --------------------------------------------------------------------
    private void extractText(final Node node, final StringBuilder text) {
        if (node instanceof TextNode textNode) {
            String rawTtmlFragment = textNode.getWholeText();
            String srtContent = sanitizeFragment(rawTtmlFragment);
            text.append(srtContent);
        } else if (node instanceof Element element) {
            // <br> is a self-closing HTML tag used to insert a line break.
            if (element.tagName().equalsIgnoreCase("br")) {
                // Add a newline for <br> tags
                text.append(NEW_LINE);
            }
        }

        traverseChildNodesForNestedTags(node, text);
    }
    // CHECKSTYLE:ON

    public void build(final SharpStream ttml) throws IOException {
        /*
         * TTML parser with BASIC support
         * multiple CUE is not supported
         * styling is not supported
         * tag timestamps (in auto-generated subtitles) are not supported, maybe in the future
         * also TimestampTagOption enum is not applicable
         * Language parsing is not supported
         */

        // parse XML
        final byte[] buffer = new byte[(int) ttml.available()];
        ttml.read(buffer);
        final Document doc = Jsoup.parse(new ByteArrayInputStream(buffer), "UTF-8", "",
                Parser.xmlParser());

        final StringBuilder text = new StringBuilder(128);
        final Elements paragraphList = doc.select("body > div > p");

        // check if has frames
        if (paragraphList.isEmpty()) {
            return;
        }

        for (final Element paragraph : paragraphList) {
            text.setLength(0);

            // Recursively extract text from all child nodes
            extractText(paragraph, text);

            if (ignoreEmptyFrames && text.length() < 1) {
                continue;
            }

            final String begin = getTimestamp(paragraph, "begin");
            final String end = getTimestamp(paragraph, "end");

            writeFrame(begin, end, text);
        }
    }
}
