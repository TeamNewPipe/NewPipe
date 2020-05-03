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
 * @author kapodamy
 */
public class SrtFromTtmlWriter {
    private static final String NEW_LINE = "\r\n";

    private SharpStream out;
    private boolean ignoreEmptyFrames;
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
        byte[] buffer = new byte[(int) ttml.available()];
        ttml.read(buffer);
        Document doc = Jsoup.parse(new ByteArrayInputStream(buffer), "UTF-8", "",
                Parser.xmlParser());

        StringBuilder text = new StringBuilder(128);
        Elements paragraphList = doc.select("body > div > p");

        // check if has frames
        if (paragraphList.size() < 1) {
            return;
        }

        for (Element paragraph : paragraphList) {
            text.setLength(0);

            for (Node children : paragraph.childNodes()) {
                if (children instanceof TextNode) {
                    text.append(((TextNode) children).text());
                } else if (children instanceof Element
                        && ((Element) children).tagName().equalsIgnoreCase("br")) {
                    text.append(NEW_LINE);
                }
            }

            if (ignoreEmptyFrames && text.length() < 1) {
                continue;
            }

            String begin = getTimestamp(paragraph, "begin");
            String end = getTimestamp(paragraph, "end");

            writeFrame(begin, end, text);
        }
    }
}
