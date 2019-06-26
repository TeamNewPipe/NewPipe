package org.schabi.newpipe.streams;

import org.schabi.newpipe.streams.io.SharpStream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

/**
 * @author kapodamy
 */
public class SubtitleConverter {
    private static final String NEW_LINE = "\r\n";

    public void dumpTTML(SharpStream in, final SharpStream out, final boolean ignoreEmptyFrames, final boolean detectYoutubeDuplicateLines
    ) throws IOException, ParseException, SAXException, ParserConfigurationException, XPathExpressionException {

        final FrameWriter callback = new FrameWriter() {
            int frameIndex = 0;
            final Charset charset = Charset.forName("utf-8");

            @Override
            public void yield(SubtitleFrame frame) throws IOException {
                if (ignoreEmptyFrames && frame.isEmptyText()) {
                    return;
                }
                out.write(String.valueOf(frameIndex++).getBytes(charset));
                out.write(NEW_LINE.getBytes(charset));
                out.write(getTime(frame.start, true).getBytes(charset));
                out.write(" --> ".getBytes(charset));
                out.write(getTime(frame.end, true).getBytes(charset));
                out.write(NEW_LINE.getBytes(charset));
                out.write(frame.text.getBytes(charset));
                out.write(NEW_LINE.getBytes(charset));
                out.write(NEW_LINE.getBytes(charset));
            }
        };

        read_xml_based(in, callback, detectYoutubeDuplicateLines,
                "tt", "xmlns", "http://www.w3.org/ns/ttml",
                new String[]{"timedtext", "head", "wp"},
                new String[]{"body", "div", "p"},
                "begin", "end", true
        );
    }

    private void read_xml_based(SharpStream source, FrameWriter callback, boolean detectYoutubeDuplicateLines,
                                String root, String formatAttr, String formatVersion, String[] cuePath, String[] framePath,
                                String timeAttr, String durationAttr, boolean hasTimestamp
    ) throws IOException, ParseException, SAXException, ParserConfigurationException, XPathExpressionException {
        /*
         * XML based subtitles parser with BASIC support
         * multiple CUE is not supported
         * styling is not supported
         * tag timestamps (in auto-generated subtitles) are not supported, maybe in the future
         * also TimestampTagOption enum is not applicable
         * Language parsing is not supported
         */

        byte[] buffer = new byte[(int) source.available()];
        source.read(buffer);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document xml = builder.parse(new ByteArrayInputStream(buffer));

        String attr;

        // get the format version or namespace
        Element node = xml.getDocumentElement();

        if (node == null) {
            throw new ParseException("Can't get the format version. ¿wrong namespace?", -1);
        } else if (!node.getNodeName().equals(root)) {
            throw new ParseException("Invalid root", -1);
        }

        if (formatAttr.equals("xmlns")) {
            if (!node.getNamespaceURI().equals(formatVersion)) {
                throw new UnsupportedOperationException("Expected xml namespace: " + formatVersion);
            }
        } else {
            attr = node.getAttributeNS(formatVersion, formatAttr);
            if (attr == null) {
                throw new ParseException("Can't get the format attribute", -1);
            }
            if (!attr.equals(formatVersion)) {
                throw new ParseException("Invalid format version : " + attr, -1);
            }
        }

        NodeList node_list;

        int line_break = 0;// Maximum characters per line if present (valid for TranScript v3)

        if (!hasTimestamp) {
            node_list = selectNodes(xml, cuePath, formatVersion);

            if (node_list != null) {
                // if the subtitle has multiple CUEs, use the highest value
                for (int i = 0; i < node_list.getLength(); i++) {
                    try {
                        int tmp = Integer.parseInt(((Element) node_list.item(i)).getAttributeNS(formatVersion, "ah"));
                        if (tmp > line_break) {
                            line_break = tmp;
                        }
                    } catch (Exception err) {
                    }
                }
            }
        }

        // parse every frame
        node_list = selectNodes(xml, framePath, formatVersion);

        if (node_list == null) {
            return;// no frames detected
        }

        int fs_ff = -1;// first timestamp of first frame
        boolean limit_lines = false;

        for (int i = 0; i < node_list.getLength(); i++) {
            Element elem = (Element) node_list.item(i);
            SubtitleFrame obj = new SubtitleFrame();
            obj.text = elem.getTextContent();

            attr = elem.getAttribute(timeAttr);// ¡this cant be null!
            obj.start = hasTimestamp ? parseTimestamp(attr) : Integer.parseInt(attr);

            attr = elem.getAttribute(durationAttr);
            if (obj.text == null || attr == null) {
                continue;// normally is a blank line (on auto-generated subtitles) ignore
            }

            if (hasTimestamp) {
                obj.end = parseTimestamp(attr);

                if (detectYoutubeDuplicateLines) {
                    if (limit_lines) {
                        int swap = obj.end;
                        obj.end = fs_ff;
                        fs_ff = swap;
                    } else {
                        if (fs_ff < 0) {
                            fs_ff = obj.end;
                        } else {
                            if (fs_ff < obj.start) {
                                limit_lines = true;// the subtitles has duplicated lines
                            } else {
                                detectYoutubeDuplicateLines = false;
                            }
                        }
                    }
                }
            } else {
                obj.end = obj.start + Integer.parseInt(attr);
            }

            if (/*node.getAttribute("w").equals("1") &&*/line_break > 1 && obj.text.length() > line_break) {

                // implement auto line breaking (once)
                StringBuilder text = new StringBuilder(obj.text);
                obj.text = null;

                switch (text.charAt(line_break)) {
                    case ' ':
                    case '\t':
                        putBreakAt(line_break, text);
                        break;
                    default:// find the word start position
                        for (int j = line_break - 1; j > 0; j--) {
                            switch (text.charAt(j)) {
                                case ' ':
                                case '\t':
                                    putBreakAt(j, text);
                                    j = -1;
                                    break;
                                case '\r':
                                case '\n':
                                    j = -1;// long word, just ignore
                                    break;
                            }
                        }
                        break;
                }

                obj.text = text.toString();// set the processed text
            }

            callback.yield(obj);
        }
    }

