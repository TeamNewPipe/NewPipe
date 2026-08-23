package org.schabi.newpipe.player.datasource;

import java.io.IOException;

/** Signals that another serialized SABR request may shortly deliver the requested segment. */
final class SabrSegmentPendingException extends IOException {
    SabrSegmentPendingException(final String message) {
        super(message);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
