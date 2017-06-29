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
import org.schabi.newpipe.report.UserAction;

import java.io.IOException;

import static org.schabi.newpipe.report.UserAction.*;

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

        /**
         * Called when an unrecoverable error has occurred.
         * <p> This is a good place to finish the caller. </p>
         */
        void onUnrecoverableError(Exception exception);
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

        if (streamInfo != null && !streamInfo.errors.isEmpty()) handleErrorsDuringExtraction(streamInfo.errors, REQUESTED_STREAM);

        if (callback != null && getHandler() != null && streamInfo != null && !isInterrupted()) getHandler().post(new Runnable() {
            @Override
            public void run() {
                if (isInterrupted() || callback == null) return;

                callback.onReceive(streamInfo);
                onDestroy();
            }
        });

    }

    @Override
    protected void handleException(final Exception exception, int serviceId, final String url) {
        if (callback == null || getHandler() == null || isInterrupted()) return;

        if (exception instanceof ReCaptchaException) {
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    callback.onReCaptchaException();
                }
            });
        } else if (exception instanceof IOException) {
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    callback.onError(R.string.network_error);
                }
            });
        } else if (exception instanceof YoutubeStreamExtractor.GemaException) {
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    callback.onBlockedByGemaError();
                }
            });
        } else if (exception instanceof YoutubeStreamExtractor.LiveStreamException) {
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    callback.onContentErrorWithMessage(R.string.live_streams_not_supported);
                }
            });
        } else if (exception instanceof StreamExtractor.ContentNotAvailableException) {
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    callback.onContentError();
                }
            });
        } else if (exception instanceof YoutubeStreamExtractor.DecryptException) {
            // custom service related exceptions
            ErrorActivity.reportError(getHandler(), getContext(), exception, MainActivity.class, null, ErrorActivity.ErrorInfo.make(REQUESTED_STREAM, getServiceName(), url, R.string.youtube_signature_decryption_error));
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    callback.onUnrecoverableError(exception);
                }
            });
        } else if (exception instanceof StreamInfo.StreamExctractException) {
            if (!streamInfo.errors.isEmpty()) {
                // !!! if this case ever kicks in someone gets kicked out !!!
                ErrorActivity.reportError(getHandler(), getContext(), exception, MainActivity.class, null, ErrorActivity.ErrorInfo.make(REQUESTED_STREAM, getServiceName(), url, R.string.could_not_get_stream));
            } else {
                ErrorActivity.reportError(getHandler(), getContext(), streamInfo.errors, MainActivity.class, null, ErrorActivity.ErrorInfo.make(REQUESTED_STREAM, getServiceName(), url, R.string.could_not_get_stream));
            }

            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    callback.onUnrecoverableError(exception);
                }
            });
        } else if (exception instanceof ParsingException) {
            ErrorActivity.reportError(getHandler(), getContext(), exception, MainActivity.class, null, ErrorActivity.ErrorInfo.make(REQUESTED_STREAM, getServiceName(), url, R.string.parsing_error));
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    callback.onUnrecoverableError(exception);
                }
            });
        } else {
            ErrorActivity.reportError(getHandler(), getContext(), exception, MainActivity.class, null, ErrorActivity.ErrorInfo.make(REQUESTED_STREAM, getServiceName(), url, R.string.general_error));
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    callback.onUnrecoverableError(exception);
                }
            });
        }

    }

}
