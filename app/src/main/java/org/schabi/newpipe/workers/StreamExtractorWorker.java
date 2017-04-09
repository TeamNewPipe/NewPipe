package org.schabi.newpipe.workers;

import android.content.Context;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.services.youtube.YoutubeStreamExtractor;
import org.schabi.newpipe.extractor.stream_info.StreamExtractor;
import org.schabi.newpipe.extractor.stream_info.StreamInfo;
import org.schabi.newpipe.report.ErrorActivity;

import java.io.IOException;

/**
 * Extract {@link StreamInfo} with {@link StreamExtractor} from the given url of the given service
 *
 * @author mauriciocolli
 */
@SuppressWarnings("WeakerAccess")
public class StreamExtractorWorker extends ExtractorWorker {
    //private static final String TAG = "StreamExtractorWorker";

    private StreamInfo streamInfo = null;
    private OnStreamInfoReceivedListener callback;

    /**
     * Interface which will be called for result and errors
     */
    public interface OnStreamInfoReceivedListener {
        void onReceive(StreamInfo info);
        void onError(int messageId);
        void onReCaptchaException();
        void onBlockedByGemaError();
        void onContentErrorWithMessage(int messageId);
        void onContentError();
    }

    /**
     * @param context   context for error reporting purposes
     * @param serviceId id of the request service
     * @param videoUrl  videoUrl of the service (e.g. https://www.youtube.com/watch?v=HyHNuVaZJ-k)
     * @param callback  listener that will be called-back when events occur (check {@link StreamExtractorWorker.OnStreamInfoReceivedListener})
     */
    public StreamExtractorWorker(Context context, int serviceId, String videoUrl, OnStreamInfoReceivedListener callback) {
        super(context, videoUrl, serviceId);
        this.callback = callback;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.callback = null;
        this.streamInfo = null;
    }

    @Override
    protected void doWork(int serviceId, String url) throws Exception {
        StreamExtractor streamExtractor = getService().getExtractorInstance(url);
        streamInfo = StreamInfo.getVideoInfo(streamExtractor);

        if (streamInfo != null && !streamInfo.errors.isEmpty()) handleErrorsDuringExtraction(streamInfo.errors, ErrorActivity.REQUESTED_STREAM);

        if (callback != null && streamInfo != null && !isInterrupted()) getHandler().post(new Runnable() {
            @Override
            public void run() {
                if (isInterrupted() || callback == null) return;

                callback.onReceive(streamInfo);
                onDestroy();
            }
        });

    }

    @Override
    protected void handleException(final Exception exception, int serviceId, String url) {
        if (exception instanceof ReCaptchaException) {
            if (callback != null) getHandler().post(new Runnable() {
                @Override
                public void run() {
                    callback.onReCaptchaException();
                }
            });
        } else if (exception instanceof IOException) {
            if (callback != null) getHandler().post(new Runnable() {
                @Override
                public void run() {
                    callback.onError(R.string.network_error);
                }
            });
        } else if (exception instanceof YoutubeStreamExtractor.GemaException) {
            if (callback != null) getHandler().post(new Runnable() {
                @Override
                public void run() {
                    callback.onBlockedByGemaError();
                }
            });
        } else if (exception instanceof YoutubeStreamExtractor.LiveStreamException) {
            if (callback != null) getHandler().post(new Runnable() {
                @Override
                public void run() {
                    callback.onContentErrorWithMessage(R.string.live_streams_not_supported);
                }
            });
        } else if (exception instanceof StreamExtractor.ContentNotAvailableException) {
            if (callback != null) getHandler().post(new Runnable() {
                @Override
                public void run() {
                    callback.onContentError();
                }
            });
        } else if (exception instanceof YoutubeStreamExtractor.DecryptException) {
            // custom service related exceptions
            ErrorActivity.reportError(getHandler(), getContext(), exception, MainActivity.class, null, ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_STREAM, getServiceName(), url, R.string.youtube_signature_decryption_error));
            finishIfActivity();
        } else if (exception instanceof StreamInfo.StreamExctractException) {
            if (!streamInfo.errors.isEmpty()) {
                // !!! if this case ever kicks in someone gets kicked out !!!
                ErrorActivity.reportError(getHandler(), getContext(), exception, MainActivity.class, null, ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_STREAM, getServiceName(), url, R.string.could_not_get_stream));
            } else {
                ErrorActivity.reportError(getHandler(), getContext(), streamInfo.errors, MainActivity.class, null, ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_STREAM, getServiceName(), url, R.string.could_not_get_stream));
            }
            finishIfActivity();
        } else if (exception instanceof ParsingException) {
            ErrorActivity.reportError(getHandler(), getContext(), exception, MainActivity.class, null, ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_STREAM, getServiceName(), url, R.string.parsing_error));
            finishIfActivity();
        } else {
            ErrorActivity.reportError(getHandler(), getContext(), exception, MainActivity.class, null, ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_STREAM, getServiceName(), url, R.string.general_error));
            finishIfActivity();
        }

    }

}
