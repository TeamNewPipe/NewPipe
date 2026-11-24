package org.schabi.newpipe.extractor.downloader;

import java.util.concurrent.TimeUnit;

import okhttp3.Call;

public class CancellableCall {
    private final Call call;
    private final long startedAtNanos = System.nanoTime();
    private long finishedAtNanos;
    private volatile boolean isFinished = false;

    public CancellableCall(Call call) {
        this.call = call;
    }

    public void cancel() {
        call.cancel();
    }

    public boolean isCancelled() {
        return call.isCanceled();
    }

    public boolean isFinished() {
        return isFinished;
    }

    public synchronized void setFinished() {
        if (!isFinished) {
            finishedAtNanos = System.nanoTime();
            isFinished = true;
            notifyAll();
        }
    }

    /** Elapsed duration exposed by the PipePipe plugin downloader contract. */
    public long getElapsedTimeMillis() {
        final long endNanos = isFinished ? finishedAtNanos : System.nanoTime();
        return TimeUnit.NANOSECONDS.toMillis(endNanos - startedAtNanos);
    }

    /** PipePipe-compatible alias; WizeStream callers may continue using {@link #awaitFinished(long, TimeUnit)}. */
    public boolean await(final long timeout, final TimeUnit unit) throws InterruptedException {
        // Match CountDownLatch.await's interruption contract even if already completed.
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        return awaitFinished(timeout, unit);
    }

    /** Waits for response processing to finish without occupying a CPU core. */
    public synchronized boolean awaitFinished(final long timeout, final TimeUnit unit)
            throws InterruptedException {
        final long budgetNanos = unit.toNanos(timeout);
        final long startNanos = System.nanoTime();
        long remainingNanos = budgetNanos;
        while (!isFinished && remainingNanos > 0) {
            TimeUnit.NANOSECONDS.timedWait(this, remainingNanos);
            remainingNanos = budgetNanos - (System.nanoTime() - startNanos);
        }
        return isFinished;
    }

    Call getCall() {
        return call;
    }
}
