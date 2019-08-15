package us.shandian.giga.postprocessing;

import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Log;

import org.schabi.newpipe.streams.io.SharpStream;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

import us.shandian.giga.get.DownloadMission;
import us.shandian.giga.io.ChunkFileInputStream;
import us.shandian.giga.io.CircularFileWriter;
import us.shandian.giga.io.CircularFileWriter.OffsetChecker;
import us.shandian.giga.service.DownloadManagerService;

import static us.shandian.giga.get.DownloadMission.ERROR_NOTHING;
import static us.shandian.giga.get.DownloadMission.ERROR_POSTPROCESSING_HOLD;
import static us.shandian.giga.get.DownloadMission.ERROR_UNKNOWN_EXCEPTION;

public abstract class Postprocessing implements Serializable {

    static transient final byte OK_RESULT = ERROR_NOTHING;

    public transient static final String ALGORITHM_TTML_CONVERTER = "ttml";
    public transient static final String ALGORITHM_WEBM_MUXER = "webm";
    public transient static final String ALGORITHM_MP4_FROM_DASH_MUXER = "mp4D-mp4";
    public transient static final String ALGORITHM_M4A_NO_DASH = "mp4D-m4a";

    public static Postprocessing getAlgorithm(@NonNull String algorithmName, String[] args) {
        Postprocessing instance;

        switch (algorithmName) {
            case ALGORITHM_TTML_CONVERTER:
                instance = new TtmlConverter();
                break;
            case ALGORITHM_WEBM_MUXER:
                instance = new WebMMuxer();
                break;
            case ALGORITHM_MP4_FROM_DASH_MUXER:
                instance = new Mp4FromDashMuxer();
                break;
            case ALGORITHM_M4A_NO_DASH:
                instance = new M4aNoDash();
                break;
            /*case "example-algorithm":
                instance = new ExampleAlgorithm();*/
            default:
                throw new UnsupportedOperationException("Unimplemented post-processing algorithm: " + algorithmName);
        }

        instance.args = args;
        return instance;
    }

    /**
     * Get a boolean value that indicate if the given algorithm work on the same
     * file
     */
    public final boolean worksOnSameFile;

    /**
     * Indicates whether the selected algorithm needs space reserved at the beginning of the file
     */
    public final boolean reserveSpace;

    /**
     * Gets the given algorithm short name
     */
    private final String name;


    private String[] args;

    protected transient DownloadMission mission;

    private File tempFile;

    Postprocessing(boolean reserveSpace, boolean worksOnSameFile, String algorithmName) {
        this.reserveSpace = reserveSpace;
        this.worksOnSameFile = worksOnSameFile;
        this.name = algorithmName;// for debugging only
    }

    public void setTemporalDir(@NonNull File directory) {
        long rnd = (int) (Math.random() * 100000f);
        tempFile = new File(directory, rnd + "_" + System.nanoTime() + ".tmp");
    }

    public void cleanupTemporalDir() {
        if (tempFile != null && tempFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            tempFile.delete();
        }
    }


    public void run(DownloadMission target) throws IOException {
        this.mission = target;

        CircularFileWriter out = null;
        int result;
        long finalLength = -1;

        mission.done = 0;
        mission.length = mission.storage.length();

        if (worksOnSameFile) {
            ChunkFileInputStream[] sources = new ChunkFileInputStream[mission.urls.length];
            try {
                int i = 0;
                for (; i < sources.length - 1; i++) {
                    sources[i] = new ChunkFileInputStream(mission.storage.getStream(), mission.offsets[i], mission.offsets[i + 1]);
                }
                sources[i] = new ChunkFileInputStream(mission.storage.getStream(), mission.offsets[i]);

                if (test(sources)) {
                    for (SharpStream source : sources) source.rewind();

                    OffsetChecker checker = () -> {
                        for (ChunkFileInputStream source : sources) {
                            /*
                             * WARNING: never use rewind() in any chunk after any writing (especially on first chunks)
                             *          or the CircularFileWriter can lead to unexpected results
                             */
                            if (source.isClosed() || source.available() < 1) {
                                continue;// the selected source is not used anymore
                            }

                            return source.getFilePointer() - 1;
                        }

                        return -1;
                    };

                    out = new CircularFileWriter(mission.storage.getStream(), tempFile, checker);
                    out.onProgress = this::progressReport;

                    out.onWriteError = (err) -> {
                        mission.psState = 3;
                        mission.notifyError(ERROR_POSTPROCESSING_HOLD, err);

                        try {
                            synchronized (this) {
                                while (mission.psState == 3)
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
                    if (source != null && !source.isClosed()) {
                        source.close();
                    }
                }
                if (out != null) {
                    out.close();
                }
                if (tempFile != null) {
                    //noinspection ResultOfMethodCallIgnored
                    tempFile.delete();
                    tempFile = null;
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

        if (result != OK_RESULT && worksOnSameFile) mission.storage.delete();

        this.mission = null;
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
        if (args == null || index >= args.length) {
            return defaultValue;
        }

        return args[index];
    }

    private void progressReport(long done) {
        mission.done = done;
        if (mission.length < mission.done) mission.length = mission.done;

        Message m = new Message();
        m.what = DownloadManagerService.MESSAGE_PROGRESS;
        m.obj = mission;

        mission.mHandler.sendMessage(m);
    }

    @NonNull
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("name=").append(name).append('[');

        if (args != null) {
            for (String arg : args) {
                str.append(", ");
                str.append(arg);
            }
            str.delete(0, 1);
        }

        return str.append(']').toString();
    }
}
