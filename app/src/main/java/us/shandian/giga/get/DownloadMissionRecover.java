package us.shandian.giga.get;

import android.util.Log;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.services.bilibili.BilibiliService;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.player.helper.PlayerDataSource;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.nio.channels.ClosedByInterruptException;
import java.util.List;
import java.util.Map;

import us.shandian.giga.get.DownloadMission.HttpError;

import static us.shandian.giga.get.DownloadMission.ERROR_RESOURCE_GONE;
import static us.shandian.giga.util.Utility.setRequestPropertyIfDownloadingBilibili;

import com.grack.nanojson.JsonParserException;

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

        Stream resolvedStream = null;

        switch (mRecovery.getKind()) {
            case 'a':
                for (AudioStream audio : mExtractor.getAudioStreams()) {
                    if (matchesAudioRecovery(audio)) {
                        resolvedStream = audio;
                        break;
                    }
                }
                if (resolvedStream == null && mRecovery.isHls()) {
                    resolvedStream = HlsDownloadStreamHelper.createAudioFallback(
                            mExtractor.getVideoStreams(), mExtractor.getHlsUrl(),
                            mRecovery.getAudioTrackId());
                }
                break;
            case 'v':
                List<VideoStream> videoStreams;
                if (mRecovery.isDesired2())
                    videoStreams = mExtractor.getVideoOnlyStreams();
                else
                    videoStreams = mExtractor.getVideoStreams();
                for (VideoStream video : videoStreams) {
                    if (video.resolution.equals(mRecovery.getDesired()) && video.getFormat() == mRecovery.getFormat()) {
                        resolvedStream = video;
                    }
                }
                if (resolvedStream == null
                        && HlsDownloadStreamHelper.isManifestFallbackRecovery(mRecovery)) {
                    final String hlsUrl = mExtractor.getHlsUrl();
                    if (hlsUrl != null && !hlsUrl.isEmpty()) {
                        resolvedStream = HlsDownloadStreamHelper.createManifestFallback(hlsUrl);
                    }
                }
                break;
            case 's':
                for (SubtitlesStream subtitles : mExtractor.getSubtitles(mRecovery.getFormat())) {
                    String tag = subtitles.getLanguageTag();
                    if (tag.equals(mRecovery.getDesired()) && subtitles.isAutoGenerated() == mRecovery.isDesired2()) {
                        resolvedStream = subtitles;
                        break;
                    }
                }
                break;
            default:
                throw new RuntimeException("Unknown stream type");
        }

        resolve(resolvedStream);
    }

    private void resolve(Stream stream) throws IOException, HttpError {
        final String url = stream == null ? null : stream.getUrl();
        if (mRecovery.getValidateCondition() == null) {
            Log.w(TAG, "validation condition not defined, the resource can be stale");
        }

        if (mMission.unknownLength || mRecovery.getValidateCondition() == null) {
            recover(stream, false);
            return;
        }

        ///////////////////////////////////////////////////////////////////////
        ////// Validate the http resource doing a range request
        /////////////////////
        try {
            mConn = mMission.openConnection(url, true, mMission.length - 10, mMission.length);
            mConn.setRequestProperty("If-Range", mRecovery.getValidateCondition());
            setRequestPropertyIfDownloadingBilibili(url, mConn);
            mMission.establishConnection(mID, mConn);

            int code = mConn.getResponseCode();

            switch (code) {
                case 200:
                case 413:
                    // stale
                    recover(stream, true);
                    return;
                case 206:
                    // in case of validation using the Last-Modified date, check the resource length
                    long[] contentRange = parseContentRange(mConn.getHeaderField("Content-Range"));
                    boolean lengthMismatch = contentRange[2] != -1 && contentRange[2] != mMission.length;

                    recover(stream, lengthMismatch);
                    return;
            }

            throw new HttpError(code);
        } finally {
            disconnect();
        }
    }

    private boolean matchesAudioRecovery(final AudioStream audio) {
        if (audio.getFormat() != mRecovery.getFormat()) {
            return false;
        }

        if (mRecovery.isHls()) {
            if (audio.getDeliveryMethod() != org.schabi.newpipe.extractor.stream.DeliveryMethod.HLS) {
                return false;
            }
            final String audioTrackId = mRecovery.getAudioTrackId();
            return audioTrackId == null || audioTrackId.equals(audio.getAudioTrackId());
        }

        return audio.getAverageBitrate() == mRecovery.getDesiredBitrate();
    }

    private void recover(Stream stream, boolean stale) {
        final String url = stream == null ? null : stream.getUrl();
        Log.i(TAG,
                String.format("recover()  name=%s  isStale=%s  urlSummary=%s",
                        mMission.storage.getName(), stale, safeUrlSummary(url))
        );

        mMission.urls[mMission.current] = url;
        updateResourceMetadata(stream);

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

    private void updateResourceMetadata(Stream stream) {
        if (stream == null || mMission.current < 0 || mMission.current >= mMission.urls.length) {
            return;
        }

        if (mMission.resourceDeliveryMethods != null
                && mMission.resourceDeliveryMethods.length == mMission.urls.length) {
            mMission.resourceDeliveryMethods[mMission.current] = stream.getDeliveryMethod().name();
        }
        if (mMission.resourceManifestUrls != null
                && mMission.resourceManifestUrls.length == mMission.urls.length) {
            mMission.resourceManifestUrls[mMission.current] = stream.getManifestUrl();
        }
        if (mMission.resourceIsUrls != null
                && mMission.resourceIsUrls.length == mMission.urls.length) {
            mMission.resourceIsUrls[mMission.current] = stream.isUrl();
        }
    }

    private String safeUrlSummary(String rawUrl) {
        if (rawUrl == null) {
            return "null";
        }
        try {
            int cookieIndex = rawUrl.indexOf("#cookie=");
            String url = cookieIndex < 0 ? rawUrl : rawUrl.substring(0, cookieIndex);
            java.net.URI uri = java.net.URI.create(url);
            String path = uri.getPath() == null ? "" : uri.getPath();
            String[] segments = path.split("/");
            String first = "";
            int count = 0;
            for (String segment : segments) {
                if (segment.isEmpty()) {
                    continue;
                }
                if (first.isEmpty()) {
                    first = segment;
                }
                count++;
            }
            return "host=" + uri.getHost() + " firstPath=" + first + " pathSegments=" + count;
        } catch (Exception ignored) {
            return "invalid-url";
        }
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
