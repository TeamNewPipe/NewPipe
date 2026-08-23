package org.schabi.newpipe.player.helper;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.metadata.icy.IcyHeaders;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.common.base.Predicate;

import java.util.HashMap;
import java.util.Map;

public class PurifiedHttpDataSource extends DefaultHttpDataSource {
    public static class Factory implements HttpDataSource.Factory {

        private final RequestProperties defaultRequestProperties;

        @Nullable
        private TransferListener transferListener;
        @Nullable private Predicate<String> contentTypePredicate;
        @Nullable private String userAgent;
        private int connectTimeoutMs;
        private int readTimeoutMs;
        private boolean allowCrossProtocolRedirects;
        private boolean keepPostFor302Redirects;

        /** Creates an instance. */
        public Factory() {
            defaultRequestProperties = new RequestProperties();
            connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MILLIS;
            readTimeoutMs = DEFAULT_READ_TIMEOUT_MILLIS;
        }

        @Override
        public final PurifiedHttpDataSource.Factory setDefaultRequestProperties(Map<String, String> defaultRequestProperties) {
            this.defaultRequestProperties.clearAndSet(defaultRequestProperties);
            return this;
        }

        /**
         * Sets the user agent that will be used.
         *
         * <p>The default is {@code null}, which causes the default user agent of the underlying
         * platform to be used.
         *
         * @param userAgent The user agent that will be used, or {@code null} to use the default user
         *     agent of the underlying platform.
         * @return This factory.
         */
        public PurifiedHttpDataSource.Factory setUserAgent(@Nullable String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        /**
         * Sets the connect timeout, in milliseconds.
         *
         * <p>The default is {@link PurifiedHttpDataSource#DEFAULT_CONNECT_TIMEOUT_MILLIS}.
         *
         * @param connectTimeoutMs The connect timeout, in milliseconds, that will be used.
         * @return This factory.
         */
        public PurifiedHttpDataSource.Factory setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
            return this;
        }

        /**
         * Sets the read timeout, in milliseconds.
         *
         * <p>The default is {@link PurifiedHttpDataSource#DEFAULT_READ_TIMEOUT_MILLIS}.
         *
         * @param readTimeoutMs The connect timeout, in milliseconds, that will be used.
         * @return This factory.
         */
        public PurifiedHttpDataSource.Factory setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
            return this;
        }

        /**
         * Sets whether to allow cross protocol redirects.
         *
         * <p>The default is {@code false}.
         *
         * @param allowCrossProtocolRedirects Whether to allow cross protocol redirects.
         * @return This factory.
         */
        public PurifiedHttpDataSource.Factory setAllowCrossProtocolRedirects(boolean allowCrossProtocolRedirects) {
            this.allowCrossProtocolRedirects = allowCrossProtocolRedirects;
            return this;
        }

        /**
         * Sets a content type {@link Predicate}. If a content type is rejected by the predicate then a
         * {@link HttpDataSource.InvalidContentTypeException} is thrown from {@link
         * PurifiedHttpDataSource#open(DataSpec)}.
         *
         * <p>The default is {@code null}.
         *
         * @param contentTypePredicate The content type {@link Predicate}, or {@code null} to clear a
         *     predicate that was previously set.
         * @return This factory.
         */
        public PurifiedHttpDataSource.Factory setContentTypePredicate(@Nullable Predicate<String> contentTypePredicate) {
            this.contentTypePredicate = contentTypePredicate;
            return this;
        }

        /**
         * Sets the {@link TransferListener} that will be used.
         *
         * <p>The default is {@code null}.
         *
         * <p>See {@link DataSource#addTransferListener(TransferListener)}.
         *
         * @param transferListener The listener that will be used.
         * @return This factory.
         */
        public PurifiedHttpDataSource.Factory setTransferListener(@Nullable TransferListener transferListener) {
            this.transferListener = transferListener;
            return this;
        }

        /**
         * Sets whether we should keep the POST method and body when we have HTTP 302 redirects for a
         * POST request.
         */
        public PurifiedHttpDataSource.Factory setKeepPostFor302Redirects(boolean keepPostFor302Redirects) {
            this.keepPostFor302Redirects = keepPostFor302Redirects;
            return this;
        }

        @Override
        public PurifiedHttpDataSource createDataSource() {
            PurifiedHttpDataSource dataSource =
                    new PurifiedHttpDataSource(
                            userAgent,
                            connectTimeoutMs,
                            readTimeoutMs,
                            allowCrossProtocolRedirects,
                            defaultRequestProperties,
                            contentTypePredicate,
                            keepPostFor302Redirects);
            if (transferListener != null) {
                dataSource.addTransferListener(transferListener);
            }
            return dataSource;
        }
    }
    PurifiedHttpDataSource(
            @Nullable String userAgent,
            int connectTimeoutMillis,
            int readTimeoutMillis,
            boolean allowCrossProtocolRedirects,
            @Nullable RequestProperties defaultRequestProperties,
            @Nullable Predicate<String> contentTypePredicate,
            boolean keepPostFor302Redirects) {
        super(
                userAgent,
                connectTimeoutMillis,
                readTimeoutMillis,
                allowCrossProtocolRedirects,
                defaultRequestProperties
        );
    }

    @Override
    public long open(DataSpec dataSpec) throws HttpDataSourceException
    {
        final Map<String, String> m1 = dataSpec.httpRequestHeaders;
        final Map<String, String> m2 = new HashMap<>();
        for (Map.Entry<String, String> entry : m1.entrySet())
            if(!entry.getKey().equals(IcyHeaders.REQUEST_HEADER_ENABLE_METADATA_NAME))
                m2.put(entry.getKey(), entry.getValue());

        return super.open(dataSpec.withRequestHeaders(m2));
    }
}
