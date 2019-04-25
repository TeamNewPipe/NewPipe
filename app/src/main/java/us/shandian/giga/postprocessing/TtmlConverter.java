package us.shandian.giga.postprocessing;

import android.util.Log;

import org.schabi.newpipe.streams.SubtitleConverter;
import org.schabi.newpipe.streams.io.SharpStream;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.text.ParseException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

/**
 * @author kapodamy
 */
class TtmlConverter extends Postprocessing {
    private static final String TAG = "TtmlConverter";

    TtmlConverter() {
        // due how XmlPullParser works, the xml is fully loaded on the ram
        super(false, true, ALGORITHM_TTML_CONVERTER);
    }

    @Override
    int process(SharpStream out, SharpStream... sources) throws IOException {
        // check if the subtitle is already in srt and copy, this should never happen
        String format = getArgumentAt(0, null);

        if (format == null || format.equals("ttml")) {
            SubtitleConverter ttmlDumper = new SubtitleConverter();

            try {
                ttmlDumper.dumpTTML(
                        sources[0],
                        out,
                        getArgumentAt(1, "true").equals("true"),
                        getArgumentAt(2, "true").equals("true")
                );
            } catch (Exception err) {
                Log.e(TAG, "subtitle parse failed", err);

                if (err instanceof IOException) {
                    return 1;
                } else if (err instanceof ParseException) {
                    return 2;
                } else if (err instanceof SAXException) {
                    return 3;
                } else if (err instanceof ParserConfigurationException) {
                    return 4;
                } else if (err instanceof XPathExpressionException) {
                    return 7;
                }

                return 8;
            }

            return OK_RESULT;
        } else if (format.equals("srt")) {
            byte[] buffer = new byte[8 * 1024];
            int read;
            while ((read = sources[0].read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return OK_RESULT;
        }

        throw new UnsupportedOperationException("Can't convert this subtitle, unimplemented format: " + format);
    }

}
