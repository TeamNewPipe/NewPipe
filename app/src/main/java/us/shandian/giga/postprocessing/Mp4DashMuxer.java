package us.shandian.giga.postprocessing;

import org.schabi.newpipe.extractor.utils.Mp4DashWriter;
import org.schabi.newpipe.extractor.utils.io.SharpStream;

import java.io.IOException;

import us.shandian.giga.get.DownloadMission;

/**
 * @author kapodamy
 */
class Mp4DashMuxer extends Postprocessing {

    Mp4DashMuxer(DownloadMission mission) {
        super(mission);
        recommendedReserve = 2048 * 1024;// 2 MiB
        worksOnSameFile = true;
    }

    @Override
    int process(SharpStream out, SharpStream... sources) throws IOException {
        Mp4DashWriter muxer = new Mp4DashWriter(sources);
        muxer.parseSources();
        muxer.selectTracks(0, 0);
        muxer.build(out);

        return OK_RESULT;
    }

}
