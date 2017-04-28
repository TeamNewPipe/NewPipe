package org.schabi.newpipe.workers;

import android.content.Context;
import android.os.Handler;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream_info.StreamInfo;

import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Common properties of Workers
 *
 * @author mauriciocolli
 */
@SuppressWarnings("WeakerAccess")
public abstract class AbstractWorker extends Thread {

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private final int serviceId;
    private Context context;
    private Handler handler;
    private StreamingService service;

    public AbstractWorker(Context context, int serviceId) {
        this.context = context;
        this.serviceId = serviceId;
        this.handler = new Handler(context.getMainLooper());
    }

    @Override
    public void run() {
        try {
            isRunning.set(true);
            service = NewPipe.getService(serviceId);
            doWork(serviceId);
        } catch (Exception e) {
            // Handle the exception only if thread is not interrupted
            e.printStackTrace();
            if (!isInterrupted() && !(e instanceof InterruptedIOException) && !(e.getCause() instanceof InterruptedIOException)) {
                handleException(e, serviceId);
            }
        } finally {
            isRunning.set(false);
        }
    }

    /**
     * Here is the place that the heavy work is realized
     *
     * @param serviceId     serviceId that was passed when created this object
     *
     * @throws Exception    these exceptions are handled by the {@link #handleException(Exception, int)}
     */
    protected abstract void doWork(int serviceId) throws Exception;


    /**
     * Method that handle the exception thrown by the {@link #doWork(int)}.
     *
     * @param exception {@link Exception} that was thrown by {@link #doWork(int)}
     */
    protected abstract void handleException(Exception exception, int serviceId);

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

    public Handler getHandler() {
        return handler;
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
