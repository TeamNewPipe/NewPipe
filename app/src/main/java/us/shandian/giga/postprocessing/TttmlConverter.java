package us.shandian.giga.postprocessing;

import org.schabi.newpipe.extractor.utils.io.SharpStream;
import org.schabi.newpipe.extractor.utils.SubtitleConverter;

import java.io.IOException;

import us.shandian.giga.get.DownloadMission;
import us.shandian.giga.postprocessing.io.SharpInputStream;
/**
 * @author kapodamy
 */
class TttmlConverter extends Postprocessing {

    TttmlConverter(DownloadMission mission) {
        super(mission);
        recommendedReserve = 0;// due how XmlPullParser works, the xml is fully loaded on the ram
        worksOnSameFile = true;
    }

    @Override
    int process(SharpStream out, SharpStream... sources) throws IOException {
        // check if the subtitle is already in srt and copy, this should never happen
        String format = getArgumentAt(0, null);

        if (format == null || format.equals("ttml")) {
            SubtitleConverter ttmlDumper = new SubtitleConverter();

            int res = ttmlDumper.dumpTTML(
                    sources[0],
                    out,
                    getArgumentAt(1, "true").equals("true"),
                    getArgumentAt(2, "true").equals("true")
            );

            return res == 0 ? OK_RESULT : res;
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
