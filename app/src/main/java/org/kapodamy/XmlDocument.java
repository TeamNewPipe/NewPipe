package org.kapodamy;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

class XmlDocument {
    private BufferedInputStream src;
    private XmlPullParserFactory fac;

    XmlDocument(InputStream stream, int bufferSize) throws XmlPullParserException {
        // due how xml parsing works is necessary a wrapper
        src = new BufferedInputStream(stream, bufferSize);
        src.mark(0);

        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        fac = factory;
    }

    XmlNode SelectSingleNode(String... path) throws XmlPullParserException, IOException {
        if (path.length < 1) {
            return null;
        }

        src.reset();// ¡this is very much important!
        XmlPullParser parser = fac.newPullParser();
        parser.setInput(src, null);

        for (int i = 0; i < path.length; i++) {
            if (!getNextNode(parser, path[i], i + 1)) {
                return null;
            }
        }

        return new XmlNode(parser);
    }

    XmlNodeList SelectNodes(String... path) throws XmlPullParserException, IOException {
        XmlNode node = SelectSingleNode(path);
        if (node == null) {
            return null;
        }

        return new XmlNodeList(node.parser);
    }


    //***********************//
    //         Utils         //
    //***********************//

    static boolean getNextNode(XmlPullParser parser, String name, int depth) throws XmlPullParserException, IOException {
        int cursor = 0;
        int eventType = 0;

        while (eventType != XmlPullParser.END_DOCUMENT) {
            eventType = parser.next();
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    int tmp = parser.getDepth();

                    if (tmp < depth) {
                        return false;
                    }
                    if (tmp == depth && cursor == 0 && parser.getName().equals(name)) {
                        return true;
                    }
                    cursor++;
                    break;
                case XmlPullParser.END_TAG:
                    if (cursor > 0) {
                        cursor--;
                    }
            }
        }

        return false;
    }


    public static String escapeXML(CharSequence input, boolean forXml10, boolean forAttributes, boolean useHexEscape) {
        if (input == null) {
            return null;
        }
        if (input.length() < 1) {
            return "";
        }

        try {
            final StringBuilder buffer = new StringBuilder((int) (input.length() * 1.1f));

            final int len = input.length();
            for (int i = 0; i < len; i++) {
                char chr = input.charAt(i);

                switch (chr) {
                    case '\"':
                        buffer.append(forAttributes ? "&quot;" : chr);
                        break;
                    case '\'':
                        buffer.append(forAttributes ? "&apos;" : chr);
                        break;
                    case '&':
                        buffer.append("&amp;");
                        break;
                    case '<':
                        buffer.append("&lt;");
                        break;
                    case '>':
                        buffer.append("&gt;");
                        break;
                    case '\u000b':
                        if (!forXml10) {
                            buffer.append("&#11;");
                        }
                        break;
                    case '\u000c':
                        if (!forXml10) {
                            buffer.append("&#12;");
                        }
                        break;
                    default:
                        int code = Character.codePointAt(input, i);

                        switch (code) {
                            case 0:
                                break;
                            case 11:
                            case 12:
                                if (forXml10) {
                                    break;
                                }
                                buffer.append(chr);
                                break;
                            case 0xfffe:
                            case 0xffff:
                                break;
                            default:
                                if ((code >= 1 && code <= 8) || (code >= 14 && code <= 31)) {
                                    if (!forXml10) {
                                        escapeHexCodePoint(buffer, code, useHexEscape);
                                    }
                                    break;
                                }

                                if ((code >= 127 && code <= 132) || (code >= 134 && code <= 159)) {
                                    escapeHexCodePoint(buffer, code, useHexEscape);
                                    break;
                                }

                                if (code >= Character.MIN_SURROGATE && code <= Character.MAX_SURROGATE) {
                                    buffer.append(code);
                                    i++;
                                    if (Character.isHighSurrogate(chr) && i < len) {
                                        code = input.charAt(i);
                                        if (Character.isLowSurrogate(chr)) {
                                            buffer.append(code);
                                            i++;
                                        }
                                    }
                                    break;
                                }

                                buffer.append(input.charAt(i));// a normal char !!!
                        }
                        break;
                }
            }

            return buffer.toString();
        } catch (Exception err) {
            return null;
        }
    }

    public static void escapeHexCodePoint(StringBuilder str, int code, boolean useHexEscape) {
        str.append("&#");
        str.append(Integer.toString(code, useHexEscape ? 16 : 10));
        str.append(';');
    }

    public static String unescapeXML(CharSequence input) {
        int len = input.length();
        int escape = -1;

        StringBuilder buffer = new StringBuilder(len);

        for (int i = 0; i < len; i++) {
            if (escape < 0) {
                if (input.charAt(i) == '&') {
                    escape = i + 1;
                } else {
                    buffer.append(input.charAt(i));
                }
            } else if (input.charAt(i) == ';') {
                String str = input.subSequence(escape, i).toString().toLowerCase();
                if (str.length() < 1) {
                    escape = -1;
                    continue;
                }

                if (str.equals("quot")) {
                    buffer.append('\"');
                } else if (str.equals("amp")) {
                    buffer.append("&");
                } else if (str.equals("lt")) {
                    buffer.append("<");
                } else if (str.equals("gt")) {
                    buffer.append(">");
                } else if (str.equals("apos")) {
                    buffer.append('\'');
                } else if (str.equals("nbsp")) {
                    buffer.appendCodePoint(0xA0);
                } else {
                    if (str.charAt(0) != '#') {
                        escape = -1;
                        continue;
                    }

                    try {
                        byte radix;
                        if (str.charAt(1) == 'x') {
                            radix = 16;
                            escape = 2;
                        } else {
                            radix = 10;
                            escape = 1;
                        }

                        buffer.appendCodePoint(Integer.parseInt(str.substring(escape), radix));

                    } catch (Exception err) {
                        // Ignore (posible html entity)
                    }
                }

                escape = -1;// find next escape
            }
        }

        return buffer.toString();
    }

}

