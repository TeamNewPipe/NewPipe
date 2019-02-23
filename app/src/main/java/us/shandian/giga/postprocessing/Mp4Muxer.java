package us.shandian.giga.postprocessing;

import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaMuxer;
import android.media.MediaMuxer.OutputFormat;
import android.util.Log;

import static org.schabi.newpipe.BuildConfig.DEBUG;

import org.schabi.newpipe.streams.io.SharpStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import us.shandian.giga.get.DownloadMission;


class Mp4Muxer extends Postprocessing {
    private static final String TAG = "Mp4Muxer";
    private static final int NOTIFY_BYTES_INTERVAL = 128 * 1024;// 128 KiB

    Mp4Muxer(DownloadMission mission) {
        super(mission, 0, false);
    }

    @Override
    int process(SharpStream out, SharpStream... sources) throws IOException {
        File dlFile = mission.getDownloadedFile();
        File tmpFile = new File(mission.location, mission.name.concat(".tmp"));

        if (tmpFile.exists())
            if (!tmpFile.delete()) return DownloadMission.ERROR_FILE_CREATION;

        if (!tmpFile.createNewFile()) return DownloadMission.ERROR_FILE_CREATION;

        FileInputStream source = null;
        MediaMuxer muxer = null;

        //noinspection TryFinallyCanBeTryWithResources
        try {
            source = new FileInputStream(dlFile);
            MediaExtractor tracks[] = {
                    getMediaExtractor(source, mission.offsets[0], mission.offsets[1] - mission.offsets[0]),
                    getMediaExtractor(source, mission.offsets[1], mission.length - mission.offsets[1])
            };

            muxer = new MediaMuxer(tmpFile.getAbsolutePath(), OutputFormat.MUXER_OUTPUT_MPEG_4);

            int tracksIndex[] = {
                    muxer.addTrack(tracks[0].getTrackFormat(0)),
                    muxer.addTrack(tracks[1].getTrackFormat(0))
            };

            ByteBuffer buffer = ByteBuffer.allocate(512 * 1024);// 512 KiB
            BufferInfo info = new BufferInfo();

            long written = 0;
            long nextReport = NOTIFY_BYTES_INTERVAL;

            muxer.start();

            while (true) {
                int done = 0;

                for (int i = 0; i < tracks.length; i++) {
                    if (tracksIndex[i] < 0) continue;

                    info.set(0,
                            tracks[i].readSampleData(buffer, 0),
                            tracks[i].getSampleTime(),
                            tracks[i].getSampleFlags()
                    );

                    if (info.size >= 0) {
                        muxer.writeSampleData(tracksIndex[i], buffer, info);
                        written += info.size;
                        done++;
                    }
                    if (!tracks[i].advance()) {
                        // EOF reached
                        tracks[i].release();
                        tracksIndex[i] = -1;
                    }

                    if (written > nextReport) {
                        nextReport = written + NOTIFY_BYTES_INTERVAL;
                        super.progressReport(written);
                    }
                }

                if (done < 1) break;
            }

            // this part should not fail
            if (!dlFile.delete()) return DownloadMission.ERROR_FILE_CREATION;
            if (!tmpFile.renameTo(dlFile)) return DownloadMission.ERROR_FILE_CREATION;

            return OK_RESULT;
        } finally {
            try {
                if (muxer != null) {
                    muxer.stop();
                    muxer.release();
                }
            } catch (Exception err) {
                if (DEBUG)
                    Log.e(TAG, "muxer stop/release failed", err);
            }

            if (source != null) {
                try {
                    source.close();
                } catch (IOException e) {
                    // nothing to do
                }
            }

            // if the operation fails, delete the temporal file
            if (tmpFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tmpFile.delete();
            }
        }
    }

    private MediaExtractor getMediaExtractor(FileInputStream source, long offset, long length) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(source.getFD(), offset, length);
        extractor.selectTrack(0);

        return extractor;
    }
}
