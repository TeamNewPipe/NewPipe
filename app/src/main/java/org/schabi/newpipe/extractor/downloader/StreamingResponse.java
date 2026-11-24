/*
 * SPDX-FileCopyrightText: PipePipe contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Source: PipePipeExtractor e9818f8ef16d3720107c51f6ee157aecb1da2b70.
 * Public streaming response contract retained for binary plugin compatibility.
 */

package org.schabi.newpipe.extractor.downloader;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A streaming HTTP response: the body is exposed as an {@link InputStream} instead of a buffered
 * {@code byte[]}, so a large response (e.g. a SABR media batch, which can be 50-150MB at 4K) is not
 * held whole in memory. The caller MUST {@link #close()} it to release the underlying connection.
 */
public class StreamingResponse implements Closeable {
    private final int responseCode;
    @Nonnull
    private final Map<String, List<String>> responseHeaders;
    @Nonnull
    private final InputStream body;

    public StreamingResponse(final int responseCode,
                             @Nullable final Map<String, List<String>> responseHeaders,
                             @Nonnull final InputStream body) {
        this.responseCode = responseCode;
        this.responseHeaders = responseHeaders == null ? Collections.emptyMap() : responseHeaders;
        this.body = body;
    }

    public int responseCode() {
        return responseCode;
    }

    @Nonnull
    public InputStream body() {
        return body;
    }

    /** First value for a header name (case-insensitive), or {@code null}. */
    @Nullable
    public String getHeader(final String name) {
        for (final Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
            final String key = entry.getKey();
            if (key != null && key.equalsIgnoreCase(name) && !entry.getValue().isEmpty()) {
                return entry.getValue().get(0);
            }
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        body.close();
    }
}
