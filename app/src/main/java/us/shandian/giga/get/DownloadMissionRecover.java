package us.shandian.giga.get;

import android.util.Log;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.nio.channels.ClosedByInterruptException;
import java.util.List;

import us.shandian.giga.get.DownloadMission.HttpError;

import static us.shandian.giga.get.DownloadMission.ERROR_RESOURCE_GONE;

public class DownloadMissionRecover extends Thread {
    private static final String TAG = "DownloadMissionRecover";
    static final int mID = -3;

    private final DownloadMission mMission;
    private final boolean mNotInitialized;

    private final int mErrCode;

    private HttpURLConnection mConn;
    private MissionRecoveryInfo mRecovery;
    private StreamExtractor mExtractor;

    DownloadMissionRecover(DownloadMission mission, int errCode) {
        mMission = mission;
        mNotInitialized = mission.blocks == null && mission.current == 0;
        mErrCode = errCode;
    }

    @Override
    public void run() {
        if (mMission.source == null) {
            mMission.notifyError(mErrCode, null);
            return;
        }

        Exception err = null;
        int attempt = 0;

        while (attempt++ < mMission.maxRetry) {
            try {
                tryRecover();
                return;
            } catch (InterruptedIOException | ClosedByInterruptException e) {
                return;
            } catch (Exception e) {
                if (!mMission.running || super.isInterrupted()) return;
                err = e;
            }
        }

        // give up
        mMission.notifyError(mErrCode, err);
    }

    private void tryRecover() throws ExtractionException, IOException, HttpError {
        if (mExtractor == null) {
            try {
                StreamingService svr = NewPipe.getServiceByUrl(mMission.source);
                mExtractor = svr.getStreamExtractor(mMission.source);
                mExtractor.fetchPage();
            } catch (ExtractionException e) {
                mExtractor = null;
                throw e;
            }
        }

        // maybe the following check is redundant
        if (!mMission.running || super.isInterrupted()) return;

        if (!mNotInitialized) {
            // set the current download url to null in case if the recovery
            // process is canceled. Next time start() method is called the
            // recovery will be executed, saving time
            mMission.urls[mMission.current] = null;

            mRecovery = mMission.recoveryInfo[mMission.current];
            resolveStream();
            return;
        }

        Log.w(TAG, "mission is not fully initialized, this will take a while");

        try {
            for (; mMission.current < mMission.urls.length; mMission.current++) {
                mRecovery = mMission.recoveryInfo[mMission.current];

                if (test()) continue;
                if (!mMission.running) return;

                resolveStream();
                if (!mMission.running) return;

                // before continue, check if the current stream was resolved
                if (mMission.urls[mMission.current] == null) {
                    break;
                }
            }
        } finally {
            mMission.current = 0;
        }

        mMission.writeThisToFile();

        if (!mMission.running || super.isInterrupted()) return;

        mMission.running = false;
        mMission.start();
    }

    private void resolveStream() throws IOException, ExtractionException, HttpError {
        // FIXME: this getErrorMessage() always returns "video is unavailable"
        /*if (mExtractor.getErrorMessage() != null) {
            mMission.notifyError(mErrCode, new ExtractionException(mExtractor.getErrorMessage()));
            return;
        }*/

        String url = null;

        switch (mRecovery.kind) {
            case 'a':
                for (AudioStream audio : mExtractor.getAudioStreams()) {
                    if (audio.average_bitrate == mRecovery.desiredBitrate && audio.getFormat() == mRecovery.format) {
                        url = audio.getUrl();
                        break;
                    }
                }
                break;
            case 'v':
                List<VideoStream> videoStreams;
                if (mRecovery.desired2)
                    videoStreams = mExtractor.getVideoOnlyStreams();
                else
                    videoStreams = mExtractor.getVideoStreams();
                for (VideoStream video : videoStreams) {
                    if (video.resolution.equals(mRecovery.desired) && video.getFormat() == mRecovery.format) {
                        url = video.getUrl();
                        break;
                    }
                }
                break;
            case 's':
                for (SubtitlesStream subtitles : mExtractor.getSubtitles(mRecovery.format)) {
                    String tag = subtitles.getLanguageTag();
                    if (tag.equals(mRecovery.desired) && subtitles.isAutoGenerated() == mRecovery.desired2) {
                        url = subtitles.getURL();
                        break;
                    }
                }
                break;
            default:
                throw new RuntimeException("Unknown stream type");
        }

        resolve(url);
    }

