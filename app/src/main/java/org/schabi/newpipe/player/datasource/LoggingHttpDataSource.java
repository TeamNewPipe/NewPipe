package org.schabi.newpipe.player.datasource;

import static org.schabi.newpipe.MainActivity.DEBUG;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;

import org.schabi.newpipe.DownloaderImpl;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class LoggingHttpDataSource extends DefaultHttpDataSource {

    public final String TAG = getClass().getSimpleName() + "@" + hashCode();

    public LoggingHttpDataSource() { }

    public LoggingHttpDataSource(@Nullable final String userAgent,
                                 final int connectTimeoutMillis,
                                 final int readTimeoutMillis,
                                 final boolean allowCrossProtocolRedirects,
                                 @Nullable final RequestProperties defaultRequestProperties) {
        super(userAgent,
              connectTimeoutMillis,
              readTimeoutMillis,
              allowCrossProtocolRedirects,
              defaultRequestProperties);
    }


    @Override
    public long open(final DataSpec dataSpec) throws HttpDataSourceException {
        if (!DEBUG) {
            return super.open(dataSpec);
        }

        Log.d(TAG, "Request URL: " + dataSpec.uri);
        try {
            return super.open(dataSpec);
        } catch (final HttpDataSource.InvalidResponseCodeException e) {
            Log.e(TAG, "HTTP error for URL: " + dataSpec.uri);
            Log.e(TAG, "Response code: " + e.responseCode);
            Log.e(TAG, "Headers: " + e.headerFields);
            Log.e(TAG, "Body: " + new String(e.responseBody, StandardCharsets.UTF_8));
            throw e;
        }
    }

    @SuppressWarnings("checkstyle:hiddenField")
    public static class Factory implements HttpDataSource.Factory {

        final RequestProperties defaultRequestProperties;

        @Nullable
        TransferListener transferListener;
        @Nullable
        String userAgent;
        int connectTimeoutMs;
        int readTimeoutMs;
        boolean allowCrossProtocolRedirects;

        public Factory() {
            defaultRequestProperties = new RequestProperties();
            connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MILLIS;
            readTimeoutMs = DEFAULT_READ_TIMEOUT_MILLIS;
            userAgent = DownloaderImpl.USER_AGENT;
        }

        @NonNull
        @Override
        public HttpDataSource createDataSource() {
            final var dataSource = new LoggingHttpDataSource(userAgent,
                                                             connectTimeoutMs,
                                                             readTimeoutMs,
                                                             allowCrossProtocolRedirects,
                                                             defaultRequestProperties);
            if (transferListener != null) {
                dataSource.addTransferListener(transferListener);
            }
            return dataSource;
        }

        @NonNull
        @Override
        public Factory setDefaultRequestProperties(
                @NonNull final Map<String, String> defaultRequestProperties) {
            this.defaultRequestProperties.clearAndSet(defaultRequestProperties);
            return this;
        }

        public Factory setUserAgent(@Nullable final String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Factory setConnectTimeoutMs(final int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
            return this;
        }

        public Factory setReadTimeoutMs(final int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
            return this;
        }

        public Factory setAllowCrossProtocolRedirects(final boolean allowCrossProtocolRedirects) {
            this.allowCrossProtocolRedirects = allowCrossProtocolRedirects;
            return this;
        }

        public Factory setTransferListener(@Nullable final TransferListener transferListener) {
            this.transferListener = transferListener;
            return this;
        }
    }
}
