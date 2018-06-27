package org.kapodamy;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ported from SubtitleUtils.cs
public class SubtitleUtils {
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final String NEW_LINE = "\r\n";
    private static final boolean USE_XML10_ESCAPE = true;// xml 1.0 or xml 1.1 schema


    public static int getSubtitleIndexBy(List<SubtitlesStream> streams, String languageCode) {
        int i = 0;
        for (; i < streams.size(); i++) {
            if (streams.get(i).getLanguageTag().equalsIgnoreCase(languageCode)) {
                return i;
            }
        }

        i = languageCode.indexOf('-');
        if (i < 1) {
            return 0;
        }

        languageCode = languageCode.substring(0, i);

        // not found? use language with county+variant pair
        return getSubtitleIndexBy(streams, languageCode);
    }

    /**
     * Convert a existing subtitle to SubRip (*.srt)
     *
     * @param cacheDir     Directory for creating temporal files
     * @param subtitle     Subtitle object info. TranScript v1 and TranScript v2 are not supported (deprecated)
     * @param subtitlePath Path to the subtitle file
     * @param deleteOnFail Delete the file if the operation isn't successful
     * @return true, if the file is parsed and written correctly.
     */
    public static boolean Dump(File cacheDir, SubtitlesStream subtitle, String subtitlePath, boolean deleteOnFail) {
        if (subtitle.getFormat() == MediaFormat.SRT) {
            return true;
        }
        
        File src = null;
        File tmp = null;
        FileInputStream reader = null;

        try {
            src = new File(subtitlePath);
            reader = new FileInputStream(src);

            tmp = File.createTempFile("subtitle_", ".srt", cacheDir);
            tmp.createNewFile();

            boolean res = Dump(reader, subtitle.getFormat(), tmp, true, TimestampParsingOption.Accumulate,
                    subtitle.getLanguageTag(), false, false);

            if (res) {
                // now """move""" the file
                reader.close();
                reader = new FileInputStream(tmp);
                copy(reader, src);
            }

            return res;
        } catch (Exception err) {
            if (deleteOnFail && src != null) {
                src.delete();
            }
            err.printStackTrace();
            return false;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    // ¿respect the orginal result?
                }

            }
            if (tmp != null) {
                tmp.delete();
            }
        }
    }

    /**
     * Write a subtitle resource on the disk
     *
     * @param source                      Subtitle stream
     * @param toSubRip                    true, to dump the file to SubRip (*.srt) instead of  WebVTT (*.vtt)
     * @param parseOption                 A TimestampParsingOption indicate how thread the timestamp tags
     * @param languageCode                (For WebVTT only) Indicate the language should we used, if NULL all language lines will be written
     * @param detectYoutubeDuplicateLines (Not valid for TranScript v3) Skip duplicate frame subtitles. First, this option detects if the subtitle is auto-generated (from youtube) before proceeding
     * @param ignoreEmptyFrames           Ignore empty frame/lines in the output subtitle, this option is normally used with detectYoutubeDuplicateLines = true
     * @return true, if the file is parsed and written correctly
     */
    public static boolean Dump(InputStream source, MediaFormat format, File outputFile,
                               boolean toSubRip, TimestampParsingOption parseOption, String languageCode,
                               boolean detectYoutubeDuplicateLines, boolean ignoreEmptyFrames) {
        switch (format) {
            case VTT:
            case TTML:
            case TRANSCRIPT3:
                break;
            default:
                throw new UnsupportedOperationException("Can't convert this subtitle, unimplemented subtitle format: " + format.getName());
        }
       
        FileOutputStream file_stream = null;
        Charset chrst = Charset.forName("UTF-8");

        try {
            file_stream = new FileOutputStream(outputFile);// OutputStreamWriter doesn't appear work in android >=4.4

            // prepare srt or vtt callback
            FrameWriter callback;
            final FileOutputStream writer = file_stream;

            if (toSubRip) {
                int[] frame_index = {0};// ugly workaround

                callback = (SubtitleFrame frame) -> {
                    if (ignoreEmptyFrames && frame.isEmptyText()) {
                        return;
                    }
                    writeString(writer, chrst, String.valueOf(frame_index[0]++));
                    writeString(writer, chrst, NEW_LINE);
                    writeString(writer, chrst, getTime(frame.start, true));
                    writeString(writer, chrst, " --> ");
                    writeString(writer, chrst, getTime(frame.end, true));
                    writeString(writer, chrst, NEW_LINE);
                    writeString(writer, chrst, frame.text);
                    writeString(writer, chrst, NEW_LINE);
                    writeString(writer, chrst, NEW_LINE);
                };
            } else {
                StringBuilder buffer = new StringBuilder(64);
                writeString(writer, chrst, "WEBVTT");
                writeString(writer, chrst, NEW_LINE);

                callback = (SubtitleFrame frame) -> {
                    if (ignoreEmptyFrames && frame.isEmptyText()) {
                        return;
                    }

                    buffer.setLength(0);
                    buffer.append(frame.text);

                    // box any bold, italic or underline tag before the string escape  and then un-box it
                    tagBoxing(buffer, true);
                    frame.text = XmlDocument.escapeXML(buffer.toString(), USE_XML10_ESCAPE, false, false);
                    tagBoxing(buffer, false);

                    writeString(writer, chrst, getTime(frame.start, false));
                    writeString(writer, chrst, " --> ");
                    writeString(writer, chrst, getTime(frame.end, false));
                    writeString(writer, chrst, NEW_LINE);
                    writeString(writer, chrst, frame.text);
                    writeString(writer, chrst, NEW_LINE);
                    writeString(writer, chrst, NEW_LINE);
                };
            }

            // parse subtitles
            switch (format) {
                case VTT:
                    BufferedReader reader = new BufferedReader(new InputStreamReader(source), BUFFER_SIZE);
                    read_vtt(reader, callback, detectYoutubeDuplicateLines, parseOption, languageCode);
                    break;
                case TRANSCRIPT3:
                    read_transcript_v3(source, callback);// is fine without detectYoutubeDuplicateLines
                    break;
                case TTML:
                    read_ttml(source, callback, detectYoutubeDuplicateLines);
                    break;
            }

            return true;
        } catch (Exception err) {
            err.printStackTrace();
            return false;
        } finally {
            /*
            if (source != null) {
                try {
                    source.close();
                } catch (Exception e) {
                }
            }
            */
            if (file_stream != null) {
                try {
                    file_stream.close();
                } catch (Exception e) {
                }
            }
        }
    }


    // ***************************** //
    //            readers            //
    // ***************************** //

    private static void read_vtt(BufferedReader reader, FrameWriter callback, boolean detectYoutubeDuplicateLines,
                                 TimestampParsingOption parseOption, String languageCode) throws IOException, ParseException {
        /*
         * the support is PARTIAL
         * CSS pseudo-classes is supported
         * Cue payload text tags is supported
         * Region is not supported
         * Any type of metadata frame will break the output subtitle
         * voices tags are striped
         * DEFAULT LANGUAGE is not checked, this algorithm trusts the server and is sending subtitles with the required language
         *
         * the timestamp can be confused with a cue.
         * the easy (and slow) way to detect the timestamp is using regular expressions
         * this can fail if the regex is malformed or new WebVTT format is implemented
         * the (?:) parts is a no-match group
         */

        boolean skip = false;
        boolean enable_ignore_first_line = false;
        Pattern rx1 = Pattern.compile("((?:\\d{2}:)?\\d{2}:\\d{2}\\.\\d{3})\\s+-->\\s+((?:\\d{2}:)?\\d{2}:\\d{2}\\.\\d{3})(:?\\s+)?");
        Pattern rx2 = Pattern.compile("<((?:\\d{2}:)?\\d{2}:\\d{2}\\.\\d{3})>");
        ArrayList<Match> ts_list = parseOption == TimestampParsingOption.Ignore ? null : new ArrayList<>(2);
        StringBuilder text = new StringBuilder(128);

        String line = reader.readLine();
        if (!line.startsWith("WEBVTT")) {
            throw new IOException("WebVTT header missing");
        }

        // youtube put extra data immediately after the header, skip until a blank line is reached
        while (true) {
            line = reader.readLine();
            if (line == null) {
                return;// ¿empty subtitles?
            }
            if (line.length() < 1) {
                break;
            }
        }

        while ((line = reader.readLine()) != null) {
            line = trimEnd(line);// useful for ""handwritten"" subtitles

            if (skip) {
                skip = line.length() > 0;
                continue;
            }

            if (line.startsWith("STYLE") || line.startsWith("NOTE") || line.startsWith("REGION")) {
                skip = true;
                continue;
            } else if (line.length() < 1) {
                continue;// don't use "skip=true" to skip the next line
            }

            Matcher m = rx1.matcher(line);

            if (!m.find())// cue detected? skip
            {
                line = reader.readLine();
                if (line == null || line.length() < 1) {
                    break;// EOF reached or corrupt WebVTT data
                }

                m = rx1.matcher(line);
                if (!m.find()) {
                    /*
                     * Not implemented format, parse error, or bad regex
                     * instead of reading the next line and check it, just throw a error
                     */
                    throw new ParseException("Invalid WebVTT timestamps line", -1);
                }
            }

            //read both timestamp
            int frame_start = parseTime(m.group(1));
            int frame_end = parseTime(m.group(2));

            text.setLength(0);

            int fl_ff = -1;// first line of first frame
            int line_count = 0;
            boolean ignore_first_line = enable_ignore_first_line;

            while (true) {
                line = reader.readLine();
                if (line == null || line.length() < 1) {
                    break;
                }

                if (detectYoutubeDuplicateLines) {
                    if (ignore_first_line) {
                        ignore_first_line = false;
                        continue;
                    } else if (fl_ff < 0) {
                        fl_ff = line.length();
                    }
                }

                line_count++;
                text.append(line);
                text.append(NEW_LINE);// big dilemma: the original subtitle text can contains '\r\n' or just '\n'. And will replaced by NEW_LINE const
            }

            if (!enable_ignore_first_line && detectYoutubeDuplicateLines) {
                if (line_count < 2 || fl_ff > 1) {
                    detectYoutubeDuplicateLines = false;
                } else {
                    enable_ignore_first_line = true;// enable
                    text.delete(0, fl_ff + NEW_LINE.length());
                }
            }

            if (text.length() >= NEW_LINE.length()) {
                text.delete(text.length() - NEW_LINE.length(), text.length());// remove residual new line sequence
            }

            processLangTags(text, languageCode);// get rid of any xml tag language

            // parse timestamp tags
            if (ts_list != null) {
                Matcher mc = rx2.matcher(text);
                ts_list.clear();

                while (mc.find()) {
                    Match ts = new Match();
                    ts.start = mc.start();
                    ts.end = mc.end();
                    ts.text = mc.group();
                    ts_list.add(ts);
                }
            }

            SubtitleFrame frame = new SubtitleFrame();
            frame.start = frame_start;

            if (ts_list == null || ts_list.size() < 1) {
                frame.end = frame_end;
                frame.text = stripTags(text, 0, text.length());

                callback.yield(frame);// return the whole frame
            } else {
                frame.end = parseTimeTag(ts_list.get(0).text);
                frame.text = stripTags(text, 0, ts_list.get(0).start);
                StringBuilder last_text = parseOption == TimestampParsingOption.Accumulate ? new StringBuilder(frame.text) : null;

                callback.yield(frame);// return first chunk

                for (int i = 0; i < ts_list.size(); i++)// find and return all consecutive chunks
                {
                    frame = new SubtitleFrame();
                    frame.start = parseTimeTag(ts_list.get(i).text);

                    int str_end = i + 1;

                    frame.end = str_end >= ts_list.size() ? frame_end : parseTimeTag(ts_list.get(str_end).text);

                    str_end = str_end >= ts_list.size() ? text.length() : ts_list.get(str_end).start;

                    String result = stripTags(text, ts_list.get(i).start, str_end);

                    if (last_text == null) {
                        frame.text = result;
                    } else {
                        last_text.append(result);
                        frame.text = last_text.toString();
                    }

                    callback.yield(frame);
                }
            }
        }
    }


    private static void read_transcript_v3(InputStream reader, FrameWriter callback)
            throws XmlPullParserException, IOException, ParseException {

        read_xml_based(reader, callback, false, "timedtext", "format", "3",
                new String[]{"timedtext", "body", "p"}, "t", "d", false
        );
    }

    private static void read_ttml(InputStream reader, FrameWriter callback, boolean detectYoutubeDuplicateLines)
            throws XmlPullParserException, IOException, ParseException {

        read_xml_based(reader, callback, detectYoutubeDuplicateLines, "tt", "xmlns", "http://www.w3.org/ns/ttml",
                new String[]{"tt", "body", "div", "p"}, "begin", "end", true
        );
    }


    private static void read_xml_based(InputStream reader, FrameWriter callback, boolean detectYoutubeDuplicateLines,
                                       String root, String formatAttr, String formatVersion, String[] framePath,
                                       String timeAttr, String durationAttr, boolean hasTimestamp
    ) throws XmlPullParserException, IOException, ParseException {
        /*
         * XML based subtitles parser with BASIC support
         * multiple CUE is not supported
         * styling is not supported
         * tag timestamps (in auto-generated subtitles) are not supported, maybe in the future
         * also TimestampTagOption enum is not applicable
         * Language parsing is not supported
         */

        XmlDocument ttml = new XmlDocument(reader, BUFFER_SIZE);
        String attr;

        // get the format version or namespace
        XmlNode node = ttml.SelectSingleNode(root);
        if (node == null) {
            throw new ParseException("Can't get the format version. ¿wrong namespace?", -1);
        }

        if (formatAttr.equals("xmlns")) {
            if (!node.getNameSpace().equals(formatVersion)) {
                throw new UnsupportedOperationException("Expected xml namespace: " + formatVersion);
            }
        } else {
            attr = node.getAttribute(formatAttr);
            if (attr == null) {
                throw new ParseException("Can't get the format attribute", -1);
            }
            if (!attr.equals(formatVersion)) {
                throw new ParseException("Invalid format version : " + attr, -1);
            }
        }


        XmlNodeList node_list;

        int line_break = 0;// Maximum characters per line if present (valid for TranScript v3)

        if (!hasTimestamp) {
            node_list = ttml.SelectNodes("timedtext", "head", "wp");

            if (node_list != null) {
                // if the subtitle has multiple CUEs, use the highest value
                while ((node = node_list.getNextNode()) != null) {
                    try {
                        int tmp = Integer.parseInt(node.getAttribute("ah"));
                        if (tmp > line_break) {
                            line_break = tmp;
                        }
                    } catch (NumberFormatException err) {
                    }
                }
            }
        }

        // parse every frame
        node_list = ttml.SelectNodes(framePath);

        if (node_list == null) {
            return;// no frames detected
        }

        int fs_ff = -1;// first timestamp of first frame
        boolean limit_lines = false;

        while ((node = node_list.getNextNode()) != null) {
            SubtitleFrame obj = new SubtitleFrame();
            obj.text = node.getInnerText();

            attr = node.getAttribute(timeAttr);// ¡this cant be null!
            obj.start = hasTimestamp ? parseTimestamp(attr) : Integer.parseInt(attr);

            attr = node.getAttribute(durationAttr);
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

            if (/*node.getAttribute("w").equals("1") &&*/ line_break > 1 && obj.text.length() > line_break) {

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


    // ***************************** //
    //        method helpers         //
    // ***************************** //

    private static int parseTimeTag(String str) throws NumberFormatException, ParseException {
        return parseTime(str.substring(1, str.length() - 1));
    }

    private static int parseTime(String str) throws NumberFormatException, ParseException {
        int time = 0;
        String[] units = str.split(":");// ¿by regex instead of Char? srly
        // Array.Reverse(units); (no reverse on java)

        switch (units.length) {
            case 3:
                time += Integer.parseInt(units[units.length - 3]) * 3600000;
            case 2:
                time += Integer.parseInt(units[units.length - 2]) * 60000;
            case 1:
                time += Integer.parseInt(units[units.length - 1].replace(".", "")); // worldwide decimal parsing ™
                break;
            default:
                throw new ParseException("Invalid WebVTT timestamp length", units.length);
        }

        return time;
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

    private static String getTime(int time, boolean comma) {
        // cast every value to integrer to avoid auto-round in ToString("00").
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

    private static void putBreakAt(int idx, StringBuilder str) {
        // this should be optimized at compile time

        if (NEW_LINE.length() > 1) {
            str.delete(idx, idx + 1);// remove after replace
            str.insert(idx, NEW_LINE);
        } else {
            str.setCharAt(idx, NEW_LINE.charAt(0));
        }
    }

    private static boolean checkXmlTagCloseAt(int idx, StringBuilder buffer) {
        return (idx + 1) < buffer.length() && buffer.charAt(idx) == '/' && buffer.charAt(idx + 1) == '>';
    }

    private static void tagBoxing(StringBuilder str, boolean boxOrUnbox) {
        String[] b1 = new String[]{"<b>", "\u0003b\u0004"};
        String[] i1 = new String[]{"<i>", "\u0003i\u0004"};
        String[] u1 = new String[]{"<u>", "\u0003u\u0004"};
        String[] b2 = new String[]{"</b>", "\u0003/b\u0004"};
        String[] i2 = new String[]{"</i>", "\u0003/i\u0004"};
        String[] u2 = new String[]{"</u>", "\u0003/u\u0004"};

        int o1 = boxOrUnbox ? 0 : 1;
        int o2 = boxOrUnbox ? 1 : 0;

        replace(str, b1[o1], b2[o2]);
        replace(str, i1[o1], i2[o2]);
        replace(str, u1[o1], u2[o2]);
    }

    private static String stripTags(StringBuilder str, int start, int end) {
        if ((end - start) < 3) {
            return str.substring(start, end);// string too short to have tags
        }

        StringBuilder text = new StringBuilder(str.substring(start, end));

        int idx = -1;
        for (int i = 0; i < text.length(); i++) {
            if (idx > -1) {
                if (text.charAt(i) == '>') {
                    int length = i - idx + 1;
                    remove(text, idx, length);
                    i -= length;
                    idx = -1;
                }
            } else {
                if (text.charAt(i) == '<') {
                    if ((i + 2) >= text.length()) {
                        return text.toString();// bad xml data
                    }

                    boolean flag = text.charAt(i + 1) == '/';// detect tag close
                    if (flag) {
                        i++;
                    }

                    boolean skip = false;

                    switch (text.charAt(i + 1))// bypass for bold, italic and underline
                    {
                        default:
                            if (flag) {
                                i--;
                            }
                            break;
                        case 'b':
                        case 'i':
                        case 'u':
                            i += 2;
                            boolean whitespace = false;
                            switch (text.charAt(i)) {
                                case '\r':
                                case '\n':
                                case '\t':
                                case ' ':
                                    whitespace = true;// check for "<b   >" or "</b  >"
                                    break;
                            }

                            if (text.charAt(i) == '>' || checkXmlTagCloseAt(i, text)) {
                                skip = true;
                                break;
                            }

                            if (!whitespace && text.charAt(i) != '.' && text.charAt(i) != '>' && !checkXmlTagCloseAt(i, text)) {
                                break;
                            }

                            if (whitespace || text.charAt(i) == '.')// strip class or attributes
                            {
                                int class_start = i;
                                i++;

                                for (; i < text.length(); i++) {
                                    if (text.charAt(i) == '>' || checkXmlTagCloseAt(i, text)) {
                                        remove(text, class_start, i - class_start);
                                        i -= i - class_start;
                                        skip = true;
                                        break;
                                    }
                                }
                            }
                            break;
                    }

                    if (skip) {
                        continue;
                    }
                    idx = i;
                }
            }
        }

        return XmlDocument.unescapeXML(text);
    }

    private static void processLangTags(StringBuilder str, String lang) {
        if (lang == null || lang.length() < 1) {
            return;// do nothing
        }

        int i = 0;
        Tag tag;

        while ((tag = findXmlTag(str, "lang", true, i)) != null) {
            String value = str.substring(tag.start + 5, tag.end - (tag.selfClose ? 2 : 1)).trim();// get language code
            boolean lang_check = !value.endsWith(lang);// check language
            boolean lang_not_empty = value.length() > 0 && lang_check;
            int len = str.length();

            str.delete(tag.start, tag.end);// delete tag start

            if (tag.selfClose && lang_not_empty) {
                str.delete(tag.start, str.length());
                continue;
            }

            int lang_start = tag.start;
            tag = findXmlTag(str, "lang", false, lang_start);

            if (tag == null) {
                str.delete(lang_start, str.length());
                return;
            } else {
                str.delete(lang_not_empty ? lang_start : tag.start, tag.end);
            }

            i = lang_start;// set position on the end of striped text
        }
    }

    private static Tag findXmlTag(StringBuilder buffer, String name, boolean openOrClose, int startIndex) {
        for (; startIndex < buffer.length(); startIndex++) {
            if (buffer.charAt(startIndex) == '<') {
                if ((startIndex + 2) >= buffer.length()) {
                    return null;// truncated tag (bad xml data)
                }

                boolean close = buffer.charAt(startIndex + 1) == '/';

                if (!openOrClose && close) {
                    startIndex++;
                }

                if (openOrClose == close) {
                    continue;
                }

                boolean escape = false;
                startIndex++;

                while (startIndex < buffer.length()) {
                    switch (buffer.charAt(startIndex)) {
                        case '\r':
                        case '\n':
                        case '\t':
                        case ' ':
                            return null;// illegal spaces on tag (bad xml data)
                    }

                    // read tag name
                    int idx = startIndex;
                    for (; idx < buffer.length(); idx++) {
                        if (buffer.charAt(idx) == '>') {
                            Tag result = new Tag();
                            result.start = startIndex + (openOrClose ? -1 : -2);
                            result.end = idx + 1;
                            result.selfClose = openOrClose && (buffer.charAt(idx - 1) == '/');

                            // read the current tag name
                            int len = startIndex + name.length();
                            if (len > idx || !buffer.substring(startIndex, len).equals(name)) {
                                startIndex = idx + 1;
                                escape = true;// find another tag
                                break;
                            }

                            return result;
                        }
                    }

                    if (escape) {
                        break;// if escape, find another xml tag
                    }

                    return null;// '>' character not reached (bad xml tag)
                }
            }
        }

        return null;// no xml tag found
    }

    private static String trimEnd(String str) {
        for (int i = str.length() - 1; i >= 0; i--) {
            switch (str.charAt(i)) {
                case ' ':
                case '\t':
                case '\r':
                case '\n':
                    break;
                default:
                    return str.substring(0, i + 1);
            }
        }

        return str;
    }

    private static void remove(StringBuilder buffer, int startIndex, int length) {
        buffer.delete(startIndex, startIndex + length);//todo check it
    }

    private static void replace(StringBuilder buffer, String oldValue, String newValue) {
        int i = 0;
        while ((i = buffer.indexOf(oldValue, i)) != -1) {
            buffer.replace(i, i + oldValue.length(), newValue);
            i += newValue.length();
        }
    }

    private static String numberToString(int nro, int pad) {
        return String.format(Locale.ENGLISH, "%0".concat(String.valueOf(pad)).concat("d"), nro);
    }

    private static void writeString(FileOutputStream fw, Charset cs, String str) throws IOException {
        fw.write(str.getBytes(cs));
    }

    private static void copy(InputStream source, File output) throws IOException {
        FileOutputStream fw = null;

        try {
            fw = new FileOutputStream(output);
            int read;
            byte[] buffer = new byte[BUFFER_SIZE];
            while ((read = source.read(buffer, 0, buffer.length)) > 0) {
                fw.write(buffer, 0, read);
            }
        } finally {
            if (fw != null) {
                fw.close();
            }
        }
    }


}

// ***************************** //
//        object helpers         //
// ***************************** //

interface FrameWriter {
    void yield(SubtitleFrame frame) throws IOException;
}

class Match {
    public int start;
    public int end;
    public String text;
}

class Tag {
    public int start;
    public int end;
    public boolean selfClose;
}

class SubtitleFrame {
    //Java no support unsigned integers
    public int end;
    public int start;
    public String text = "";

    public boolean isEmptyText() {
        if (text == null) return true;

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