    private void resolve(String url) throws IOException, HttpError {
        if (mRecovery.validateCondition == null) {
            Log.w(TAG, "validation condition not defined, the resource can be stale");
        }

        if (mMission.unknownLength || mRecovery.validateCondition == null) {
            recover(url, false);
            return;
        }

        ///////////////////////////////////////////////////////////////////////
        ////// Validate the http resource doing a range request
        /////////////////////
        try {
            mConn = mMission.openConnection(url, true, mMission.length - 10, mMission.length);
            mConn.setRequestProperty("If-Range", mRecovery.validateCondition);
            mMission.establishConnection(mID, mConn);

            int code = mConn.getResponseCode();

            switch (code) {
                case 200:
                case 413:
                    // stale
                    recover(url, true);
                    return;
                case 206:
                    // in case of validation using the Last-Modified date, check the resource length
                    long[] contentRange = parseContentRange(mConn.getHeaderField("Content-Range"));
                    boolean lengthMismatch = contentRange[2] != -1 && contentRange[2] != mMission.length;

                    recover(url, lengthMismatch);
                    return;
            }

            throw new HttpError(code);
        } finally {
            disconnect();
        }
    }

    private void recover(String url, boolean stale) {
        Log.i(TAG,
                String.format("recover()  name=%s  isStale=%s  url=%s", mMission.storage.getName(), stale, url)
        );

        mMission.urls[mMission.current] = url;

        if (url == null) {
            mMission.urls = new String[0];
            mMission.notifyError(ERROR_RESOURCE_GONE, null);
            return;
        }

        if (mNotInitialized) return;

        if (stale) {
            mMission.resetState(false, false, DownloadMission.ERROR_NOTHING);
        }

        mMission.writeThisToFile();

        if (!mMission.running || super.isInterrupted()) return;

        mMission.running = false;
        mMission.start();
    }

    private long[] parseContentRange(String value) {
        long[] range = new long[3];

        if (value == null) {
            // this never should happen
            return range;
        }

        try {
            value = value.trim();

            if (!value.startsWith("bytes")) {
                return range;// unknown range type
            }

            int space = value.lastIndexOf(' ') + 1;
            int dash = value.indexOf('-', space) + 1;
            int bar = value.indexOf('/', dash);

            // start
            range[0] = Long.parseLong(value.substring(space, dash - 1));

            // end
            range[1] = Long.parseLong(value.substring(dash, bar));

            // resource length
            value = value.substring(bar + 1);
            if (value.equals("*")) {
                range[2] = -1;// unknown length received from the server but should be valid
            } else {
                range[2] = Long.parseLong(value);
            }
        } catch (Exception e) {
            // nothing to do
        }

        return range;
    }

    private boolean test() {
        if (mMission.urls[mMission.current] == null) return false;

        try {
            mConn = mMission.openConnection(mMission.urls[mMission.current], true, -1, -1);
            mMission.establishConnection(mID, mConn);

            if (mConn.getResponseCode() == 200) return true;
        } catch (Exception e) {
            // nothing to do
        } finally {
            disconnect();
        }

        return false;
    }

    private void disconnect() {
        try {
            try {
                mConn.getInputStream().close();
            } finally {
                mConn.disconnect();
            }
        } catch (Exception e) {
            // nothing to do
        } finally {
            mConn = null;
        }
    }

    @Override
    public void interrupt() {
        super.interrupt();
        if (mConn != null) disconnect();
    }
}
