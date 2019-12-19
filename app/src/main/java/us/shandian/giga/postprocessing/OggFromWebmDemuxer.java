package us.shandian.giga.postprocessing;

import androidx.annotation.NonNull;

import org.schabi.newpipe.streams.OggFromWebMWriter;
import org.schabi.newpipe.streams.io.SharpStream;

import java.io.IOException;
import java.nio.ByteBuffer;

class OggFromWebmDemuxer extends Postprocessing {

    OggFromWebmDemuxer() {
        super(true, true, ALGORITHM_OGG_FROM_WEBM_DEMUXER);
    }

    @Override
    boolean test(SharpStream... sources) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        sources[0].read(buffer.array());

        // youtube uses WebM as container, but the file extension (format suffix) is "*.opus"
        // check if the file is a webm/mkv file before proceed

        switch (buffer.getInt()) {
            case 0x1a45dfa3:
                return true;// webm/mkv
            case 0x4F676753:
                return false;// ogg
        }

        throw new UnsupportedOperationException("file not recognized, failed to demux the audio stream");
    }

    @Override
    int process(SharpStream out, @NonNull SharpStream... sources) throws IOException {
        OggFromWebMWriter demuxer = new OggFromWebMWriter(sources[0], out);
        demuxer.parseSource();
        demuxer.selectTrack(0);
        demuxer.build();

        return OK_RESULT;
    }
}
