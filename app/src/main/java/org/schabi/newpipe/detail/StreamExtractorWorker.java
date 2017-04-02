package org.schabi.newpipe.detail;

import android.app.Activity;
import android.os.Handler;
import android.util.Log;
import android.view.View;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.services.youtube.YoutubeStreamExtractor;
import org.schabi.newpipe.extractor.stream_info.StreamExtractor;
import org.schabi.newpipe.extractor.stream_info.StreamInfo;
import org.schabi.newpipe.report.ErrorActivity;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Extract {@link StreamInfo} with {@link StreamExtractor} from the given url of the given service
 */
@SuppressWarnings("WeakerAccess")
public class StreamExtractorWorker extends Thread {
    private static final String TAG = "StreamExtractorWorker";

    private Activity activity;
    private final String videoUrl;
    private final int serviceId;
    private OnStreamInfoReceivedListener callback;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final Handler handler = new Handler();


    public interface OnStreamInfoReceivedListener {
        void onReceive(StreamInfo info);
        void onError(int messageId);
        void onReCaptchaException();
        void onBlockedByGemaError();
        void onContentErrorWithMessage(int messageId);
        void onContentError();
    }

    public StreamExtractorWorker(Activity activity, String videoUrl, int serviceId, OnStreamInfoReceivedListener callback) {
        this.serviceId = serviceId;
        this.videoUrl = videoUrl;
        this.activity = activity;
        this.callback = callback;
    }

    /**
     * Returns a new instance <b>already</b> started  of {@link StreamExtractorWorker}.<br>
     * The caller is responsible to check if {@link StreamExtractorWorker#isRunning()}, or {@link StreamExtractorWorker#cancel()} it
     *
     * @param serviceId id of the request service
     * @param url       videoUrl of the service (e.g. https://www.youtube.com/watch?v=HyHNuVaZJ-k)
     * @param activity  activity for error reporting purposes
     * @param callback  listener that will be called-back when events occur (check {@link OnStreamInfoReceivedListener})
     * @return new instance already started of {@link StreamExtractorWorker}
     */
    public static StreamExtractorWorker startExtractorThread(int serviceId, String url, Activity activity, OnStreamInfoReceivedListener callback) {
        StreamExtractorWorker extractorThread = getExtractorThread(serviceId, url, activity, callback);
        extractorThread.start();
        return extractorThread;
    }

    /**
     * Returns a new instance of {@link StreamExtractorWorker}.<br>
     * The caller is responsible to check if {@link StreamExtractorWorker#isRunning()}, or {@link StreamExtractorWorker#cancel()}
     * when it doesn't need it anymore
     * <p>
     * <b>Note:</b> this instance is <b>not</b> started yet
     *
     * @param serviceId id of the request service
     * @param url       videoUrl of the service (e.g. https://www.youtube.com/watch?v=HyHNuVaZJ-k)
     * @param activity  activity for error reporting purposes
     * @param callback  listener that will be called-back when events occur (check {@link OnStreamInfoReceivedListener})
     * @return instance of {@link StreamExtractorWorker}
     */
    public static StreamExtractorWorker getExtractorThread(int serviceId, String url, Activity activity, OnStreamInfoReceivedListener callback) {
        return new StreamExtractorWorker(activity, url, serviceId, callback);
    }

