package us.shandian.giga.postprocessing;

import org.schabi.newpipe.streams.Mp4FromDashWriter;
import org.schabi.newpipe.streams.io.SharpStream;

import java.io.IOException;

/**
 * @author kapodamy
 */
class Mp4FromDashMuxer extends Postprocessing {

    Mp4FromDashMuxer() {
        super(true, true, ALGORITHM_MP4_FROM_DASH_MUXER);
    }

    @Override
    int process(SharpStream out, SharpStream... sources) throws IOException {
        Mp4FromDashWriter muxer = new Mp4FromDashWriter(sources);
        muxer.parseSources();
        muxer.selectTracks(0, 0);
        muxer.build(out);

        return OK_RESULT;
    }

}
