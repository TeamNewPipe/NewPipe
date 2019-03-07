package us.shandian.giga.postprocessing;

import org.schabi.newpipe.streams.WebMReader.TrackKind;
import org.schabi.newpipe.streams.WebMReader.WebMTrack;
import org.schabi.newpipe.streams.WebMWriter;
import org.schabi.newpipe.streams.io.SharpStream;

import java.io.IOException;

import us.shandian.giga.get.DownloadMission;

/**
 * @author kapodamy
 */
class WebMMuxer extends Postprocessing {

    WebMMuxer(DownloadMission mission) {
        super(mission, 2048 * 1024/* 2 MiB */, true);
    }

    @Override
    int process(SharpStream out, SharpStream... sources) throws IOException {
        WebMWriter muxer = new WebMWriter(sources);
        muxer.parseSources();

        // youtube uses a webm with a fake video track that acts as a "cover image"
        WebMTrack[] tracks = muxer.getTracksFromSource(1);
        int audioTrackIndex = 0;
        for (int i = 0; i < tracks.length; i++) {
            if (tracks[i].kind == TrackKind.Audio) {
                audioTrackIndex = i;
                break;
            }
        }

        muxer.selectTracks(0, audioTrackIndex);
        muxer.build(out);

        return OK_RESULT;
    }

}