    @Override
    //Just ignore the errors for now
    @SuppressWarnings("ConstantConditions")
    public void run() {
        // TODO: Improve error checking
        // and this method in general

        StreamInfo streamInfo = null;
        StreamingService service;
        try {
            service = NewPipe.getService(serviceId);
        } catch (Exception e) {
            e.printStackTrace();
            ErrorActivity.reportError(handler, activity, e, VideoItemDetailActivity.class, null,
                    ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_STREAM,
                            "", videoUrl, R.string.could_not_get_stream));
            return;
        }
        try {
            isRunning.set(true);
            StreamExtractor streamExtractor = service.getExtractorInstance(videoUrl);
            streamInfo = StreamInfo.getVideoInfo(streamExtractor);

            final StreamInfo info = streamInfo;
            if (callback != null) handler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onReceive(info);
                }
            });
            isRunning.set(false);
            // look for errors during extraction
            // this if statement only covers extra information.
            // if these are not available or caused an error, they are just not available
            // but don't render the stream information unusalbe.
            if (streamInfo != null && !streamInfo.errors.isEmpty()) {
                Log.e(TAG, "OCCURRED ERRORS DURING EXTRACTION:");
                for (Throwable e : streamInfo.errors) {
                    e.printStackTrace();
                    Log.e(TAG, "------");
                }

                View rootView = activity != null ? activity.findViewById(R.id.video_item_detail) : null;
                ErrorActivity.reportError(handler, activity,
                        streamInfo.errors, null, rootView,
                        ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_STREAM,
                                service.getServiceInfo().name, videoUrl, 0 /* no message for the user */));
            }

            // These errors render the stream information unusable.
        } catch (ReCaptchaException e) {
            if (callback != null) handler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onReCaptchaException();
                }
            });
        } catch (IOException e) {
            if (callback != null) handler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onError(R.string.network_error);
                }
            });
            if (callback != null) e.printStackTrace();
        } catch (YoutubeStreamExtractor.DecryptException de) {
            // custom service related exceptions
            ErrorActivity.reportError(handler, activity, de, VideoItemDetailActivity.class, null,
                    ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_STREAM,
                            service.getServiceInfo().name, videoUrl, R.string.youtube_signature_decryption_error));
            handler.post(new Runnable() {
                @Override
                public void run() {
                    activity.finish();
                }
            });
            de.printStackTrace();
        } catch (YoutubeStreamExtractor.GemaException ge) {
            if (callback != null) handler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onBlockedByGemaError();
                }
            });
        } catch (YoutubeStreamExtractor.LiveStreamException e) {
            if (callback != null) handler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onContentErrorWithMessage(R.string.live_streams_not_supported);
                }
            });
        }
        // ----------------------------------------
        catch (StreamExtractor.ContentNotAvailableException e) {
            if (callback != null) handler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onContentError();
                }
            });
            e.printStackTrace();
        } catch (StreamInfo.StreamExctractException e) {
            if (!streamInfo.errors.isEmpty()) {
                // !!! if this case ever kicks in someone gets kicked out !!!
                ErrorActivity.reportError(handler, activity, e, VideoItemDetailActivity.class, null,
                        ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_STREAM,
                                service.getServiceInfo().name, videoUrl, R.string.could_not_get_stream));
            } else {
                ErrorActivity.reportError(handler, activity, streamInfo.errors, VideoItemDetailActivity.class, null,
                        ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_STREAM,
                                service.getServiceInfo().name, videoUrl, R.string.could_not_get_stream));
            }
            handler.post(new Runnable() {
                @Override
                public void run() {
                    activity.finish();
                }
            });
            e.printStackTrace();
        } catch (ParsingException e) {
            ErrorActivity.reportError(handler, activity, e, VideoItemDetailActivity.class, null,
                    ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_STREAM,
                            service.getServiceInfo().name, videoUrl, R.string.parsing_error));
            handler.post(new Runnable() {
                @Override
                public void run() {
                    activity.finish();
                }
            });
            e.printStackTrace();
        } catch (Exception e) {
            ErrorActivity.reportError(handler, activity, e, VideoItemDetailActivity.class, null,
                    ErrorActivity.ErrorInfo.make(ErrorActivity.REQUESTED_STREAM,
                            service.getServiceInfo().name, videoUrl, R.string.general_error));
            handler.post(new Runnable() {
                @Override
                public void run() {
                    activity.finish();
                }
            });
            e.printStackTrace();
        }
    }

    /**
     * Return true if the extraction is not completed yet
     *
     * @return the value of the AtomicBoolean {@link #isRunning}
     */
    public boolean isRunning() {
        return isRunning.get();
    }

    /**
     * Cancel this ExtractorThread, setting the callback to null, the AtomicBoolean {@link #isRunning} to false and interrupt this thread.
     * <p>
     * <b>Note:</b> Any I/O that is active in the moment that this method is called will be canceled and a Exception will be thrown, because of the {@link #interrupt()}.<br>
     * This is useful when you don't want the resulting {@link StreamInfo} anymore, but don't want to waste bandwidth, otherwise it'd run till it receives the StreamInfo.
     */
    public void cancel() {
        this.callback = null;
        this.isRunning.set(false);
        this.interrupt();
    }
}
