package us.shandian.giga.postprocessing;

import android.util.Log;

import org.schabi.newpipe.streams.io.SharpStream;

import java.io.IOException;
import java.util.Random;

import us.shandian.giga.get.DownloadMission;

/**
 * Algorithm for testing proposes
 */
class TestAlgo extends Postprocessing {

    public TestAlgo(DownloadMission mission) {
        super(mission);

        worksOnSameFile = true;
        recommendedReserve = 4096 * 1024;// 4 KiB
    }

    @Override
    int process(SharpStream out, SharpStream... sources) throws IOException {

        int written = 0;
        int size = 5 * 1024 * 1024;// 5 MiB
        byte[] buffer = new byte[8 * 1024];//8 KiB
        mission.length = size;

        Random rnd = new Random();

        // only write random data
        sources[0].dispose();

        while (written < size) {
            rnd.nextBytes(buffer);

            int read = Math.min(buffer.length, size - written);
            out.write(buffer, 0, read);

            try {
                Thread.sleep((int) (Math.random() * 10));
            } catch (InterruptedException e) {
                return -1;
            }

            written += read;
        }

        return Postprocessing.OK_RESULT;
    }
}