class XmlNode {
    XmlPullParser parser;

    XmlNode(XmlPullParser parser) {
        this.parser = parser;
    }

    private void init_attrs() {
        if (attrs != null) {
            return;
        }

        // backup attributes first
        attrs = new HashMap<String, String>(parser.getAttributeCount());
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            attrs.put(parser.getAttributeName(i), parser.getAttributeValue(i));
        }
    }

    String getText() throws IOException, XmlPullParserException {
        init_attrs();

        int eventType = 0;
        boolean crash = false;
        int deep = parser.getDepth();

        while (!crash && eventType != XmlPullParser.END_DOCUMENT) {
            eventType = parser.next();

            switch (eventType) {
                case XmlPullParser.TEXT:
                    if (parser.getDepth() != deep) {
                        continue;
                    }
                    return parser.getText();
                case XmlPullParser.END_TAG:
                    if (parser.getDepth() > deep) {
                        continue;
                    }
                    return null;
                case XmlPullParser.START_TAG:
                    if (parser.getDepth() < deep) {
                        crash = true;
                    }
                    break;
            }
        }

        throw new XmlPullParserException("cant read the text node, XmlPullParser crashed");
    }

    String getInnerText() throws IOException, XmlPullParserException {
        init_attrs();

        int eventType = 0;
        boolean crash = false;
        int deep = parser.getDepth();
        StringBuilder buffer = new StringBuilder(128);

        while (!crash && eventType != XmlPullParser.END_DOCUMENT) {
            eventType = parser.next();

            switch (eventType) {
                case XmlPullParser.TEXT:
                    String str = parser.getText();
                    if (str != null) {
                        buffer.append(str);
                    }
                    break;
                case XmlPullParser.END_TAG:
                    if (parser.getDepth() > deep) {
                        continue;
                    }
                    return buffer.toString();
                case XmlPullParser.START_TAG:
                    if (parser.getDepth() < deep) {
                        crash = true;
                    }
                    break;
            }
        }

        throw new XmlPullParserException("cant read the text node, XmlPullParser crashed");
    }

    String getAttribute(String name) {
        return attrs == null ? parser.getAttributeValue(null, name) : attrs.get(name);
    }

    String getNameSpace() {
        return parser.getNamespace();
    }

    private Map<String, String> attrs;
}

class XmlNodeList {
    private XmlPullParser parser;
    boolean first = true;
    String node_name;
    int node_depth;

    XmlNodeList(XmlPullParser parser) {
        this.parser = parser;
        node_name = parser.getName();
        node_depth = parser.getDepth();
    }

    XmlNode getNextNode() throws XmlPullParserException, IOException {
        if (first) {
            first = false;
            return new XmlNode(parser);
        }

        if (!XmlDocument.getNextNode(parser, node_name, node_depth)) {
            parser = null;
        }

        return parser == null ? null : new XmlNode(parser);
    }
}
