package org.schabi.newpipe.player.datasource;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;

import java.io.IOException;

/** Retries locally pending SABR segments without applying network-error backoff. */
final class SabrLoadErrorHandlingPolicy extends DefaultLoadErrorHandlingPolicy {
    private static final long PENDING_RETRY_DELAY_MS = 100;

    @Override
    public long getRetryDelayMsFor(final LoadErrorHandlingPolicy.LoadErrorInfo loadErrorInfo) {
        if (isPending(loadErrorInfo.exception)) {
            return PENDING_RETRY_DELAY_MS;
        }
        final int normalRetryCount = super.getMinimumLoadableRetryCount(
                loadErrorInfo.mediaLoadData.dataType);
        return loadErrorInfo.errorCount > normalRetryCount
                ? C.TIME_UNSET : super.getRetryDelayMsFor(loadErrorInfo);
    }

    @Override
    public int getMinimumLoadableRetryCount(final int dataType) {
        // Pending-segment retries can exceed the normal network retry budget while a response is
        // streaming. Real errors are still made fatal by getRetryDelayMsFor after that budget.
        return Integer.MAX_VALUE;
    }

    private static boolean isPending(final IOException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SabrSegmentPendingException) return true;
            current = current.getCause();
        }
        return false;
    }
}