    private static NodeList selectNodes(Document xml, String[] path, String namespaceUri) {
        Element ref = xml.getDocumentElement();

        for (int i = 0; i < path.length - 1; i++) {
            NodeList nodes = ref.getChildNodes();
            if (nodes.getLength() < 1) {
                return null;
            }

            Element elem;
            for (int j = 0; j < nodes.getLength(); j++) {
                if (nodes.item(j).getNodeType() == Node.ELEMENT_NODE) {
                    elem = (Element) nodes.item(j);
                    if (elem.getNodeName().equals(path[i]) && elem.getNamespaceURI().equals(namespaceUri)) {
                        ref = elem;
                        break;
                    }
                }
            }
        }

        return ref.getElementsByTagNameNS(namespaceUri, path[path.length - 1]);
    }

    private static int parseTimestamp(String multiImpl) throws NumberFormatException, ParseException {
        if (multiImpl.length() < 1) {
            return 0;
        } else if (multiImpl.length() == 1) {
            return Integer.parseInt(multiImpl) * 1000;// ¡this must be a number in seconds!
        }

        // detect wallclock-time
        if (multiImpl.startsWith("wallclock(")) {
            throw new UnsupportedOperationException("Parsing wallclock timestamp is not implemented");
        }

        // detect offset-time
        if (multiImpl.indexOf(':') < 0) {
            int multiplier = 1000;
            char metric = multiImpl.charAt(multiImpl.length() - 1);
            switch (metric) {
                case 'h':
                    multiplier *= 3600000;
                    break;
                case 'm':
                    multiplier *= 60000;
                    break;
                case 's':
                    if (multiImpl.charAt(multiImpl.length() - 2) == 'm') {
                        multiplier = 1;// ms
                    }
                    break;
                default:
                    if (!Character.isDigit(metric)) {
                        throw new NumberFormatException("Invalid metric suffix found on : " + multiImpl);
                    }
                    metric = '\0';
                    break;
            }
            try {
                String offset_time = multiImpl;

                if (multiplier == 1) {
                    offset_time = offset_time.substring(0, offset_time.length() - 2);
                } else if (metric != '\0') {
                    offset_time = offset_time.substring(0, offset_time.length() - 1);
                }

                double time_metric_based = Double.parseDouble(offset_time);
                if (Math.abs(time_metric_based) <= Double.MAX_VALUE) {
                    return (int) (time_metric_based * multiplier);
                }
            } catch (Exception err) {
                throw new UnsupportedOperationException("Invalid or not implemented timestamp on: " + multiImpl);
            }
        }

        // detect clock-time
        int time = 0;
        String[] units = multiImpl.split(":");

        if (units.length < 3) {
            throw new ParseException("Invalid clock-time timestamp", -1);
        }

        time += Integer.parseInt(units[0]) * 3600000;// hours
        time += Integer.parseInt(units[1]) * 60000;//minutes
        time += Float.parseFloat(units[2]) * 1000f;// seconds and milliseconds (if present)

        // frames and sub-frames are ignored (not implemented)
        // time += units[3] * fps;
        return time;
    }

    private static void putBreakAt(int idx, StringBuilder str) {
        // this should be optimized at compile time

        if (NEW_LINE.length() > 1) {
            str.delete(idx, idx + 1);// remove after replace
            str.insert(idx, NEW_LINE);
        } else {
            str.setCharAt(idx, NEW_LINE.charAt(0));
        }
    }

    private static String getTime(int time, boolean comma) {
        // cast every value to integer to avoid auto-round in ToString("00").
        StringBuilder str = new StringBuilder(12);
        str.append(numberToString(time / 1000 / 3600, 2));// hours
        str.append(':');
        str.append(numberToString(time / 1000 / 60 % 60, 2));// minutes
        str.append(':');
        str.append(numberToString(time / 1000 % 60, 2));// seconds
        str.append(comma ? ',' : '.');
        str.append(numberToString(time % 1000, 3));// miliseconds

        return str.toString();
    }

    private static String numberToString(int nro, int pad) {
        return String.format(Locale.ENGLISH, "%0".concat(String.valueOf(pad)).concat("d"), nro);
    }


    /******************
     * helper classes *
     ******************/

    private interface FrameWriter {

        void yield(SubtitleFrame frame) throws IOException;
    }

    private static class SubtitleFrame {
        //Java no support unsigned int

        public int end;
        public int start;
        public String text = "";

        private boolean isEmptyText() {
            if (text == null) {
                return true;
            }

            for (int i = 0; i < text.length(); i++) {
                switch (text.charAt(i)) {
                    case ' ':
                    case '\t':
                    case '\r':
                    case '\n':
                        break;
                    default:
                        return false;
                }
            }

            return true;
        }
    }

}
