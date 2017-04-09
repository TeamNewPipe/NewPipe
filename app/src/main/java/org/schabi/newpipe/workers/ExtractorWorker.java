package org.schabi.newpipe.workers;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.View;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream_info.StreamInfo;
import org.schabi.newpipe.report.ErrorActivity;

import java.io.InterruptedIOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Common properties of ExtractorWorkers
 *
 * @author mauriciocolli
 */
@SuppressWarnings("WeakerAccess")
public abstract class ExtractorWorker extends Thread {

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private final String url;
    private final int serviceId;
    private Context context;
    private Handler handler;
    private StreamingService service;

    public ExtractorWorker(Context context, String url, int serviceId) {
        this.context = context;
        this.url = url;
        this.serviceId = serviceId;
        this.handler = new Handler(context.getMainLooper());
        if (url.length() >= 40) setName("Thread-" + url.substring(url.length() - 11, url.length()));
    }

    @Override
    public void run() {
        try {
            isRunning.set(true);
            service = NewPipe.getService(serviceId);
            doWork(serviceId, url);
        } catch (Exception e) {
            // Handle the exception only if thread is not interrupted
            if (!isInterrupted() && !(e instanceof InterruptedIOException) && !(e.getCause() instanceof InterruptedIOException)) {
                handleException(e, serviceId, url);
            }
        } finally {
            isRunning.set(false);
        }
    }

    /**
     * Here is the place that the heavy work is realized
     *
     * @param serviceId     serviceId that was passed when created this object
     * @param url           url that was passed when created this object
     *
     * @throws Exception    these exceptions are handled by the {@link #handleException(Exception, int, String)}
     */
    protected abstract void doWork(int serviceId, String url) throws Exception;


    /**
     * Method that handle the exception thrown by the {@link #doWork(int, String)}.
     *
     * @param exception {@link Exception} that was thrown by {@link #doWork(int, String)}
     */
    protected abstract void handleException(Exception exception, int serviceId, String url);

    /**
     * Handle the errors <b>during</b> extraction and shows a Report button to the user.<br/>
     * Subclasses <b>maybe</b> call this method.
     *
     * @param errorsList        list of exceptions that happened during extraction
     * @param errorUserAction   what action was the user performing during the error.
     *                          (One of the {@link ErrorActivity}.REQUEST_* error (message) ids)
     */
    protected void handleErrorsDuringExtraction(List<Throwable> errorsList, int errorUserAction){
        String errorString = "<error id>";
        switch (errorUserAction) {
            case ErrorActivity.REQUESTED_STREAM:
                errorString=  ErrorActivity.REQUESTED_STREAM_STRING;
                break;
            case ErrorActivity.REQUESTED_CHANNEL:
                errorString=  ErrorActivity.REQUESTED_CHANNEL_STRING;
                break;
        }

        Log.e(errorString, "OCCURRED ERRORS DURING EXTRACTION:");
        for (Throwable e : errorsList) {
            e.printStackTrace();
            Log.e(errorString, "------");
        }

        if (getContext() instanceof Activity) {
            View rootView = getContext() != null ? ((Activity) getContext()).findViewById(android.R.id.content) : null;
            ErrorActivity.reportError(getHandler(), getContext(), errorsList, null, rootView, ErrorActivity.ErrorInfo.make(errorUserAction, getServiceName(), url, 0 /* no message for the user */));
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
     * Cancel this ExtractorWorker, calling {@link #onDestroy()} and interrupting this thread.
     * <p>
     * <b>Note:</b> Any I/O that is active in the moment that this method is called will be canceled and a Exception will be thrown, because of the {@link #interrupt()}.<br>
     * This is useful when you don't want the resulting {@link StreamInfo} anymore, but don't want to waste bandwidth, otherwise it'd run till it receives the StreamInfo.
     */
    public void cancel() {
        onDestroy();
        this.interrupt();
    }

    /**
     * Method that discards everything that doesn't need anymore.<br>
     * Subclasses can override this method to destroy their garbage.
     */
    protected void onDestroy() {
        this.isRunning.set(false);
        this.context = null;
        this.handler = null;
        this.service = null;
    }

    /**
     * If the context passed in the constructor is an {@link Activity}, finish it.
     */
    protected void finishIfActivity() {
        if (getContext() instanceof Activity) ((Activity) getContext()).finish();
    }

    public Handler getHandler() {
        return handler;
    }

    public String getUrl() {
        return url;
    }

    public StreamingService getService() {
        return service;
    }

    public int getServiceId() {
        return serviceId;
    }

    public String getServiceName() {
        return service == null ? "none" : service.getServiceInfo().name;
    }

    public Context getContext() {
        return context;
    }
}
