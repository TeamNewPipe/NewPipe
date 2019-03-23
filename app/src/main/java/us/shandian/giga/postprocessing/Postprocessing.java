package us.shandian.giga.postprocessing;

import android.os.Message;
import android.util.Log;

import org.schabi.newpipe.streams.io.SharpStream;

import java.io.File;
import java.io.IOException;

import us.shandian.giga.get.DownloadMission;
import us.shandian.giga.postprocessing.io.ChunkFileInputStream;
import us.shandian.giga.postprocessing.io.CircularFileWriter;
import us.shandian.giga.postprocessing.io.CircularFileWriter.OffsetChecker;
import us.shandian.giga.service.DownloadManagerService;

import static us.shandian.giga.get.DownloadMission.ERROR_NOTHING;
import static us.shandian.giga.get.DownloadMission.ERROR_POSTPROCESSING_HOLD;
import static us.shandian.giga.get.DownloadMission.ERROR_UNKNOWN_EXCEPTION;

public abstract class Postprocessing {

    static final byte OK_RESULT = ERROR_NOTHING;

    public static final String ALGORITHM_TTML_CONVERTER = "ttml";
    public static final String ALGORITHM_WEBM_MUXER = "webm";
    public static final String ALGORITHM_MP4_FROM_DASH_MUXER = "mp4D-mp4";
    public static final String ALGORITHM_M4A_NO_DASH = "mp4D-m4a";

    public static Postprocessing getAlgorithm(String algorithmName, DownloadMission mission) {
        if (null == algorithmName) {
            throw new NullPointerException("algorithmName");
        } else switch (algorithmName) {
            case ALGORITHM_TTML_CONVERTER:
                return new TtmlConverter(mission);
            case ALGORITHM_WEBM_MUXER:
                return new WebMMuxer(mission);
            case ALGORITHM_MP4_FROM_DASH_MUXER:
                return new Mp4FromDashMuxer(mission);
            case ALGORITHM_M4A_NO_DASH:
                return new M4aNoDash(mission);
            /*case "example-algorithm":
                return new ExampleAlgorithm(mission);*/
            default:
                throw new RuntimeException("Unimplemented post-processing algorithm: " + algorithmName);
        }
    }

    /**
     * Get a boolean value that indicate if the given algorithm work on the same
     * file
     */
    public boolean worksOnSameFile;

    /**
     * Get the recommended space to reserve for the given algorithm. The amount
     * is in bytes
     */
    public int recommendedReserve;

    /**
     * the download to post-process
     */
    protected DownloadMission mission;

    Postprocessing(DownloadMission mission, int recommendedReserve, boolean worksOnSameFile) {
        this.mission = mission;
        this.recommendedReserve = recommendedReserve;
        this.worksOnSameFile = worksOnSameFile;
    }

    public void run() throws IOException {
        File file = mission.getDownloadedFile();
        File temp = null;
        CircularFileWriter out = null;
        int result;
        long finalLength = -1;

        mission.done = 0;
        mission.length = file.length();

        if (worksOnSameFile) {
            ChunkFileInputStream[] sources = new ChunkFileInputStream[mission.urls.length];
            try {
                int i = 0;
                for (; i < sources.length - 1; i++) {
                    sources[i] = new ChunkFileInputStream(file, mission.offsets[i], mission.offsets[i + 1], "rw");
                }
                sources[i] = new ChunkFileInputStream(file, mission.offsets[i], mission.getDownloadedFile().length(), "rw");

                if (test(sources)) {
                    for (SharpStream source : sources) source.rewind();

                    OffsetChecker checker = () -> {
                        for (ChunkFileInputStream source : sources) {
                            /*
                             * WARNING: never use rewind() in any chunk after any writing (especially on first chunks)
                             *          or the CircularFileWriter can lead to unexpected results
                             */
                            if (source.isDisposed() || source.available() < 1) {
                                continue;// the selected source is not used anymore
                            }

                            return source.getFilePointer() - 1;
                        }

                        return -1;
                    };

                    temp = new File(mission.location, mission.name + ".tmp");

                    out = new CircularFileWriter(file, temp, checker);
                    out.onProgress = this::progressReport;

                    out.onWriteError = (err) -> {
                        mission.postprocessingState = 3;
                        mission.notifyError(ERROR_POSTPROCESSING_HOLD, err);

                        try {
                            synchronized (this) {
                                while (mission.postprocessingState == 3)
                                    wait();
                            }
                        } catch (InterruptedException e) {
                            // nothing to do
                            Log.e(this.getClass().getSimpleName(), "got InterruptedException");
                        }

                        return mission.errCode == ERROR_NOTHING;
                    };

                    result = process(out, sources);

                    if (result == OK_RESULT)
                        finalLength = out.finalizeFile();
                } else {
                    result = OK_RESULT;
                }
            } finally {
                for (SharpStream source : sources) {
                    if (source != null && !source.isDisposed()) {
                        source.dispose();
                    }
                }
                if (out != null) {
                    out.dispose();
                }
                if (temp != null) {
                    //noinspection ResultOfMethodCallIgnored
                    temp.delete();
                }
            }
        } else {
            result = test() ? process(null) : OK_RESULT;
        }

        if (result == OK_RESULT) {
            if (finalLength != -1) {
                mission.done = finalLength;
                mission.length = finalLength;
            }
        } else {
            mission.errCode = ERROR_UNKNOWN_EXCEPTION;
            mission.errObject = new RuntimeException("post-processing algorithm returned " + result);
        }

        if (result != OK_RESULT && worksOnSameFile) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    /**
     * Test if the post-processing algorithm can be skipped
     *
     * @param sources files to be processed
     * @return {@code true} if the post-processing is required, otherwise, {@code false}
     * @throws IOException if an I/O error occurs.
     */
    boolean test(SharpStream... sources) throws IOException {
        return true;
    }

    /**
     * Abstract method to execute the post-processing algorithm
     *
     * @param out     output stream
     * @param sources files to be processed
     * @return a error code, 0 means the operation was successful
     * @throws IOException if an I/O error occurs.
     */
    abstract int process(SharpStream out, SharpStream... sources) throws IOException;

    String getArgumentAt(int index, String defaultValue) {
        if (mission.postprocessingArgs == null || index >= mission.postprocessingArgs.length) {
            return defaultValue;
        }

        return mission.postprocessingArgs[index];
    }

    private void progressReport(long done) {
        mission.done = done;
        if (mission.length < mission.done) mission.length = mission.done;

        Message m = new Message();
        m.what = DownloadManagerService.MESSAGE_PROGRESS;
        m.obj = mission;

        mission.mHandler.sendMessage(m);
    }
}
